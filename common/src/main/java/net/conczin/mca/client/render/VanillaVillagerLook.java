package net.conczin.mca.client.render;

import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.LivingEntity;
import net.conczin.mca.Config;
import net.conczin.mca.client.render.voxel.VoxelLayerRenderer;
import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.SkinSelection;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * MCA visuals for villagers drawn by the squidward (vanilla) renderer: MCA villagers use their
 * own data, plain vanilla villagers get deterministic visuals seeded by their UUID.
 */
public final class VanillaVillagerLook {
    private static final int NO_DYE = 0xFF000000;
    private static final int NATURAL_EYE_DYE = 0xFFFFFFFF;
    private static final Map<UUID, Genome> GENOMES = new MaxSizeHashMap<>(512);

    private VanillaVillagerLook() {
    }

    public static boolean isEnabled() {
        Config config = Config.getInstance();
        return config.useSquidwardModels && (config.squidwardHumanLikeModels || config.freshAnimationsIntegration);
    }

    /**
     * MCA villagers drawn by the squidward renderer use their own data, children included; plain
     * vanilla villagers get a look seeded by their UUID, babies with toddler proportions.
     */
    public static @Nullable VillagerVisuals visualsFor(Villager villager) {
        return visualsFor(villager, professionKey(villager.getVillagerData()));
    }

    /** The same for zombie villagers: MCA's with their own data (and zombie clothes), vanilla ones seeded by UUID. */
    public static @Nullable VillagerVisuals visualsFor(ZombieVillager zombie) {
        return visualsFor(zombie, professionKey(zombie.getVillagerData()));
    }

    private static @Nullable VillagerVisuals visualsFor(LivingEntity villager, String profession) {
        if (!isEnabled()) {
            return null;
        }
        if (villager instanceof VillagerLike<?> mcaVillager) {
            return mcaVillager.getGenetics() == null ? null : VillagerVisuals.capture(mcaVillager, villager);
        }

        Genome genome = GENOMES.computeIfAbsent(villager.getUUID(), Genome::create);
        AgeState age = villager.isBaby() ? AgeState.TODDLER : AgeState.ADULT;
        String clothes = genome.clothesFor(profession);
        return new VillagerVisuals(
                genome.gender.getDataName(),
                genome.gender == Gender.FEMALE,
                false,
                villager.isBaby() ? 0.0F : genome.breastSize,
                new VillagerVisuals.Dimensions(age.getWidth(), age.getHeight(), age.getBreasts(), age.getHead()),
                genome.widthFactor * age.getWidth(),
                genome.heightFactor * age.getHeight(),
                false,
                false,
                false,
                false,
                false,
                genome.skinGene,
                genome.melaninGene,
                genome.hemoglobinGene,
                genome.faceGene,
                genome.eumelaninGene,
                genome.pheomelaninGene,
                genome.eyeBrightnessGene,
                genome.skin,
                genome.hair.layer(LayeredHair.Category.BASE),
                genome.hair.layer(LayeredHair.Category.BANGS),
                genome.hair.layer(LayeredHair.Category.BACK),
                genome.hair.layer(LayeredHair.Category.FRONT),
                genome.hair.layer(LayeredHair.Category.EXTRA),
                NO_DYE,
                NO_DYE,
                NATURAL_EYE_DYE,
                NATURAL_EYE_DYE,
                clothes,
                0.0F,
                (int) (villager.getId() + villager.tickCount),
                villager.getId(),
                villager.isSleeping(),
                villager.isDeadOrDying(),
                ""
        );
    }

    // The human-like mesh already carries the vanilla villager scale; genetic sizes are relative to
    // MCA's unscaled player-sized model, as in MCA's own renderer.
    public static final float MESH_SCALE = 0.9375F;
    // Height of the unscaled player-sized model in blocks.
    private static final float MODEL_HEIGHT = 2.0F;

    /** Extra model scale for a villager's genetic and age size: {horizontal, vertical}. */
    public static float[] modelScale(VillagerVisuals visuals) {
        float child = VillagerLike.squidwardChildSize(visuals.dimensions().getHeight());
        return new float[]{visuals.rawHorizontalScaleFactor() * child / MESH_SCALE, visuals.rawVerticalScaleFactor() * child / MESH_SCALE};
    }

    /** Rendered height in blocks, for placing the name tag. */
    public static float renderedHeight(VillagerVisuals visuals) {
        return MODEL_HEIGHT * visuals.rawVerticalScaleFactor() * VillagerLike.squidwardChildSize(visuals.dimensions().getHeight());
    }

    /** The full MCA texture of a human-like villager, outer layers included. */
    public static Identifier textureFor(VillagerVisuals visuals) {
        return textureFor(visuals, true);
    }

    /**
     * The MCA texture a human-like villager is drawn with. Without outer layers when they are drawn
     * as cubes instead, so the flat layer shows nothing whatever model replaces the parts.
     */
    public static Identifier textureFor(VillagerVisuals visuals, boolean outerLayers) {
        return DynamicSkinCache.getOrCreateStitchedSkin(visuals, Config.getInstance().freshAnimationsIntegration, outerLayers);
    }

    /**
     * Whether the outer layers are drawn as cubes for this villager (close enough and enabled). Screen previews too,
     * like the editor's: the layer poses the model right before placing the cubes (VillagerParts.poseNow), so they
     * follow a preview standing and turning towards the mouse.
     */
    public static boolean use3dLayers(net.minecraft.client.renderer.entity.state.VillagerRenderState state) {
        Config config = Config.getInstance();
        boolean preview = state instanceof VillagerStateHolder holder && holder.mca$getPreviewEntityAnimationState() != null;
        return config.useSquidwardModels && config.squidward3dLayers
               && state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null
               && (preview || VoxelLayerRenderer.inRange(state.distanceToCameraSq));
    }

    private static String professionKey(VillagerData data) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.profession().value()).toString();
    }

    private static <T extends SkinListEntry> @Nullable T pickWeighted(List<T> entries, Random random) {
        float total = 0.0F;
        for (T entry : entries) {
            total += Math.max(0.0F, entry.getChance());
        }
        if (total <= 0.0F) {
            return entries.isEmpty() ? null : entries.get(random.nextInt(entries.size()));
        }
        float roll = random.nextFloat() * total;
        for (T entry : entries) {
            roll -= Math.max(0.0F, entry.getChance());
            if (roll < 0.0F) {
                return entry;
            }
        }
        return entries.getLast();
    }

    private static final class Genome {
        private static final HairStyle NO_HAIR = new HairStyle("", Gender.NEUTRAL, 0.0F, "", "", "", "", "");

        private final long seed;
        private final Gender gender;
        private final float breastSize;
        private final float skinGene;
        private final float melaninGene;
        private final float hemoglobinGene;
        private final float faceGene;
        private final float eumelaninGene;
        private final float pheomelaninGene;
        private final float eyeBrightnessGene;
        // Same formulas as Genetics and Gender: gene based size times the configured gender factor.
        private final float heightFactor;
        private final float widthFactor;
        private final String skin;
        private final HairStyle hair;
        private final Map<String, String> clothesByProfession = new MaxSizeHashMap<>(4);

        private Genome(UUID uuid) {
            this.seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
            Random random = new Random(seed);
            this.gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
            this.breastSize = gender == Gender.FEMALE ? random.nextFloat() : 0.0F;
            this.skinGene = random.nextFloat();
            this.melaninGene = random.nextFloat();
            this.hemoglobinGene = random.nextFloat();
            this.faceGene = random.nextFloat();
            this.eumelaninGene = random.nextFloat();
            this.pheomelaninGene = random.nextFloat();
            this.eyeBrightnessGene = random.nextFloat();
            this.heightFactor = Genetics.squidwardHeight(0.75F + centered(random) / 2) * gender.getScaleFactor();
            this.widthFactor = (0.75F + centered(random) / 2) * gender.getHorizontalScaleFactor();

            var bodySkin = pickWeighted(sorted(SkinSelection.forGender(ClientSkinCatalog.bodySkins().values(), gender)), random);
            this.skin = bodySkin == null ? "" : bodySkin.getIdentifier();

            HairStyle style = pickWeighted(sorted(SkinSelection.forGender(ClientSkinCatalog.hairStyles().values(), gender)), random);
            this.hair = style == null ? NO_HAIR : style;
        }

        /** A gene value around 0.5, like Genetics' centered randomization. */
        private static float centered(Random random) {
            return (random.nextFloat() + random.nextFloat()) / 2;
        }

        static Genome create(UUID uuid) {
            return new Genome(uuid);
        }

        String clothesFor(String profession) {
            return clothesByProfession.computeIfAbsent(profession, key -> {
                Map<String, String> conversions = Config.getInstance().professionConversionsMap;
                var available = ClientSkinCatalog.clothing().values();
                List<Clothing> options = SkinSelection.clothingForProfession(available, gender, SkinSelection.mapProfession(key, conversions));
                if (options.isEmpty()) {
                    options = SkinSelection.clothingForProfession(available, gender, SkinSelection.mapProfession("minecraft:none", conversions));
                }
                Clothing clothing = pickWeighted(options, new Random(seed ^ key.hashCode()));
                return clothing == null ? "" : clothing.getIdentifier();
            });
        }

        private static <T extends SkinListEntry> List<T> sorted(List<T> entries) {
            return entries.stream()
                    .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                    .toList();
        }
    }
}
