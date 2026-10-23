package net.conczin.mca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.util.ImmersiveLibraryIds;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DynamicSkinCache {
    private record SkinKey(
        String genderDataName,
        boolean female,
        boolean albinism,
        boolean heterochromia,
        float skinGene,
        float melaninGene,
        float hemoglobinGene,
        float faceGene,
        float eumelaninGene,
        float pheomelaninGene,
        float eyeBrightnessGene,
        String skin,
        String hairBase,
        String hairBangs,
        String hairBack,
        String hairFront,
        String hairExtra,
        int skinDye,
        int hairDye,
        int eyeDye,
        int eyeLeftDye,
        String clothes,
        int infectionProgressKey,
        boolean freshAnimationsPupils,
        boolean outerLayers,
        String beard
    ) {
        public static SkinKey from(VillagerVisuals visuals, boolean freshAnimationsPupils, boolean outerLayers) {
            int infProgress = Math.round(visuals.infectionProgress() * 100.0f);
            return new SkinKey(
                visuals.genderDataName(),
                visuals.female(),
                visuals.albinism(),
                visuals.heterochromia(),
                visuals.skinGene(),
                visuals.melaninGene(),
                visuals.hemoglobinGene(),
                visuals.faceGene(),
                visuals.eumelaninGene(),
                visuals.pheomelaninGene(),
                visuals.eyeBrightnessGene(),
                visuals.skin(),
                visuals.hairBase(),
                visuals.hairBangs(),
                visuals.hairBack(),
                visuals.hairFront(),
                visuals.hairExtra(),
                visuals.skinDye(),
                visuals.hairDye(),
                visuals.eyeDye(),
                visuals.eyeLeftDye(),
                visuals.clothes(),
                infProgress,
                freshAnimationsPupils,
                outerLayers,
                visuals.beard()
            );
        }

        public String getUniqueId() {
            return genderDataName + "_" +
                   female + "_" +
                   albinism + "_" +
                   heterochromia + "_" +
                   skinGene + "_" +
                   melaninGene + "_" +
                   hemoglobinGene + "_" +
                   faceGene + "_" +
                   eumelaninGene + "_" +
                   pheomelaninGene + "_" +
                   eyeBrightnessGene + "_" +
                   skin + "_" +
                   hairBase + "_" +
                   hairBangs + "_" +
                   hairBack + "_" +
                   hairFront + "_" +
                   hairExtra + "_" +
                   skinDye + "_" +
                   hairDye + "_" +
                   eyeDye + "_" +
                   eyeLeftDye + "_" +
                   clothes + "_" +
                   String.format(java.util.Locale.ROOT, "%05d", infectionProgressKey) + "_" +
                   freshAnimationsPupils + "_" +
                   outerLayers + "_v2_" + beard; // expansion: looks - layout version (6x6 eye slots), beard
        }

        boolean references(String identifier) {
            return identifier.equals(skin)
                   || identifier.equals(beard)
                   || identifier.equals(hairBase)
                   || identifier.equals(hairBangs)
                   || identifier.equals(hairBack)
                   || identifier.equals(hairFront)
                   || identifier.equals(hairExtra)
                   || identifier.equals(clothes);
        }
    }

    private static final Set<SkinKey> INCOMPLETE_CACHE = new HashSet<>();
    private static final Map<SkinKey, Identifier> CACHE = new MaxSizeHashMap<>(128, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<SkinKey, Identifier> eldest) {
            boolean remove = super.removeEldestEntry(eldest);
            if (remove) {
                Identifier id = eldest.getValue();
                Minecraft.getInstance().getTextureManager().release(id);
                INCOMPLETE_CACHE.remove(eldest.getKey());
            }
            return remove;
        }
    };

    private static boolean isMissingImmersiveLibraryAssets(VillagerVisuals visuals) {
        String clothes = visuals.clothes();
        if (!MCA.isBlankString(clothes)) {
            var contentId = ImmersiveLibraryIds.contentId(clothes);
            if (contentId.isPresent() && !SkinCache.isLoaded(contentId.getAsInt())) {
                return true;
            }
        }
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String hair = visuals.layeredHair(category);
            if (!MCA.isBlankString(hair)) {
                var contentId = ImmersiveLibraryIds.contentId(hair);
                if (contentId.isPresent() && !SkinCache.isLoaded(contentId.getAsInt())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void clear() {
        CACHE.values().forEach(DynamicSkinCache::releaseDynamicTexture);
        CACHE.clear();
        INCOMPLETE_CACHE.clear();
        McaVillagerFeatureSlots.clearFaceMatches();
    }

    /** Evict stitched skins when a source texture changes under the same identifier. */
    public static void invalidateSourceTexture(Identifier source) {
        if (source == null) {
            return;
        }
        CACHE.entrySet().removeIf(entry -> {
            if (!entry.getKey().references(source.toString())) {
                return false;
            }
            releaseDynamicTexture(entry.getValue());
            INCOMPLETE_CACHE.remove(entry.getKey());
            return true;
        });
    }

    public static Identifier getOrCreateStitchedSkin(VillagerVisuals visuals) {
        return getOrCreateStitchedSkin(visuals, true);
    }

    /**
     * @param freshAnimationsPupils whether the model's own moving pupils (Fresh Animations) take the
     *                              iris's role, on faces whose eyes sit where those pupils move
     */
    public static Identifier getOrCreateStitchedSkin(VillagerVisuals visuals, boolean freshAnimationsPupils) {
        return getOrCreateStitchedSkin(visuals, freshAnimationsPupils, true);
    }

    /**
     * @param outerLayers false leaves the outer layer regions (hat, jacket, sleeves, pants) empty, for
     *                    models whose outer layers are drawn separately as cubes
     */
    public static Identifier getOrCreateStitchedSkin(VillagerVisuals visuals, boolean freshAnimationsPupils, boolean outerLayers) {
        SkinKey key = SkinKey.from(visuals, freshAnimationsPupils, outerLayers);
        boolean missingAssets = isMissingImmersiveLibraryAssets(visuals);
        Identifier cachedId = CACHE.get(key);
        if (cachedId != null) {
            if (!missingAssets && INCOMPLETE_CACHE.remove(key)) {
                Minecraft.getInstance().getTextureManager().release(cachedId);
                CACHE.remove(key);
            } else {
                return cachedId;
            }
        }

        Identifier newId = generateStitchedSkin(visuals, key);
        CACHE.put(key, newId);
        if (missingAssets) {
            INCOMPLETE_CACHE.add(key);
        } else {
            INCOMPLETE_CACHE.remove(key);
        }
        return newId;
    }

    private static Identifier generateStitchedSkin(VillagerVisuals visuals, SkinKey key) {
        try {
            Identifier skinId = SkinExporter.getSkin(visuals);
            Identifier faceId = SkinExporter.getFace(visuals);
            String variant = "normal";
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(visuals.entityId());
                if (entity instanceof ZombieVillagerEntityMCA) {
                    variant = "zombie";
                }
            }
            Identifier clothesId = SkinExporter.getClothes(visuals, variant);

            // The composite takes the largest layer resolution, so detailed textures keep their detail.
            int size = Math.max(64, Math.max(SkinExporter.textureWidth(skinId), SkinExporter.textureWidth(faceId)));
            if (clothesId != null) {
                size = Math.max(size, SkinExporter.textureWidth(clothesId));
            }
            for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
                String hair = visuals.layeredHair(category);
                if (!MCA.isBlankString(hair)) {
                    size = Math.max(size, SkinExporter.textureWidth(SkinExporter.getLibraryOrResourceIdentifier(hair)));
                }
            }
            NativeImage base = new NativeImage(size, size, true);

            int skinColor = SkinExporter.getSkinColor(visuals);
            SkinExporter.composite(base, skinId, skinColor);
            McaVillagerFeatureSlots.clearSlots(base);

            // Faces whose eyes sit where Fresh Animations' eyes move hand them to its planes: the eye
            // shapes go to the eye white slots and the moving pupil takes the iris's role. Other faces
            // keep their own eyes and iris; the slots stay empty.
            boolean movingPupils = key.freshAnimationsPupils() && McaVillagerFeatureSlots.eyesMatchFreshAnimations(faceId);
            if (movingPupils) {
                McaVillagerFeatureSlots.compositeMovingEyes(base, faceId, visuals);
            } else {
                SkinExporter.compositeFace(base, faceId, visuals, true);
            }

            // expansion: looks - the beard over the face, in the hair colour
            if (!MCA.isBlankString(key.beard())) {
                SkinExporter.composite(base, Identifier.parse(key.beard()), SkinExporter.getHairColor(visuals));
            }

            // The feature slots belong to the face: clothes and hair may have stray pixels there.
            int[] slots = McaVillagerFeatureSlots.saveSlots(base);
            if (clothesId != null) {
                SkinExporter.composite(base, clothesId, 0xFFFFFFFF);
            }

            int hairColor = SkinExporter.getHairColor(visuals);
            for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
                String identifier = visuals.layeredHair(category);
                if (!MCA.isBlankString(identifier)) {
                    SkinExporter.composite(base, SkinExporter.getLibraryOrResourceIdentifier(identifier), hairColor);
                    Identifier overlay = getResourceOverlayIdentifier(identifier);
                    if (overlay != null) {
                        SkinExporter.composite(base, overlay, 0xFFFFFFFF);
                    }
                }
            }

            McaVillagerFeatureSlots.restoreSlots(base, slots);
            if (!key.outerLayers()) {
                McaVillagerFeatureSlots.clearOuterLayers(base);
            }
            if (movingPupils) {
                McaVillagerFeatureSlots.paintDefaults(base, visuals);
            }

            Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/stitched/" + keyId(key.getUniqueId()));
            Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newId::toString, base));
            return newId;
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to generate dynamic stitched skin", e);
            return Identifier.parse("textures/entity/steve.png");
        }
    }

    private static Identifier getResourceOverlayIdentifier(String identifier) {
        if (ImmersiveLibraryIds.contentId(identifier).isPresent() || !identifier.endsWith(".png")) {
            return null;
        }
        Identifier id = Identifier.parse(identifier);
        Identifier overlay = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace(".png", "_overlay.png"));
        return Minecraft.getInstance().getResourceManager().getResource(overlay).isPresent() ? overlay : null;
    }

    private static String keyId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void releaseDynamicTexture(Identifier id) {
        if (id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/")) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}
