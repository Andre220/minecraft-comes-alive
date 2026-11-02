package net.conczin.mca.client.gui.workorder;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small isometric pictures of blueprints for the library cards: every block drawn as a little cube in its map color,
 * the top lit, the two visible sides shaded, painted back to front. Made once per blueprint and kept while the game
 * runs.
 */
final class BlueprintThumbnails {
    /** The longest side a picture gets, in pixels; cubes shrink to fit, but not below this many pixels across. */
    private static final int MAX_SIZE = 112;
    private static final int MIN_CUBE = 2;
    private static final int MAX_CUBE = 8;
    private static final float LEFT_SHADE = 0.78F;
    private static final float RIGHT_SHADE = 0.6F;

    private record Thumbnail(Blueprint blueprint, Identifier texture, int width, int height) {
    }

    private static final Map<String, Thumbnail> CACHE = new HashMap<>();
    private static int nextId;

    private BlueprintThumbnails() {
    }

    /**
     * Draws the picture of a blueprint centred in the box, as large as fits without stretching. False if there is
     * nothing to draw.
     */
    static boolean draw(GuiGraphicsExtractor graphics, Blueprint blueprint, int x, int y, int width, int height) {
        Thumbnail thumbnail = get(blueprint);
        if (thumbnail == null) {
            return false;
        }
        float scale = Math.min((float) width / thumbnail.width(), (float) height / thumbnail.height());
        int drawWidth = Math.max(1, Math.round(thumbnail.width() * scale));
        int drawHeight = Math.max(1, Math.round(thumbnail.height() * scale));
        graphics.blit(RenderPipelines.GUI_TEXTURED, thumbnail.texture(), x + (width - drawWidth) / 2, y + (height - drawHeight) / 2,
                0.0F, 0.0F, drawWidth, drawHeight, thumbnail.width(), thumbnail.height(), thumbnail.width(), thumbnail.height());
        return true;
    }

    private static Thumbnail get(Blueprint blueprint) {
        String key = blueprint.id().toString();
        Thumbnail cached = CACHE.get(key);
        if (cached != null && cached.blueprint() == blueprint) {
            return cached;
        }
        if (cached != null) {
            // captured again: the old picture is stale
            Minecraft.getInstance().getTextureManager().release(cached.texture());
        }
        Thumbnail made = make(blueprint);
        if (made == null) {
            CACHE.remove(key);
            return null;
        }
        CACHE.put(key, made);
        return made;
    }

    private static Thumbnail make(Blueprint blueprint) {
        List<Blueprint.Entry> solid = new ArrayList<>();
        for (Blueprint.Entry entry : blueprint.blocks()) {
            if (!entry.state().isAir() && entry.state().getFluidState().isEmpty()) {
                solid.add(entry);
            }
        }
        if (solid.isEmpty()) {
            return null;
        }
        int sizeX = blueprint.sizeX();
        int sizeY = blueprint.sizeY();
        int sizeZ = blueprint.sizeZ();
        int span = sizeX + sizeZ;
        // a = half the width of a cube; kept even so the top diamond stays sharp
        int a = Math.clamp((MAX_SIZE / span) & ~1, MIN_CUBE, MAX_CUBE);
        int width = span * a;
        int height = span * a / 2 + sizeY * a;
        byte[] mask = cubeMask(a);

        // back to front: the cubes nearer to the viewer (high x, y and z) cover the ones behind
        solid.sort(Comparator.<Blueprint.Entry>comparingInt(entry -> entry.pos().getX() + entry.pos().getY() + entry.pos().getZ())
                .thenComparingInt(entry -> entry.pos().getY()));
        NativeImage image = new NativeImage(width, height, true);
        for (Blueprint.Entry entry : solid) {
            BlockPos pos = entry.pos();
            int left = (pos.getX() - pos.getZ() + sizeZ - 1) * a;
            int top = (pos.getX() + pos.getZ()) * a / 2 + (sizeY - 1 - pos.getY()) * a;
            int base = colorOf(entry.state());
            int[] shades = {base, shade(base, LEFT_SHADE), shade(base, RIGHT_SHADE)};
            for (int py = 0; py < 2 * a; py++) {
                for (int px = 0; px < 2 * a; px++) {
                    int face = mask[py * 2 * a + px];
                    int imageX = left + px;
                    int imageY = top + py;
                    if (face > 0 && imageX >= 0 && imageY >= 0 && imageX < width && imageY < height) {
                        image.setPixel(imageX, imageY, shades[face - 1]);
                    }
                }
            }
        }
        Identifier texture = MCA.locate("dynamic/blueprint_thumbnail/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
        return new Thumbnail(blueprint, texture, width, height);
    }

    /**
     * Which face each pixel of a cube {@code 2a} pixels square belongs to: 0 outside, 1 the top diamond, 2 the left
     * (south) side, 3 the right (east) side.
     */
    private static byte[] cubeMask(int a) {
        int size = 2 * a;
        byte[] mask = new byte[size * size];
        double half = a / 2.0;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double x = px + 0.5;
                double y = py + 0.5;
                byte face = 0;
                if (Math.abs(x - a) / a + Math.abs(y - half) / half <= 1.0) {
                    face = 1;
                } else if (y >= half) {
                    // below the diamond and above the cube's lower edges
                    double bottom = x < a ? 1.5 * a + x / 2.0 : 2.0 * a - (x - a) / 2.0;
                    if (y <= bottom) {
                        face = (byte) (x < a ? 2 : 3);
                    }
                }
                mask[py * size + px] = face;
            }
        }
        return mask;
    }

    private static int colorOf(BlockState state) {
        MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int rgb = color == MapColor.NONE ? 0x9A9A9A : color.col;
        return 0xFF000000 | rgb;
    }

    private static int shade(int argb, float factor) {
        int red = Math.round(((argb >> 16) & 0xFF) * factor);
        int green = Math.round(((argb >> 8) & 0xFF) * factor);
        int blue = Math.round((argb & 0xFF) * factor);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
