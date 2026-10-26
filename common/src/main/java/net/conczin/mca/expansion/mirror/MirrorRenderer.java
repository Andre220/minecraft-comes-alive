package net.conczin.mca.expansion.mirror;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a mirror's reflection (see MirrorReflections) over its glass, when there is one; otherwise the glass of the
 * block model shows. The reflection is drawn at full light: it was lit as it was drawn.
 */
public class MirrorRenderer implements BlockEntityRenderer<MirrorBlockEntity, MirrorRenderer.State> {
    private static final float PX = 1.0F / 16.0F;
    /** How far in front of the glass the reflection lies, in px. */
    private static final float LIFT = 0.02F;
    private static final int FULL_LIGHT = 0xF000F0;

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier reflection;
        boolean flipped;
        float[] glass = new float[5];
        Direction facing = Direction.SOUTH;
    }

    public MirrorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MirrorBlockEntity mirror, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(mirror, state, partialTick, cameraPos, crumbling);
        state.reflection = null;
        if (!(mirror.getBlockState().getBlock() instanceof MirrorBlock block)) {
            return;
        }
        MirrorReflections.seen(mirror.getBlockPos(), mirror.getBlockState());
        state.reflection = MirrorReflections.textureFor(mirror.getBlockPos());
        state.flipped = MirrorReflections.flipped(mirror.getBlockPos());
        state.glass = block.glass(mirror.getBlockState());
        state.facing = mirror.getBlockState().getValue(MirrorBlock.FACING);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Identifier reflection = state.reflection;
        if (reflection == null) {
            return;
        }
        float[] g = state.glass;
        float x0 = g[0] * PX;
        float y0 = g[1] * PX;
        float x1 = g[2] * PX;
        float y1 = g[3] * PX;
        float z = (g[4] + LIFT) * PX;
        // the texture's left edge is on the glass's right when it runs right to left
        float uLeft = state.flipped ? 1.0F : 0.0F;
        float uRight = 1.0F - uLeft;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(state.facing)));
        pose.translate(-0.5F, 0.0F, -0.5F);
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(reflection), (p, buffer) -> {
            // the top of the drawn image is at v = 1
            float[][] corners = {{x0, y0, uLeft, 0.0F}, {x1, y0, uRight, 0.0F}, {x1, y1, uRight, 1.0F}, {x0, y1, uLeft, 1.0F}};
            for (float[] corner : corners) {
                buffer.addVertex(p, corner[0], corner[1], z)
                        .setColor(0xFFFFFFFF)
                        .setUv(corner[2], corner[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_LIGHT)
                        .setNormal(p, 0.0F, 0.0F, 1.0F);
            }
        });
        pose.popPose();
    }

    /** The blockstate's y rotation for a facing, as in the blockstate files. */
    private static float yRot(Direction facing) {
        return switch (facing) {
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }
}
