package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.client.resources.SkinExporter;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Face feature slots of the MCA villager texture layout, in 64 unit texture space. They live in
 * the head's unused corner (0,0)-(8,8) and, for the eye whites, the unused (56,16)-(64,32) beside the
 * right arm; no player box samples either. The human-like villager model
 * for Fresh Animations maps its moving face parts here
 * ({@code resourcepacks/mca_fresh_animations}); keep both in sync.
 */
public final class McaVillagerFeatureSlots {
    public static final int RIGHT_PUPIL_X = 0;
    public static final int LEFT_PUPIL_X = 1;
    public static final int PUPIL_Y = 0;
    /** Mouth: (2,0)-(6,1). Brow: (0,6)-(8,8). Inside of the mouth (dark) at (6,0), tongue (pink) at (7,0). */
    public static final int LAYOUT_SIZE = 64;
    /** Closed eye lines of the sleeping model, 2x1 each: right at (6,1), left at (6,2). */
    private static final int CLOSED_EYE_X = 6;
    private static final int RIGHT_CLOSED_EYE_Y = 1;
    private static final int LEFT_CLOSED_EYE_Y = 2;
    private static final int CLOSED_EYE_COLOR = 0xFF3A2A22;
    // expansion: looks - solid colours for the talking mouth planes of the Fresh Animations model
    private static final int MOUTH_INSIDE_X = 6;
    private static final int TONGUE_X = 7;
    private static final int MOUTH_INSIDE_COLOR = 0xFF1A0D0D;
    private static final int TONGUE_COLOR = 0xFFD9707A;

    // Eye shapes, {x, y, width, height}: where they are drawn on the face (relative to its corner
    // at (8, 8)) and the eye white slots the Fresh Animations planes show them from.
    private static final int[] RIGHT_EYE_ON_FACE = {0, 4, 3, 3};
    private static final int[] LEFT_EYE_ON_FACE = {5, 4, 3, 3};
    // expansion: looks - the eye white slots are twice as large: a face may draw detailed eyes right
    // there, else its eyes on the face are scaled up
    private static final int[] RIGHT_EYE_WHITE_SLOT = {56, 16, 6, 6};
    private static final int[] LEFT_EYE_WHITE_SLOT = {56, 24, 6, 6};
    /** Regions of the layout that belong to the face features, {x, y, width, height}. */
    private static final int[][] SLOT_REGIONS = {{0, 0, 8, 8}, {56, 16, 8, 16}};

    private McaVillagerFeatureSlots() {
    }

    // The face of the human-like model starts at (8, 8) in the layout.
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final Map<Identifier, Boolean> FACE_MATCHES = new ConcurrentHashMap<>();

    /**
     * Whether a face texture draws both eyes inside the areas Fresh Animations' eye planes show, so
     * its eyes can move there. Cached per face texture.
     */
    public static boolean eyesMatchFreshAnimations(Identifier faceId) {
        return FACE_MATCHES.computeIfAbsent(faceId, id -> {
            NativeImage face = SkinExporter.loadTexture(id);
            if (face == null) {
                return false;
            }
            try {
                int scale = Math.max(1, face.getWidth() / LAYOUT_SIZE);
                return hasPixels(face, RIGHT_EYE_ON_FACE, scale) && hasPixels(face, LEFT_EYE_ON_FACE, scale);
            } finally {
                face.close();
            }
        });
    }

    private static boolean hasPixels(NativeImage face, int[] onFace, int scale) {
        for (int dx = 0; dx < onFace[2] * scale; dx++) {
            for (int dy = 0; dy < onFace[3] * scale; dy++) {
                if (ARGB.alpha(face.getPixel((FACE_X + onFace[0]) * scale + dx, (FACE_Y + onFace[1]) * scale + dy)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Composites a face whose eyes are animated by Fresh Animations: the eye shapes (sclera and
     * details) are moved from the face into the eye white slots, and the skin under them is kept,
     * so only the moving planes show the eyes. The iris is left to the moving pupils.
     */
    public static void compositeMovingEyes(NativeImage base, Identifier faceId, VillagerVisuals visuals) {
        int scale = Math.max(1, base.getWidth() / LAYOUT_SIZE);
        NativeImage eyes = new NativeImage(base.getWidth(), base.getHeight(), true);
        NativeImage face = new NativeImage(base.getWidth(), base.getHeight(), true);
        try {
            SkinExporter.compositeFace(eyes, faceId, visuals, false);
            SkinExporter.compositeFace(face, faceId, visuals, true);
            placeEye(base, eyes, RIGHT_EYE_ON_FACE, RIGHT_EYE_WHITE_SLOT, scale);
            placeEye(base, eyes, LEFT_EYE_ON_FACE, LEFT_EYE_WHITE_SLOT, scale);
            // The rest of the face (mouth, blush, ...) stays on the head.
            for (int x = 0; x < base.getWidth(); x++) {
                for (int y = 0; y < base.getHeight(); y++) {
                    if (!inEye(x, y, scale)) {
                        SkinExporter.compositePixel(base, x, y, face.getPixel(x, y), 0xFFFFFFFF);
                    }
                }
            }
        } finally {
            eyes.close();
            face.close();
        }
    }

    /**
     * Puts an eye into its slot: the face's own detailed eye drawn in the slot if it has one, else
     * its eye on the face scaled up to the slot.
     */
    private static void placeEye(NativeImage base, NativeImage eyes, int[] onFace, int[] slot, int scale) {
        int width = slot[2] * scale;
        int height = slot[3] * scale;
        boolean detailed = false;
        for (int dx = 0; dx < width && !detailed; dx++) {
            for (int dy = 0; dy < height && !detailed; dy++) {
                detailed = ARGB.alpha(eyes.getPixel(slot[0] * scale + dx, slot[1] * scale + dy)) != 0;
            }
        }
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                int pixel = detailed
                        ? eyes.getPixel(slot[0] * scale + dx, slot[1] * scale + dy)
                        : eyes.getPixel((FACE_X + onFace[0]) * scale + dx * onFace[2] / slot[2],
                                (FACE_Y + onFace[1]) * scale + dy * onFace[3] / slot[3]);
                base.setPixel(slot[0] * scale + dx, slot[1] * scale + dy, pixel);
            }
        }
    }

    private static boolean inEye(int x, int y, int scale) {
        return inRect(x, y, RIGHT_EYE_ON_FACE, scale) || inRect(x, y, LEFT_EYE_ON_FACE, scale)
               || inSlot(x, y, RIGHT_EYE_WHITE_SLOT, scale) || inSlot(x, y, LEFT_EYE_WHITE_SLOT, scale);
    }

    private static boolean inSlot(int x, int y, int[] slot, int scale) {
        return x >= slot[0] * scale && x < (slot[0] + slot[2]) * scale && y >= slot[1] * scale && y < (slot[1] + slot[3]) * scale;
    }

    private static boolean inRect(int x, int y, int[] onFace, int scale) {
        int x0 = (FACE_X + onFace[0]) * scale;
        int y0 = (FACE_Y + onFace[1]) * scale;
        return x >= x0 && x < x0 + onFace[2] * scale && y >= y0 && y < y0 + onFace[3] * scale;
    }

    public static void clearFaceMatches() {
        FACE_MATCHES.clear();
    }

    /**
     * Fills the pupil slots with the villager's eye colors unless the composited textures already
     * provide them. Other slots stay transparent until textures supply them.
     */
    public static void paintDefaults(NativeImage image, VillagerVisuals visuals) {
        int scale = Math.max(1, image.getWidth() / LAYOUT_SIZE);
        paintIfEmpty(image, RIGHT_PUPIL_X, PUPIL_Y, scale, visuals.eyeColor(0.0F, false));
        paintIfEmpty(image, LEFT_PUPIL_X, PUPIL_Y, scale, visuals.eyeColor(0.0F, true));
        paintIfEmpty(image, MOUTH_INSIDE_X, 0, scale, MOUTH_INSIDE_COLOR);
        paintIfEmpty(image, TONGUE_X, 0, scale, TONGUE_COLOR);
        // Closed lids for the sleeping model: right (6,1)-(8,2), left (6,2)-(8,3).
        for (int x = CLOSED_EYE_X; x < CLOSED_EYE_X + 2; x++) {
            paintIfEmpty(image, x, RIGHT_CLOSED_EYE_Y, scale, CLOSED_EYE_COLOR);
            paintIfEmpty(image, x, LEFT_CLOSED_EYE_Y, scale, CLOSED_EYE_COLOR);
        }
    }

    // Outer layer regions of the layout, as {x, y, width, height} in 64 unit space: hat, jacket,
    // right sleeve, left sleeve, right pants, left pants.
    private static final int[][] OUTER_LAYERS = {
            {32, 0, 32, 16},
            {16, 32, 24, 16},
            {40, 32, 16, 16},
            {48, 48, 16, 16},
            {0, 32, 16, 16},
            {0, 48, 16, 16},
    };

    /**
     * Empties the feature slot corner. Body skins may fill unused texture space, but the slots
     * belong to the face and its features.
     */
    public static void clearSlots(NativeImage image) {
        int scale = Math.max(1, image.getWidth() / LAYOUT_SIZE);
        for (int[] region : SLOT_REGIONS) {
            for (int x = region[0] * scale; x < (region[0] + region[2]) * scale; x++) {
                for (int y = region[1] * scale; y < (region[1] + region[3]) * scale; y++) {
                    image.setPixel(x, y, 0);
                }
            }
        }
    }

    /** A copy of the feature slots, to put back after layers that are not the face. */
    public static int[] saveSlots(NativeImage image) {
        int scale = Math.max(1, image.getWidth() / LAYOUT_SIZE);
        int count = 0;
        for (int[] region : SLOT_REGIONS) {
            count += region[2] * region[3] * scale * scale;
        }
        int[] pixels = new int[count];
        int i = 0;
        for (int[] region : SLOT_REGIONS) {
            for (int x = region[0] * scale; x < (region[0] + region[2]) * scale; x++) {
                for (int y = region[1] * scale; y < (region[1] + region[3]) * scale; y++) {
                    pixels[i++] = image.getPixel(x, y);
                }
            }
        }
        return pixels;
    }

    public static void restoreSlots(NativeImage image, int[] pixels) {
        int scale = Math.max(1, image.getWidth() / LAYOUT_SIZE);
        int i = 0;
        for (int[] region : SLOT_REGIONS) {
            for (int x = region[0] * scale; x < (region[0] + region[2]) * scale; x++) {
                for (int y = region[1] * scale; y < (region[1] + region[3]) * scale; y++) {
                    if (i < pixels.length) {
                        image.setPixel(x, y, pixels[i++]);
                    }
                }
            }
        }
    }

    /** Makes the outer layer regions transparent. */
    public static void clearOuterLayers(NativeImage image) {
        int scale = Math.max(1, image.getWidth() / LAYOUT_SIZE);
        for (int[] region : OUTER_LAYERS) {
            for (int y = region[1] * scale; y < (region[1] + region[3]) * scale; y++) {
                for (int x = region[0] * scale; x < (region[0] + region[2]) * scale; x++) {
                    image.setPixel(x, y, 0);
                }
            }
        }
    }

    private static void paintIfEmpty(NativeImage image, int x, int y, int scale, int color) {
        int opaque = ARGB.color(255, ARGB.red(color), ARGB.green(color), ARGB.blue(color));
        for (int dx = 0; dx < scale; dx++) {
            for (int dy = 0; dy < scale; dy++) {
                int px = x * scale + dx;
                int py = y * scale + dy;
                if (ARGB.alpha(image.getPixel(px, py)) == 0) {
                    image.setPixel(px, py, opaque);
                }
            }
        }
    }
}
