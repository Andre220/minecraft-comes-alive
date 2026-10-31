package net.conczin.mca.expansion.minds;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Profession skill, from 0 to 100 per profession: villagers get better by doing real work.
 * <p>
 * How far and how fast they get follows their intelligence gene. A skilled villager works faster,
 * now and then makes one more and finds rare things more often. Skill earned in a profession is
 * kept when switching jobs.
 */
public final class Skills {
    public static final float MAX = 100.0F;

    /** Skill per profession id, saved with the villager and synced to clients for the UI. */
    private static final CDataParameter<CompoundTag> SKILLS = CParameter.create("ProfessionSkills", new CompoundTag());

    /** Typical gains for one piece of work. */
    public static final float GAIN_BATCH = 1.0F;
    public static final float GAIN_GATHER = 0.6F;
    public static final float GAIN_DELIVERY = 0.4F;
    public static final float GAIN_BLOCK = 0.05F;
    /** Elders learn more slowly; wisdom makes up for it. */
    private static final float ELDER_LEARNING = 0.8F;

    private Skills() {
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(SKILLS);
    }

    /** Whether skills are simulated; on clients this follows the server's setting. */
    public static boolean enabled() {
        return Config.getServerConfig().villagerSkills;
    }

    private static String key(VillagerProfession profession) {
        Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? "none" : id.toString();
    }

    /** Skill in the current profession. */
    public static float get(VillagerEntityMCA villager) {
        return get(villager, villager.getProfession());
    }

    public static float get(VillagerEntityMCA villager, VillagerProfession profession) {
        return villager.getTrackedValue(SKILLS).getFloatOr(key(profession), 0.0F);
    }

    public static void set(VillagerEntityMCA villager, VillagerProfession profession, float skill) {
        CompoundTag skills = villager.getTrackedValue(SKILLS).copy();
        skills.putFloat(key(profession), Math.clamp(skill, 0.0F, MAX));
        villager.setTrackedValue(SKILLS, skills);
    }

    /** Every profession the villager has some skill in, best first. */
    public static List<VillagerProfession> known(VillagerEntityMCA villager) {
        CompoundTag skills = villager.getTrackedValue(SKILLS);
        return skills.keySet().stream()
                .sorted((a, b) -> Float.compare(skills.getFloatOr(b, 0), skills.getFloatOr(a, 0)))
                .map(id -> BuiltInRegistries.VILLAGER_PROFESSION.getValue(Identifier.parse(id)))
                .filter(profession -> profession != null)
                .toList();
    }

    /** The intelligence gene, 0 to 1. */
    public static float intelligence(VillagerEntityMCA villager) {
        return villager.getGenetics() == null ? 0.5F : villager.getGenetics().getGene(Genetics.INTELLIGENCE);
    }

    /** The highest skill this villager can reach: 40 for the dullest, 100 for the brightest. */
    public static float cap(VillagerEntityMCA villager) {
        return Math.clamp(40.0F + 60.0F * intelligence(villager) + VillagerAttributes.skillCapBonus(villager), 10.0F, MAX);
    }

    /**
     * Learns from a piece of work in the current profession. Gains shrink near the villager's cap,
     * so getting good is quick and mastering it slow.
     */
    public static void gain(VillagerEntityMCA villager, float amount) {
        VillagerAttributes.gainExperience(villager, amount);
        if (!enabled() || villager.isBaby()) {
            return;
        }
        float skill = get(villager);
        float cap = cap(villager);
        if (skill >= cap) {
            return;
        }
        float rate = (0.5F + intelligence(villager)) * villager.getTraits().getSkillGainMultiplier()
                     * (villager.getAgeState() == AgeState.ELDER ? ELDER_LEARNING : 1.0F) * VillagerAttributes.learnFactor(villager);
        set(villager, villager.getProfession(), Math.min(cap, skill + amount * rate * (1.0F - skill / cap)));
    }

    /** The profession line of the interaction screen, with the skill when skills are on. */
    public static Component describe(VillagerEntityMCA villager, Component profession) {
        if (!enabled()) {
            return profession;
        }
        return profession.copy().append(Component.translatable("skills.level", Math.round(get(villager)), Math.round(cap(villager)))
                .withStyle(ChatFormatting.GRAY));
    }

    // ---------------------------------------------------------------- effects

    /** How much faster the villager works: up to twice as fast at 100. */
    public static float speed(VillagerEntityMCA villager) {
        return (enabled() ? 1.0F + get(villager) / MAX : 1.0F) * VillagerAttributes.workFactor(villager)
               * villager.getTraits().getWorkSpeedMultiplier();
    }

    /** Working time for a task that takes {@code ticks} for a novice. */
    public static int ticks(VillagerEntityMCA villager, int ticks) {
        return Math.max(1, Math.round(ticks / speed(villager)));
    }

    /** Chance to make one more of something: up to 50% at 100. */
    public static boolean rollExtra(VillagerEntityMCA villager, RandomSource random) {
        return enabled() && random.nextFloat() < get(villager) / MAX * 0.5F * VillagerAttributes.extraFactor(villager);
    }

    /** Factor on the chance of rare finds: up to three times at 100. */
    public static double rareFactor(VillagerEntityMCA villager) {
        return (enabled() ? 1.0 + 2.0 * get(villager) / MAX : 1.0) * VillagerAttributes.rareFactor(villager);
    }

    /** One more of the first item now and then, for a skilled hand. */
    public static void addExtra(VillagerEntityMCA villager, List<ItemStack> produced) {
        if (!produced.isEmpty() && !produced.getFirst().isEmpty() && produced.getFirst().isStackable()
            && rollExtra(villager, villager.getRandom())) {
            produced.add(produced.getFirst().copyWithCount(1));
        }
    }
}
