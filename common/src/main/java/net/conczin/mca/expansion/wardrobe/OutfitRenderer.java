package net.conczin.mca.expansion.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.conczin.mca.MCA;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.client.resources.SkinExporter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Draws an outfit folded, lying down, out of the 64x64 clothing texture (the player skin layout): the shirt folded
 * into a flat box with the chest on top, the back underneath and a strip of the torso's sides round its edges; the
 * trousers folded under it, peeking out in front; the sleeves folded over the chest, slanting in from the shoulders.
 * Each with its outer layer over it.
 * An outfit without clothes shows the bare body texture.
 * <p>
 * Measures are in pixels of a block, the neck towards -z, the wearer's right towards -x.
 */
public class OutfitRenderer implements SpecialModelRenderer<OutfitRenderer.Look> {
    public static final Identifier ID = MCA.locate("outfit");
    private static final Identifier BARE = MCA.locate("skins/skin/male/2.png");
    private static final float PX = 1.0F / 16.0F;
    /** How far the outer layer stands off what it covers. */
    private static final float OUTER = 0.15F;

    // the folded shirt
    private static final float SHIRT_X0 = 4.0F;
    private static final float SHIRT_X1 = 12.0F;
    private static final float SHIRT_Y0 = 0.5F;
    private static final float SHIRT_Y1 = 2.5F;
    private static final float SHIRT_Z0 = 2.0F;
    private static final float SHIRT_Z1 = 14.0F;
    /** The trousers under it, slid forward so their hems show. */
    private static final float TROUSERS_Y = 0.1F;
    private static final float TROUSERS_FORWARD = 1.5F;
    /** The sleeves lie on the shirt's outer layer, the left one over the right where they cross. */
    private static final float SLEEVE_Y = SHIRT_Y1 + OUTER + 0.05F;
    private static final float SLEEVE_STEP = 0.25F;
    /** From the shoulder (this far in from the shirt's edge, at the neck) slanting in this many degrees. */
    private static final float SLEEVE_INSET = 2.0F;
    private static final float SLEEVE_TILT = 20.0F;
    private static final float SLEEVE_LENGTH = 8.0F;
    /** For the item: raised so the outfit turns about its middle. */
    private static final float ITEM_LIFT = 6.5F;

    public record Look(Identifier texture, int tint) {
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Look> bake(BakingContext context) {
            return new OutfitRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public @Nullable Look extractArgument(ItemStack stack) {
        return look(stack);
    }

    /** How an outfit item looks: its clothing texture, or the bare body. */
    public static Look look(ItemStack stack) {
        String clothes = OutfitItem.clothes(stack);
        if (clothes.isEmpty()) {
            return new Look(BARE, ColorPalette.SKIN.getColor(0.5F, 0.5F, 0.0F));
        }
        return new Look(SkinExporter.getLibraryOrResourceIdentifier(clothes), 0xFFFFFFFF);
    }

    @Override
    public void submit(@Nullable Look look, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean glint, int outline) {
        if (look != null) {
            pose.pushPose();
            pose.translate(0.0F, ITEM_LIFT * PX, 0.0F);
            submitLook(look, pose, collector, light, overlay);
            pose.popPose();
        }
    }

    /** The folded outfit lying on the floor of the unit block, about 3 px tall (wardrobes and dressers use it too). */
    public static void submitLook(Look look, PoseStack pose, SubmitNodeCollector collector, int light) {
        submitLook(look, pose, collector, light, OverlayTexture.NO_OVERLAY);
    }

    private static void submitLook(Look look, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        collector.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(look.texture()), (p, buffer) -> {
            Faces faces = new Faces(buffer, p, look.tint(), light, overlay);
            trousers(faces, 0.0F, 0);
            trousers(faces, OUTER, 16);
            shirt(faces, 0.0F, 0);
            shirt(faces, OUTER, 16);
            sleeves(faces, 0.0F, 0);
            sleeves(faces, OUTER, 16);
        });
    }

    /**
     * The shirt as a box: chest on top, back underneath. Round its edges, as if folded over them: the ends take the two
     * rows of the chest next to them, the sides two columns of the torso's sides (the top of the torso and the legs are
     * often bare in a clothing texture).
     */
    private static void shirt(Faces f, float out, int layer) {
        float x0 = SHIRT_X0 - out;
        float x1 = SHIRT_X1 + out;
        float y0 = SHIRT_Y0 - out;
        float y1 = SHIRT_Y1 + out;
        float z0 = SHIRT_Z0 - out;
        float z1 = SHIRT_Z1 + out;
        f.up(x0, x1, y1, z0, z1, 20, 20 + layer, 28, 32 + layer);
        f.down(x0, x1, y0, z0, z1, 32, 20 + layer, 40, 32 + layer);
        int top = 20 + layer;
        int hem = 32 + layer;
        // the ends: the chest's top two rows at the neck, its bottom two at the hem, the edge row at the top
        f.quad(0, 0, -1, new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
                new float[][]{{28, top + 2}, {20, top + 2}, {20, top}, {28, top}});
        f.quad(0, 0, 1, new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                new float[][]{{20, hem - 2}, {28, hem - 2}, {28, hem}, {20, hem}});
        // the sides: the two columns of each side of the torso next to the chest, neck to hem along the side
        f.quad(-1, 0, 0, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                new float[][]{{18, top}, {18, hem}, {20, hem}, {20, top}});
        f.quad(1, 0, 0, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                new float[][]{{30, hem}, {30, top}, {28, top}, {28, hem}});
    }

    /** The trousers, flat under the shirt: both legs' fronts on top, their backs underneath. */
    private static void trousers(Faces f, float out, int layer) {
        float y = TROUSERS_Y + out;
        float yBelow = TROUSERS_Y - out;
        float z0 = SHIRT_Z0 + TROUSERS_FORWARD;
        float z1 = SHIRT_Z1 + TROUSERS_FORWARD;
        float mid = (SHIRT_X0 + SHIRT_X1) / 2;
        // the wearer's right leg (4, 20) and left leg (20, 52); their outer layers at (4, 36) and (4, 52)
        int rightV = 20 + layer;
        int leftU = layer == 0 ? 20 : 4;
        f.up(SHIRT_X0, mid, y, z0, z1, 4, rightV, 8, rightV + 12);
        f.up(mid, SHIRT_X1, y, z0, z1, leftU, 52, leftU + 4, 64);
        f.down(mid, SHIRT_X0, yBelow, z0, z1, 12, rightV, 16, rightV + 12);
        f.down(SHIRT_X1, mid, yBelow, z0, z1, leftU + 8, 52, leftU + 12, 64);
    }

    /** The sleeves folded over the chest: each arm's front, from the shoulder slanting in towards the middle. */
    private static void sleeves(Faces f, float out, int layer) {
        float slant = (float) Math.sin(Math.toRadians(SLEEVE_TILT));
        float along = (float) Math.cos(Math.toRadians(SLEEVE_TILT));
        float z = SHIRT_Z0 + 0.5F;
        // the wearer's right arm (44, 20), outer layer (44, 36); the left arm (36, 52), outer layer (52, 52)
        int rightV = 20 + layer;
        int leftU = layer == 0 ? 36 : 52;
        f.strip(SHIRT_X0 + SLEEVE_INSET, SLEEVE_Y + out, z, slant, along, 44, rightV, 48, rightV + 12);
        f.strip(SHIRT_X1 - SLEEVE_INSET, SLEEVE_Y + SLEEVE_STEP + out, z, -slant, along, leftU, 52, leftU + 4, 64);
    }

    private static float[][] uv(float u0, float v0, float u1, float v1) {
        return new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
    }

    /** Writes quads; corners are given counter-clockwise as seen from outside, in pixels, with their texture pixels. */
    private record Faces(VertexConsumer buffer, PoseStack.Pose pose, int tint, int light, int overlay) {
        /**
         * A face looking up, its texture's top at -z: from {@code xLeft} (the texture's left) to {@code xRight}. Seen from
         * above, left is -x.
         */
        void up(float xLeft, float xRight, float y, float z0, float z1, float u0, float v0, float u1, float v1) {
            quad(0, 1, 0, new float[][]{{xLeft, y, z1}, {xRight, y, z1}, {xRight, y, z0}, {xLeft, y, z0}}, uv(u0, v0, u1, v1));
        }

        /** A face looking down, its texture's top at -z. Seen from below, left is +x: give {@code xLeft} > {@code xRight}. */
        void down(float xLeft, float xRight, float y, float z0, float z1, float u0, float v0, float u1, float v1) {
            quad(0, -1, 0, new float[][]{{xLeft, y, z1}, {xRight, y, z1}, {xRight, y, z0}, {xLeft, y, z0}}, uv(u0, v0, u1, v1));
        }

        /**
         * A face looking up, 4 px wide and {@link #SLEEVE_LENGTH} long, its texture's top at ({@code x}, {@code z}) and
         * running down along ({@code dx}, {@code dz}), a unit length.
         */
        void strip(float x, float y, float z, float dx, float dz, float u0, float v0, float u1, float v1) {
            // the texture's left, seen from above
            float lx = -dz * 2.0F;
            float lz = dx * 2.0F;
            float ex = x + dx * SLEEVE_LENGTH;
            float ez = z + dz * SLEEVE_LENGTH;
            quad(0, 1, 0, new float[][]{{ex + lx, y, ez + lz}, {ex - lx, y, ez - lz}, {x - lx, y, z - lz}, {x + lx, y, z + lz}},
                    uv(u0, v0, u1, v1));
        }

        void quad(float nx, float ny, float nz, float[][] corners, float[][] uvs) {
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, corners[i][0] * PX, corners[i][1] * PX, corners[i][2] * PX)
                        .setColor(tint)
                        .setUv(uvs[i][0] / 64.0F, uvs[i][1] / 64.0F)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float x : new float[]{0.0F, 1.0F}) {
            for (float y : new float[]{ITEM_LIFT * PX, (ITEM_LIFT + SLEEVE_Y + SLEEVE_STEP + OUTER) * PX}) {
                for (float z : new float[]{(SHIRT_Z0 - OUTER) * PX, (SHIRT_Z1 + TROUSERS_FORWARD) * PX}) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }
}
