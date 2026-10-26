package net.conczin.mca.expansion.mirror;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The live reflection in mirrors (client), without drawing the world a second time: only who stands in front of the
 * glass is drawn, into a small texture of each mirror, from the eye mirrored behind the glass. Its view is cut to the
 * glass exactly (an off-axis frustum whose near plane is the glass, so nothing behind the mirror shows), and behind the
 * people is a plain colour taken from the light there.
 * <p>
 * Mirrors are found by their renderer as the level is drawn (so only those in view), and their reflections drawn
 * right after the level, to be shown the next frame. At most {@link #SLOTS} at a time, the nearest; none while a
 * shader pack is in use, as it takes over drawing entities.
 */
public final class MirrorReflections {
    private static final int SLOTS = 2;
    /** Texture pixels per block of glass. */
    private static final int PIXELS_PER_BLOCK = 128;
    /** How near the eye must be to a mirror for its reflection to be drawn. */
    private static final double DISTANCE = 8.0;
    /** How far from the glass people are still drawn in it. */
    private static final double REACH = 6.0;
    private static final int MAX_ENTITIES = 5;
    /** The reflection is drawn into at most this often, in ms. */
    private static final long INTERVAL = 33L;
    /** How far behind the glass the reflection reaches. */
    private static final float DEPTH = 48.0F;
    /** The colour behind the people, at full light. */
    private static final float[] BACKDROP = {0.62F, 0.66F, 0.70F};

    private static final Map<BlockPos, BlockState> SEEN = new HashMap<>();
    private static final Slot[] SLOT = new Slot[SLOTS];
    private static final SubmitNodeStorage SUBMITS = new SubmitNodeStorage();
    private static @Nullable Method irisInstance;
    private static @Nullable Method irisShaderPackInUse;
    private static boolean irisLooked;
    /** Set when drawing a reflection failed: they are left off for the rest of the session, the game goes on. */
    private static boolean failed;

    private MirrorReflections() {
    }

    /** One reflection: the texture a mirror shows, and which mirror it is for. */
    private static final class Slot {
        final Identifier id;
        final ProjectionMatrixBuffer projection;
        @Nullable Target target;
        @Nullable BlockPos pos;
        boolean ready;
        /** Whether the texture runs right to left across the glass (the reflection is the camera's view turned round). */
        boolean flipped;
        long drawn;

        Slot(int index) {
            id = MCA.locate("dynamic/mirror/" + index);
            projection = new ProjectionMatrixBuffer("MCA mirror " + index);
        }
    }

    /** A colour and depth texture to draw a reflection into. */
    private static final class Target extends AbstractTexture {
        final GpuTexture depth;
        final GpuTextureView depthView;

        Target(int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "MCA mirror", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            depth = device.createTexture(() -> "MCA mirror depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.D32_FLOAT, width, height, 1, 1);
            depthView = device.createTextureView(depth);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        }

        boolean fits(int width, int height) {
            return texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height;
        }

        @Override
        public void close() {
            super.close();
            depthView.close();
            depth.close();
        }
    }

    public static boolean enabled() {
        return Config.getInstance().villagerMirrors && Config.getInstance().mirrorReflections;
    }

    /** A mirror is being drawn this frame (its renderer tells). */
    public static void seen(BlockPos pos, BlockState state) {
        if (enabled()) {
            SEEN.put(pos.immutable(), state);
        }
    }

    /** The reflection a mirror shows, if one has been drawn for it. */
    public static @Nullable Identifier textureFor(BlockPos pos) {
        for (Slot slot : SLOT) {
            if (slot != null && slot.ready && pos.equals(slot.pos)) {
                return slot.id;
            }
        }
        return null;
    }

    /** Whether that mirror's texture runs right to left. */
    public static boolean flipped(BlockPos pos) {
        for (Slot slot : SLOT) {
            if (slot != null && pos.equals(slot.pos)) {
                return slot.flipped;
            }
        }
        return false;
    }

    /** After the level is drawn: draws the reflections the mirrors in view will show. */
    public static void render(Minecraft client, FeatureRenderDispatcher features, float partialTick) {
        List<Map.Entry<BlockPos, BlockState>> seen = new ArrayList<>(SEEN.entrySet());
        SEEN.clear();
        if (!enabled() || failed || client.level == null || client.player == null || shaderPackInUse()) {
            for (Slot slot : SLOT) {
                if (slot != null) {
                    slot.pos = null;
                    slot.ready = false;
                }
            }
            return;
        }
        Vec3 eye = client.gameRenderer.mainCamera().position();

        // the nearest mirrors the eye is in front of
        List<Map.Entry<BlockPos, BlockState>> wanted = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : seen) {
            if (entry.getValue().getBlock() instanceof MirrorBlock mirror) {
                Vec3 center = mirror.glassCenter(entry.getKey(), entry.getValue());
                if (eye.distanceToSqr(center) < DISTANCE * DISTANCE && eye.subtract(center).dot(MirrorBlock.normal(entry.getValue())) > 0.02) {
                    wanted.add(entry);
                }
            }
        }
        wanted.sort(Comparator.comparingDouble(entry -> eye.distanceToSqr(Vec3.atCenterOf(entry.getKey()))));
        if (wanted.size() > SLOTS) {
            wanted = wanted.subList(0, SLOTS);
        }

        // a mirror keeps its slot while it is wanted; the others are freed for the newly wanted
        for (Slot slot : SLOT) {
            if (slot != null && slot.pos != null && wanted.stream().noneMatch(entry -> entry.getKey().equals(slot.pos))) {
                slot.pos = null;
                slot.ready = false;
            }
        }
        long now = Util.getMillis();
        for (Map.Entry<BlockPos, BlockState> entry : wanted) {
            Slot slot = slotFor(entry.getKey());
            if (slot != null && (!slot.ready || now - slot.drawn >= INTERVAL)) {
                try {
                    draw(client, features, slot, entry.getKey(), entry.getValue(), eye, partialTick);
                } catch (RuntimeException e) {
                    MCA.LOGGER.error("Mirror reflections failed and are off until restart", e);
                    failed = true;
                    slot.ready = false;
                    return;
                }
                slot.drawn = now;
            }
        }
    }

    private static @Nullable Slot slotFor(BlockPos pos) {
        Slot free = null;
        for (int i = 0; i < SLOTS; i++) {
            if (SLOT[i] == null) {
                SLOT[i] = new Slot(i);
            }
            if (pos.equals(SLOT[i].pos)) {
                return SLOT[i];
            }
            if (free == null && SLOT[i].pos == null) {
                free = SLOT[i];
            }
        }
        if (free != null) {
            free.pos = pos;
            free.ready = false;
        }
        return free;
    }

    private static void draw(Minecraft client, FeatureRenderDispatcher features, Slot slot, BlockPos pos, BlockState state, Vec3 eye, float partialTick) {
        MirrorBlock mirror = (MirrorBlock) state.getBlock();
        float[] glass = mirror.glass(state);
        Direction facing = state.getValue(MirrorBlock.FACING);
        Vec3 normal = MirrorBlock.normal(state);
        // the glass's corners: bottom left and right, and top left, as someone looking into it sees them
        Vec3 bottomLeft = MirrorBlock.toWorld(pos, facing, glass[0], glass[1], glass[4]);
        Vec3 bottomRight = MirrorBlock.toWorld(pos, facing, glass[2], glass[1], glass[4]);
        Vec3 topLeft = MirrorBlock.toWorld(pos, facing, glass[0], glass[3], glass[4]);

        int width = Math.max(8, Math.round((glass[2] - glass[0]) / 16.0F * PIXELS_PER_BLOCK));
        int height = Math.max(8, Math.round((glass[3] - glass[1]) / 16.0F * PIXELS_PER_BLOCK));
        if (slot.target == null || !slot.target.fits(width, height)) {
            client.getTextureManager().release(slot.id);
            slot.target = new Target(width, height);
            client.getTextureManager().register(slot.id, slot.target);
        }

        // the eye mirrored behind the glass, looking out of it
        double distance = eye.subtract(bottomLeft).dot(normal);
        Vec3 virtualEye = eye.subtract(normal.scale(2.0 * distance));
        Matrix4f view = new Matrix4f().setLookAlong((float) normal.x, 0.0F, (float) normal.z, 0.0F, 1.0F, 0.0F);
        Vector3f a = view.transformPosition(bottomLeft.subtract(virtualEye).toVector3f());
        Vector3f b = view.transformPosition(bottomRight.subtract(virtualEye).toVector3f());
        Vector3f c = view.transformPosition(topLeft.subtract(virtualEye).toVector3f());
        float near = -a.z;
        float far = near + DEPTH;
        float left = Math.min(a.x, b.x);
        float right = Math.max(a.x, b.x);
        float bottom = Math.min(a.y, c.y);
        float top = Math.max(a.y, c.y);
        slot.flipped = a.x > b.x;
        // reversed depth, as the game draws: the extents are given at the "near" argument, here the far plane
        float scale = far / near;
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Matrix4f projection = new Matrix4f().setFrustum(left * scale, right * scale, bottom * scale, top * scale, far, near, zeroToOne);

        Target target = slot.target;
        float light = Math.max(0.15F, client.level.getMaxLocalRawBrightness(pos) / 15.0F);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getTexture(),
                new Vector4f(BACKDROP[0] * light, BACKDROP[1] * light, BACKDROP[2] * light, 1.0F), target.depth, 0.0);

        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        RenderSystem.outputColorTextureOverride = target.getTextureView();
        RenderSystem.outputDepthTextureOverride = target.depthView;
        RenderSystem.setProjectionMatrix(slot.projection.getBuffer(projection), ProjectionType.PERSPECTIVE);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(view);
        client.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        try {
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
            CameraRenderState camera = new CameraRenderState();
            camera.pos = virtualEye;
            camera.orientation = new Quaternionf().rotationTo(0.0F, 0.0F, -1.0F, (float) normal.x, 0.0F, (float) normal.z);
            PoseStack pose = new PoseStack();
            for (Entity entity : inFront(client, mirror.glassCenter(pos, state), normal)) {
                EntityRenderState renderState = dispatcher.extractEntity(entity, partialTick);
                renderState.nameTag = null;
                renderState.shadowPieces.clear();
                renderState.outlineColor = 0;
                dispatcher.submit(renderState, camera, renderState.x - virtualEye.x, renderState.y - virtualEye.y, renderState.z - virtualEye.z, pose, SUBMITS);
            }
            features.renderAllFeatures(SUBMITS);
            slot.ready = true;
        } finally {
            modelView.popMatrix();
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            if (savedProjection != null) {
                RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            }
        }
    }

    /** Who stands in front of the glass to be seen in it: the player first, then the nearest others. */
    private static List<Entity> inFront(Minecraft client, Vec3 center, Vec3 normal) {
        List<Entity> found = new ArrayList<>();
        found.add(client.player);
        List<LivingEntity> others = client.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(REACH),
                entity -> entity != client.player && !entity.isInvisible() && entity.position().subtract(center).dot(normal) > 0.0);
        others.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        for (LivingEntity other : others) {
            if (found.size() >= MAX_ENTITIES) {
                break;
            }
            found.add(other);
        }
        return found;
    }

    /** Whether Iris draws with a shader pack (looked up by reflection, so Iris need not be there). */
    private static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisInstance = null;
            }
        }
        if (irisInstance == null || irisShaderPackInUse == null) {
            return false;
        }
        try {
            return (boolean) irisShaderPackInUse.invoke(irisInstance.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
