package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class EyeTextureLayers {
    private static final int SCLERA_MIN_CHANNEL = 160;
    private static final int SCLERA_MAX_CHANNEL_SPREAD = 32;
    private static final int IRIS_MIN_CHANNEL = 32;
    public static final int DETAILS_TINT = 0xFF808080;
    /**
     * A painted-in pupil: white, so the eye colour tints it to exactly that colour, as Fresh Animations' pupils are.
     * Its alpha is just under opaque, which keeps it from being taken for the eye white.
     */
    private static final int PAINTED_PUPIL = 0xFEFFFFFF;

    private EyeTextureLayers() {
    }

    public static int applyBrightness(int argb, float brightness) {
        float factor = 0.5F + Mth.clamp(brightness, 0.0F, 1.0F);
        int alpha = ARGB.alpha(argb);
        int red = scaleChannel(ARGB.red(argb), factor);
        int green = scaleChannel(ARGB.green(argb), factor);
        int blue = scaleChannel(ARGB.blue(argb), factor);
        return ARGB.color(alpha, red, green, blue);
    }

    private static int scaleChannel(int channel, float factor) {
        return Mth.clamp(Math.round(channel * factor), 0, 255);
    }

    public static Bounds findBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (ARGB.alpha(image.getPixel(x, y)) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("Face eye texture has no visible pixels");
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    /**
     * Faces drawn for Fresh Animations' moving pupils (expansion: looks) have only the eye whites. Where a face has no
     * iris of its own, paints a pupil in like those: one layout pixel square, in the middle of each eye.
     */
    public static void addMissingIris(NativeImage face) {
        int width = face.getWidth();
        int height = face.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = face.getPixel(x, y);
                if (ARGB.alpha(pixel) != 0 && !isScleraPixel(ARGB.alpha(pixel), ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel))) {
                    return;
                }
            }
        }
        Bounds all = findBounds(face);
        int splitX = all.minX() + all.width() / 2;
        int scale = Math.max(1, width / 64);
        paintPupil(face, all.minX(), splitX, scale);
        paintPupil(face, splitX, all.maxX() + 1, scale);
    }

    private static void paintPupil(NativeImage face, int fromX, int toX, int scale) {
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        int minY = Integer.MAX_VALUE;
        int maxY = -1;
        for (int x = fromX; x < toX; x++) {
            for (int y = 0; y < face.getHeight(); y++) {
                if (ARGB.alpha(face.getPixel(x, y)) != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return;
        }
        int x0 = (minX + maxX + 1) / 2 - scale / 2;
        int y0 = (minY + maxY + 1) / 2 - scale / 2;
        for (int x = x0; x < x0 + scale; x++) {
            for (int y = y0; y < y0 + scale; y++) {
                if (ARGB.alpha(face.getPixel(x, y)) != 0) {
                    face.setPixel(x, y, PAINTED_PUPIL);
                }
            }
        }
    }

    public static boolean isInSide(int x, int splitX, Side side) {
        return switch (side) {
            case FULL -> true;
            case LEFT -> x >= splitX;
            case RIGHT -> x < splitX;
        };
    }

    public static boolean isScleraPixel(int alpha, int red, int green, int blue) {
        if (alpha == 1) {
            return true;
        }
        if (alpha != 255) {
            return false;
        }

        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= SCLERA_MIN_CHANNEL && max - min <= SCLERA_MAX_CHANNEL_SPREAD;
    }

    public static boolean isPixelForLayer(Layer layer, int alpha, int red, int green, int blue) {
        if (alpha == 0) {
            return false;
        }

        boolean sclera = isScleraPixel(alpha, red, green, blue);
        int max = Math.max(red, Math.max(green, blue));
        return switch (layer) {
            case SCLERA -> sclera;
            case IRIS -> !sclera && max >= IRIS_MIN_CHANNEL;
            case DETAILS -> !sclera && max < IRIS_MIN_CHANNEL;
        };
    }

    public enum Side {
        FULL,
        LEFT,
        RIGHT
    }

    public enum Layer {
        SCLERA,
        IRIS,
        DETAILS
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX + 1;
        }
    }
}
