package net.conczin.mca.expansion.minds;

import net.minecraft.world.item.trading.MerchantOffer;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Six attributes per villager, 1 to 10, inspired by Fallout Shelter: strength, perception, vigor, dexterity, wisdom
 * and social. Work earns experience (the same pieces of work that train the profession skill); each level gives a
 * point the village chief spends, or the villager itself once points pile up. Every profession leans on at least two
 * attributes, and how well they fit (the aptitude) speeds up its work and learning. Each attribute also has a small
 * effect of its own, e.g. strength on melee damage and vigor on speed and tiredness, social on hearts and prices.
 */
public final class VillagerAttributes {
    public static final int MIN = 1;
    public static final int MAX = 10;
    /** The value an average villager has; effects are neutral there. */
    public static final int AVERAGE = 5;
    public static final int MAX_LEVEL = 50;
    /** Points a villager lets pile up before spending them on its profession itself. */
    static final int SELF_SPEND_AT = 3;
    /** Weight of the main attribute in the aptitude; the others share the rest. */
    private static final float PRIMARY = 0.6F;
    /** Share of a price each point of social above or below average takes off or adds. */
    private static final float PRICE_PER_SOCIAL = 0.04F;

    /** Attributes, level, experience and unspent points; synced for the interaction screen. */
    private static final CDataParameter<CompoundTag> DATA = CParameter.create("VillagerAttributes", new CompoundTag());

    public enum Stat {
        STRENGTH(Items.IRON_AXE, 0xFFFF5555),
        PERCEPTION(Items.SPYGLASS, 0xFF55DDFF),
        VIGOR(Items.SUGAR, 0xFFFF77EE),
        DEXTERITY(Items.SHEARS, 0xFF66EE66),
        WISDOM(Items.BOOK, 0xFFFFE04A),
        SOCIAL(Items.EMERALD, 0xFFFFA040);

        private final Item icon;
        private final int color;

        Stat(Item icon, int color) {
            this.icon = icon;
            this.color = color;
        }

        /** The attribute's own color (ARGB), for its value and name in the interface. */
        public int color() {
            return color;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public ItemStack icon() {
            return new ItemStack(icon);
        }

        public Component getName() {
            return Component.translatable("attribute.mca." + id()).withColor(color & 0xFFFFFF);
        }

        public Component getDescription() {
            return Component.translatable("attribute.mca." + id() + ".description");
        }

        public static Stat byId(String id) {
            for (Stat stat : values()) {
                if (stat.id().equals(id)) {
                    return stat;
                }
            }
            return null;
        }
    }

    /** The attributes a profession leans on: the main one and the others. */
    public record Fit(Stat primary, List<Stat> others) {
        public static Fit of(Stat primary, Stat... others) {
            return new Fit(primary, List.of(others));
        }
    }

    /** Profession id to its attributes; professions not listed lean on all of them alike. */
    static final Map<String, Fit> FITS = Map.ofEntries(
            Map.entry("mca:guard", Fit.of(Stat.STRENGTH, Stat.VIGOR)),
            Map.entry("mca:archer", Fit.of(Stat.PERCEPTION, Stat.DEXTERITY, Stat.VIGOR)),
            Map.entry("mca:mercenary", Fit.of(Stat.STRENGTH, Stat.PERCEPTION, Stat.VIGOR)),
            Map.entry("mca:hunter", Fit.of(Stat.PERCEPTION, Stat.DEXTERITY, Stat.VIGOR)),
            Map.entry("mca:lumberjack", Fit.of(Stat.VIGOR, Stat.STRENGTH)),
            Map.entry("mca:miner", Fit.of(Stat.STRENGTH, Stat.VIGOR)),
            Map.entry("mca:builder", Fit.of(Stat.STRENGTH, Stat.VIGOR, Stat.WISDOM)),
            Map.entry("mca:hauler", Fit.of(Stat.VIGOR, Stat.STRENGTH)),
            Map.entry("mca:forager", Fit.of(Stat.PERCEPTION, Stat.DEXTERITY)),
            Map.entry("minecraft:fisherman", Fit.of(Stat.PERCEPTION, Stat.DEXTERITY)),
            Map.entry("minecraft:cartographer", Fit.of(Stat.PERCEPTION, Stat.DEXTERITY, Stat.WISDOM)),
            Map.entry("minecraft:leatherworker", Fit.of(Stat.DEXTERITY, Stat.WISDOM)),
            Map.entry("minecraft:armorer", Fit.of(Stat.STRENGTH, Stat.DEXTERITY)),
            Map.entry("minecraft:weaponsmith", Fit.of(Stat.STRENGTH, Stat.DEXTERITY)),
            Map.entry("minecraft:toolsmith", Fit.of(Stat.STRENGTH, Stat.DEXTERITY)),
            Map.entry("minecraft:mason", Fit.of(Stat.STRENGTH, Stat.VIGOR)),
            Map.entry("minecraft:fletcher", Fit.of(Stat.DEXTERITY, Stat.PERCEPTION)),
            Map.entry("minecraft:butcher", Fit.of(Stat.VIGOR, Stat.STRENGTH)),
            Map.entry("minecraft:shepherd", Fit.of(Stat.DEXTERITY, Stat.VIGOR)),
            Map.entry("minecraft:farmer", Fit.of(Stat.VIGOR, Stat.DEXTERITY)),
            Map.entry("minecraft:cleric", Fit.of(Stat.WISDOM, Stat.DEXTERITY, Stat.SOCIAL)),
            Map.entry("minecraft:librarian", Fit.of(Stat.WISDOM, Stat.SOCIAL))
    );

    private VillagerAttributes() {
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(DATA);
    }

    /** Whether attributes are simulated; on clients this follows the server's setting. */
    public static boolean enabled() {
        return Config.getServerConfig().villagerAttributes;
    }

    private static CompoundTag data(VillagerEntityMCA villager) {
        return villager.getTrackedValue(DATA);
    }

    public static boolean isRolled(VillagerEntityMCA villager) {
        return data(villager).contains("level");
    }

    public static int get(VillagerEntityMCA villager, Stat stat) {
        return data(villager).getIntOr(stat.id(), AVERAGE);
    }

    public static int level(VillagerEntityMCA villager) {
        return data(villager).getIntOr("level", 1);
    }

    public static float experience(VillagerEntityMCA villager) {
        return data(villager).getFloatOr("xp", 0.0F);
    }

    public static int points(VillagerEntityMCA villager) {
        return data(villager).getIntOr("points", 0);
    }

    /** Experience from one level to the next. */
    public static float needed(int level) {
        return 8.0F + level * 4.0F;
    }

    private static void write(VillagerEntityMCA villager, CompoundTag tag) {
        villager.setTrackedValue(DATA, tag);
        villager.updateAttributes();
    }

    // ---------------------------------------------------------------- rolling, experience, points

    /** Rolls the attributes once: 3 to 6 each, shaped by intelligence, traits and age. */
    public static void ensure(VillagerEntityMCA villager) {
        if (isRolled(villager) || villager.level().isClientSide()) {
            return;
        }
        RandomSource random = villager.getRandom();
        CompoundTag tag = new CompoundTag();
        for (Stat stat : Stat.values()) {
            tag.putInt(stat.id(), clamp(3 + random.nextInt(4) + bonus(villager, stat)));
        }
        tag.putInt("level", 1);
        tag.putFloat("xp", 0.0F);
        tag.putInt("points", 0);
        write(villager, tag);
    }

    /** What genes, traits and age add to an attribute when it is rolled. */
    static int bonus(VillagerEntityMCA villager, Stat stat) {
        Traits traits = villager.getTraits();
        int bonus = 0;
        switch (stat) {
            case STRENGTH -> bonus += (traits.hasTrait(Traits.TOUGH) ? 2 : 0) - (traits.hasTrait(Traits.WEAK) ? 2 : 0);
            case PERCEPTION -> bonus += traits.hasTrait(PersonalityTraits.ADVENTUROUS) ? 1 : 0;
            case VIGOR -> bonus += (traits.hasTrait(Traits.ATHLETIC) ? 2 : 0) + (traits.hasTrait(PersonalityTraits.ACTIVE) ? 1 : 0)
                                   - (traits.hasTrait(PersonalityTraits.LAZY) ? 1 : 0)
                                   - (villager.getAgeState() == AgeState.ELDER ? 2 : 0);
            case DEXTERITY -> bonus += (traits.hasTrait(PersonalityTraits.PERFECTIONIST) ? 1 : 0) - (traits.hasTrait(PersonalityTraits.CLUMSY) ? 2 : 0);
            case WISDOM -> {
                Genetics genetics = villager.getGenetics();
                bonus += genetics == null ? 0 : Math.round((genetics.getGene(Genetics.INTELLIGENCE) - 0.5F) * 4.0F);
                bonus += (traits.hasTrait(PersonalityTraits.GENIUS) ? 2 : 0) + (villager.getAgeState() == AgeState.ELDER ? 2 : 0);
            }
            case SOCIAL -> bonus += (traits.hasTrait(PersonalityTraits.OUTGOING) ? 2 : 0) + (traits.hasTrait(PersonalityTraits.FRIENDLY) ? 1 : 0)
                                    - (traits.hasTrait(PersonalityTraits.LONER) ? 1 : 0) - (traits.hasTrait(PersonalityTraits.SOCIALLY_AWKWARD) ? 1 : 0);
        }
        return bonus;
    }

    private static int clamp(int value) {
        return Mth.clamp(value, MIN, MAX);
    }

    /** Experience from work; wise villagers learn faster. Each level up gives a point to spend. */
    public static void gainExperience(VillagerEntityMCA villager, float amount) {
        if (!enabled() || villager.isBaby() || !isRolled(villager)) {
            return;
        }
        CompoundTag tag = data(villager).copy();
        int level = tag.getIntOr("level", 1);
        if (level >= MAX_LEVEL) {
            return;
        }
        float xp = tag.getFloatOr("xp", 0.0F) + amount * (0.8F + 0.04F * get(villager, Stat.WISDOM));
        int points = tag.getIntOr("points", 0);
        while (level < MAX_LEVEL && xp >= needed(level)) {
            xp -= needed(level);
            level++;
            points++;
        }
        tag.putInt("level", level);
        tag.putFloat("xp", level >= MAX_LEVEL ? 0.0F : xp);
        tag.putInt("points", points);
        villager.setTrackedValue(DATA, tag);
    }

    /** Spends one point on an attribute; false when there is none or the attribute is at its maximum. */
    public static boolean spend(VillagerEntityMCA villager, Stat stat) {
        CompoundTag tag = data(villager).copy();
        int points = tag.getIntOr("points", 0);
        int value = tag.getIntOr(stat.id(), AVERAGE);
        if (points <= 0 || value >= MAX) {
            return false;
        }
        tag.putInt(stat.id(), value + 1);
        tag.putInt("points", points - 1);
        write(villager, tag);
        return true;
    }

    /** Without a chief spending them, a villager puts piled up points into what its work needs most. */
    public static void selfSpend(VillagerEntityMCA villager) {
        if (!enabled() || points(villager) < SELF_SPEND_AT) {
            return;
        }
        Fit fit = fit(villager.getProfession());
        if (fit == null || !spend(villager, fit.primary())) {
            for (Stat stat : fit == null ? List.of(Stat.values()) : fit.others()) {
                if (spend(villager, stat)) {
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- aptitude

    public static Fit fit(VillagerProfession profession) {
        Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? null : FITS.get(id.toString());
    }

    /** How well the attributes fit a profession, 0.1 to 1. */
    public static float aptitude(VillagerEntityMCA villager, VillagerProfession profession) {
        Fit fit = fit(profession);
        if (fit == null) {
            float sum = 0;
            for (Stat stat : Stat.values()) {
                sum += get(villager, stat);
            }
            return sum / Stat.values().length / MAX;
        }
        float others = 0;
        for (Stat stat : fit.others()) {
            others += get(villager, stat);
        }
        others /= Math.max(1, fit.others().size());
        return (PRIMARY * get(villager, fit.primary()) + (1.0F - PRIMARY) * others) / MAX;
    }

    /** The aptitude as 0 to 5 stars. */
    public static int stars(float aptitude) {
        return Math.round(aptitude * 5.0F);
    }

    // ---------------------------------------------------------------- effects

    /** How far an attribute is from average, -4 to +5; zero when attributes are off. */
    private static int offset(VillagerEntityMCA villager, Stat stat) {
        return enabled() && isRolled(villager) ? get(villager, stat) - AVERAGE : 0;
    }

    /** Factor on the speed of work: 0.84 to 1.2 by aptitude for the current profession. */
    public static float workFactor(VillagerEntityMCA villager) {
        return enabled() && isRolled(villager) ? 0.8F + 0.4F * aptitude(villager, villager.getProfession()) : 1.0F;
    }

    /** Factor on learning the profession: aptitude and wisdom. */
    public static float learnFactor(VillagerEntityMCA villager) {
        if (!enabled() || !isRolled(villager)) {
            return 1.0F;
        }
        return (0.75F + 0.5F * aptitude(villager, villager.getProfession())) * (1.0F + 0.04F * offset(villager, Stat.WISDOM));
    }

    /** Wisdom raises or lowers the highest skill the villager can reach. */
    public static float skillCapBonus(VillagerEntityMCA villager) {
        return 3.0F * offset(villager, Stat.WISDOM);
    }

    /** Dexterity: factor on the chance of making one more. */
    public static float extraFactor(VillagerEntityMCA villager) {
        return 1.0F + 0.06F * offset(villager, Stat.DEXTERITY);
    }

    /** Perception: factor on the chance of rare finds. */
    public static float rareFactor(VillagerEntityMCA villager) {
        return 1.0F + 0.06F * offset(villager, Stat.PERCEPTION);
    }

    /** Strength: factor on melee damage. */
    public static float damageFactor(VillagerEntityMCA villager) {
        return 1.0F + 0.06F * offset(villager, Stat.STRENGTH);
    }

    /** Vigor: factor on walking speed. */
    public static float speedFactor(VillagerEntityMCA villager) {
        return 1.0F + 0.02F * offset(villager, Stat.VIGOR);
    }

    /** Vigor: factor on how fast the villager gets tired. */
    public static float tirednessFactor(VillagerEntityMCA villager) {
        return 1.0F - 0.04F * offset(villager, Stat.VIGOR);
    }

    /**
     * Social: a charming villager trades cheaper, an awkward one dearer; every villager that trades, whatever its
     * profession. Applied when trading starts, reset by vanilla when it stops.
     */
    public static void adjustPrices(VillagerEntityMCA villager) {
        int social = offset(villager, Stat.SOCIAL);
        if (social == 0) {
            return;
        }
        for (MerchantOffer offer : villager.getOffers()) {
            int cost = offer.getBaseCostA().getCount();
            offer.addToSpecialPriceDiff(-Math.round(social * PRICE_PER_SOCIAL * cost));
        }
    }

    /** Social: factor on hearts won with players. */
    public static float heartFactor(VillagerEntityMCA villager) {
        return 1.0F + 0.04F * offset(villager, Stat.SOCIAL);
    }
}
