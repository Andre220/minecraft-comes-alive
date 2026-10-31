package net.conczin.mca.client.render;

import net.conczin.mca.expansion.looks.Beards;
import net.conczin.mca.Config;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.conczin.mca.entity.ai.traits.WalkStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public record VillagerVisuals(
        String genderDataName,
        boolean female,
        boolean baby,
        float breastSize,
        Dimensions dimensions,
        float rawHorizontalScaleFactor,
        float rawVerticalScaleFactor,
        boolean burned,
        boolean albinism,
        boolean rainbowHair,
        boolean rainbowEyes,
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
        float infectionProgress,
        int tickCount,
        int entityId,
        boolean sleeping,
        boolean deadOrDying,
        String beard, // expansion: looks
        boolean elder,
        WalkStyle walk
) {
    /** Visuals of someone who is not an elder and walks normally, e.g. the vanilla villager look. */
    public VillagerVisuals(String genderDataName, boolean female, boolean baby, float breastSize, Dimensions dimensions,
                           float rawHorizontalScaleFactor, float rawVerticalScaleFactor, boolean burned, boolean albinism,
                           boolean rainbowHair, boolean rainbowEyes, boolean heterochromia, float skinGene, float melaninGene,
                           float hemoglobinGene, float faceGene, float eumelaninGene, float pheomelaninGene, float eyeBrightnessGene,
                           String skin, String hairBase, String hairBangs, String hairBack, String hairFront, String hairExtra,
                           int skinDye, int hairDye, int eyeDye, int eyeLeftDye, String clothes, float infectionProgress,
                           int tickCount, int entityId, boolean sleeping, boolean deadOrDying, String beard) {
        this(genderDataName, female, baby, breastSize, dimensions, rawHorizontalScaleFactor, rawVerticalScaleFactor, burned,
                albinism, rainbowHair, rainbowEyes, heterochromia, skinGene, melaninGene, hemoglobinGene, faceGene, eumelaninGene,
                pheomelaninGene, eyeBrightnessGene, skin, hairBase, hairBangs, hairBack, hairFront, hairExtra, skinDye, hairDye,
                eyeDye, eyeLeftDye, clothes, infectionProgress, tickCount, entityId, sleeping, deadOrDying, beard, false, WalkStyle.NORMAL);
    }

    private static final int MIN_BLINK_INTERVAL_A = 50;
    private static final int MIN_BLINK_INTERVAL_B = 57;
    private static final int BLINK_INTERVAL_VARIANCE = 80;
    private static final int MIN_BLINK_DURATION = 1;
    private static final int BLINK_DURATION_VARIANCE = 4;
    private static final int NATURAL_DYE = 0xFFFFFFFF;
    /** The hair color of a villager without a dye. */
    private static final int NO_DYE = 0xFF000000;
    private static final int SILVER = 0xFFC8C8C8;
    /** How far the hair of an elder has turned silver. */
    private static final float ELDER_GREY = 0.7f;
    private static final int ALBINISM_EYE_COLOR = 0xFFE8A0A0;
    private static final int BLUE_EYE_COLOR = 0xFF557FA6;
    private static final int GREEN_EYE_COLOR = 0xFF5B8756;
    private static final int HAZEL_EYE_COLOR = 0xFF8A6A35;
    private static final int BROWN_EYE_COLOR = 0xFF4A2B18;

    public static VillagerVisuals require(Object state) {
        VillagerVisuals visuals = VillagerStateHolder.require(state).mca$getVisuals();
        if (visuals == null) {
            throw new IllegalStateException("No villager visuals available for render state");
        }
        return visuals;
    }

    public static VillagerVisuals capture(VillagerLike<?> villager) {
        return capture(villager, villager.asEntity());
    }

    /** Elders go grey: without a dye, their natural hair color fades towards silver. */
    private static int hairDye(VillagerLike<?> villager, Genetics genetics, Traits traits, AgeState age) {
        int dye = villager.getHairDye();
        if (age != AgeState.ELDER || dye != NO_DYE) {
            return dye;
        }
        float albinism = traits.hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        int natural = ColorPalette.HAIR.getColor(genetics.getGene(Genetics.EUMELANIN) * albinism,
                genetics.getGene(Genetics.PHEOMELANIN) * albinism, 0);
        return ARGB.srgbLerp(ELDER_GREY, natural, SILVER);
    }

    public static VillagerVisuals capture(VillagerLike<?> villager, LivingEntity renderEntity) {
        Genetics genetics = villager.getGenetics();
        Traits traits = villager.getTraits();
        LivingEntity entity = villager.asEntity();
        Gender gender = genetics.getGender();
        AgeState ageState = villager.getAgeState();
        VillagerDimensions dimensions = ageState == AgeState.UNASSIGNED && entity.isBaby()
                ? AgeState.TODDLER
                : villager.getVillagerDimensions();
        float rawHorizontalScaleFactor = genetics.getHorizontalScaleFactor()
                * traits.getHorizontalScaleFactor()
                * dimensions.getWidth()
                * gender.getHorizontalScaleFactor();
        float rawVerticalScaleFactor = genetics.getVerticalScaleFactor()
                * traits.getVerticalScaleFactor()
                * dimensions.getHeight()
                * gender.getScaleFactor();
        return new VillagerVisuals(
                gender.getDataName(),
                gender == Gender.FEMALE,
                ageState == AgeState.BABY,
                genetics.getBreastSize(),
                new Dimensions(
                        dimensions.getWidth(),
                        dimensions.getHeight(),
                        dimensions.getBreasts(),
                        dimensions.getHead()
                ),
                rawHorizontalScaleFactor,
                rawVerticalScaleFactor,
                villager.isBurned(),
                traits.hasTrait(Traits.ALBINISM),
                traits.hasTrait(Traits.RAINBOW),
                traits.hasTrait(Traits.RAINBOW_EYES),
                traits.hasTrait(Traits.HETEROCHROMIA),
                genetics.getGene(Genetics.SKIN),
                genetics.getGene(Genetics.MELANIN),
                genetics.getGene(Genetics.HEMOGLOBIN),
                genetics.getGene(Genetics.FACE),
                genetics.getGene(Genetics.EUMELANIN),
                genetics.getGene(Genetics.PHEOMELANIN),
                genetics.getGene(Genetics.EYE_BRIGHTNESS),
                villager.getSkin(),
                getHairLayer(villager, LayeredHair.Category.BASE),
                getHairLayer(villager, LayeredHair.Category.BANGS),
                getHairLayer(villager, LayeredHair.Category.BACK),
                getHairLayer(villager, LayeredHair.Category.FRONT),
                getHairLayer(villager, LayeredHair.Category.EXTRA),
                villager.getSkinDye(),
                hairDye(villager, genetics, traits, ageState),
                villager.getEyeDye(),
                villager.getEyeLeftDye(),
                villager.getClothes(),
                villager.getInfectionProgress(),
                animationTickCount(renderEntity),
                renderEntity.getId(),
                renderEntity.isSleeping(),
                renderEntity.isDeadOrDying(),
                Beards.forVisuals(villager),
                ageState == AgeState.ELDER,
                traits.getWalkStyle(ageState)
        );
    }

    private static String getHairLayer(VillagerLike<?> villager, LayeredHair.Category category) {
        String hair = villager.getLayeredHair(category);
        if (!isBlank(hair)) {
            return hair;
        }
        if (category == LayeredHair.Category.BASE) {
            return villager.getHairStyleId();
        }
        return "";
    }

    private static int animationTickCount(LivingEntity entity) {
        long ticks = entity.tickCount > 0 ? entity.tickCount : entity.level().getGameTime();
        return (int) (entity.getId() + ticks);
    }

    public boolean isBlinking() {
        if (sleeping || deadOrDying) {
            return true;
        }
        if (!Config.getInstance().mcaEyeBlinking) {
            return false;
        }

        int time = tickCount / 2 + (int) (hemoglobinGene * 65536);
        return isInBlinkWindow(time, MIN_BLINK_INTERVAL_A, 0x1EAF)
                || isInBlinkWindow(time, MIN_BLINK_INTERVAL_B, 0x57B1);
    }

    private boolean isInBlinkWindow(int time, int minInterval, int salt) {
        int interval = minInterval + randomBlinkValue(salt, BLINK_INTERVAL_VARIANCE + 1);
        int phase = randomBlinkValue(salt ^ 0x3459, interval);
        int phasedTime = time + phase;
        int cycle = Math.floorDiv(phasedTime, interval);
        int duration = MIN_BLINK_DURATION + randomBlinkValue(salt ^ cycle, BLINK_DURATION_VARIANCE + 1);
        return Math.floorMod(phasedTime, interval) < duration;
    }

    private int randomBlinkValue(int salt, int bound) {
        int seed = entityId * 0x45D9F3B + salt;
        seed ^= (int) (hemoglobinGene * 0x10000);
        seed ^= seed >>> 16;
        seed *= 0x45D9F3B;
        seed ^= seed >>> 16;
        return Math.floorMod(seed, bound);
    }

    public String layeredHair(LayeredHair.Category category) {
        return switch (category) {
            case BASE -> hairBase;
            case BANGS -> hairBangs;
            case BACK -> hairBack;
            case FRONT -> hairFront;
            case EXTRA -> hairExtra;
        };
    }

    public int eyeColor(float tickDelta, boolean left) {
        int color;
        if (rainbowEyes) {
            int offset = left && heterochromia ? RainbowColor.CYCLE_DURATION / 2 : 0;
            color = RainbowColor.sheep(tickCount + tickDelta + offset);
        } else {
            color = staticEyeColor(left);
        }

        return EyeTextureLayers.applyBrightness(color, eyeBrightnessGene);
    }

    public int staticEyeColor(boolean left) {
        int dye = left && heterochromia ? eyeLeftDye : eyeDye;
        return dye != NATURAL_DYE ? dye : geneticEyeColor(left && heterochromia);
    }

    private int geneticEyeColor(boolean shifted) {
        if (albinism) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = Mth.frac(faceGene + (shifted ? 0.43F : 0.0F));

        if (eyeColor < 0.35F) {
            return ARGB.srgbLerp(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return ARGB.srgbLerp((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return ARGB.srgbLerp((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record Dimensions(
            float width,
            float height,
            float breasts,
            float head
    ) implements VillagerDimensions {
        @Override
        public float getWidth() {
            return width;
        }

        @Override
        public float getHeight() {
            return height;
        }

        @Override
        public float getBreasts() {
            return breasts;
        }

        @Override
        public float getHead() {
            return head;
        }
    }
}
