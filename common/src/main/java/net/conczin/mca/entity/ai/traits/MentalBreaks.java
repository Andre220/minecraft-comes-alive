package net.conczin.mca.entity.ai.traits;

import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Opinions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Mental breaks, from RimWorld: a villager whose mood stays very low for a long while snaps. It throws a tantrum and
 * drops what it carries, sulks and stops working for a while, eats everything it has, or picks a fight with someone
 * it dislikes. Fights are fist fights that stop before anyone gets badly hurt (unless the config says otherwise),
 * and a guard nearby breaks them up. Afterwards the villager feels relieved. Willpower traits move the line.
 * Runs on the server, once a second, with {@code villagerMentalBreaks} on.
 */
public final class MentalBreaks {
    /** Mood at or below which a villager starts to crack (moods run from -15 to 15). */
    static final int BREAK_MOOD = -9;
    /** Seconds of such a mood before a break can happen. */
    static final int PATIENCE = 600;
    /** Chance per second of breaking, once out of patience. */
    private static final float BREAK_CHANCE = 1.0F / 60.0F;
    /** Ticks after a break before the next one can happen. */
    private static final long COOLDOWN = 24000;
    private static final long SULK_TICKS = 2400;
    private static final long FIGHT_TICKS = 600;
    private static final int RELIEF = 6;
    private static final double FIGHT_RANGE = 12.0;
    private static final double REACH = 2.2;
    private static final float PUNCH = 1.5F;
    /** A non-lethal fight stops when the one hit would drop below this share of its health. */
    private static final float STOP_AT_HEALTH = 0.4F;
    private static final int VICTIM_OPINION = -20;
    private static final int WITNESS_OPINION = -8;

    public enum Kind {
        TANTRUM, SULK, BINGE, FIGHT
    }

    private static final class State {
        int lowSeconds;
        long calmUntil;
        @Nullable Kind kind;
        long until;
        @Nullable VillagerEntityMCA target;
    }

    private static final Map<VillagerEntityMCA, State> STATES = new WeakHashMap<>();

    private MentalBreaks() {
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerMentalBreaks;
    }

    /** Whether the villager is sulking after a break: it does not work until it is over. */
    public static boolean isSulking(VillagerEntityMCA villager) {
        State state = STATES.get(villager);
        return state != null && state.kind == Kind.SULK && villager.level().getGameTime() < state.until;
    }

    /** The mood at which this villager starts to crack: willpower lowers it, volatility raises it. */
    static int breakMood(VillagerEntityMCA villager) {
        return BREAK_MOOD - villager.getTraits().getWillpower();
    }

    public static void tick(ServerLevel level, VillagerEntityMCA villager) {
        if (!enabled() || villager.isBaby()) {
            return;
        }
        State state = STATES.computeIfAbsent(villager, v -> new State());
        long now = level.getGameTime();
        if (state.kind == Kind.FIGHT) {
            tickFight(level, villager, state, now);
            return;
        }
        if (state.kind != null && now >= state.until) {
            finish(villager, state, now);
        }
        if (state.kind != null || now < state.calmUntil || villager.isSleeping()) {
            return;
        }
        state.lowSeconds = villager.getVillagerBrain().getMoodValue() <= breakMood(villager) ? state.lowSeconds + 1 : Math.max(0, state.lowSeconds - 2);
        if (state.lowSeconds >= PATIENCE && level.getRandom().nextFloat() < BREAK_CHANCE) {
            start(level, villager, state, now);
        }
    }

    private static void start(ServerLevel level, VillagerEntityMCA villager, State state, long now) {
        VillagerEntityMCA rival = rival(level, villager);
        List<Kind> kinds = new ArrayList<>(List.of(Kind.TANTRUM, Kind.TANTRUM, Kind.SULK, Kind.SULK, Kind.SULK));
        if (hasFood(villager)) {
            kinds.add(Kind.BINGE);
            kinds.add(Kind.BINGE);
        }
        if (rival != null) {
            int weight = isQuarrelsome(villager) ? 6 : 2;
            for (int i = 0; i < weight; i++) {
                kinds.add(Kind.FIGHT);
            }
        }
        Kind kind = kinds.get(level.getRandom().nextInt(kinds.size()));
        state.kind = kind;
        state.until = now + (kind == Kind.SULK ? SULK_TICKS : kind == Kind.FIGHT ? FIGHT_TICKS : 0);
        announce(level, villager, "mental_break." + kind.name().toLowerCase());
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getEyeY() + 0.4, villager.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        switch (kind) {
            case TANTRUM -> {
                if (!villager.getTraits().hasTrait(PersonalityTraits.PYROMANIAC) || !TraitBehaviors.setSafeFire(level, villager)) {
                    tantrum(level, villager);
                }
            }
            case BINGE -> binge(villager);
            case FIGHT -> {
                state.target = rival;
                rival.getOpinions().change(villager, VICTIM_OPINION);
                for (VillagerEntityMCA witness : level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(10.0),
                        v -> v != villager && v != rival)) {
                    witness.getOpinions().change(villager, WITNESS_OPINION);
                }
            }
            case SULK -> villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    private static void finish(VillagerEntityMCA villager, State state, long now) {
        state.kind = null;
        state.target = null;
        state.lowSeconds = 0;
        state.calmUntil = now + COOLDOWN;
        villager.getVillagerBrain().modifyMoodValue(RELIEF);
    }

    /** Throws down up to three things it carries. */
    private static void tantrum(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < 3; i++) {
            int slot = level.getRandom().nextInt(inventory.getContainerSize());
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                villager.spawnAtLocation(level, inventory.removeItem(slot, Math.max(1, stack.getCount() / 2)));
            }
        }
    }

    /** Eats everything edible it carries, at once. */
    private static void binge(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                villager.getNeeds().onAte(food.nutrition() * stack.getCount());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static boolean hasFood(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).has(DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQuarrelsome(VillagerEntityMCA villager) {
        Traits traits = villager.getTraits();
        return traits.hasTrait(PersonalityTraits.HOT_HEADED) || traits.hasTrait(PersonalityTraits.VOLATILE)
               || traits.hasTrait(PersonalityTraits.BRAWLER) || traits.hasTrait(PersonalityTraits.MEAN);
    }

    /** The villager nearby it dislikes most, if it dislikes anyone there. */
    private static @Nullable VillagerEntityMCA rival(ServerLevel level, VillagerEntityMCA villager) {
        Opinions opinions = villager.getOpinions();
        Optional<VillagerEntityMCA> rival = level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(FIGHT_RANGE),
                        v -> v != villager && v.isAlive() && !v.isBaby() && opinions.get(v.getUUID()) <= Opinions.DISLIKED)
                .stream().min(Comparator.comparingInt(v -> opinions.get(v.getUUID())));
        return rival.orElse(null);
    }

    /** Walks up to the rival and punches it, until it is over, someone breaks it up or the rival is hurt enough. */
    private static void tickFight(ServerLevel level, VillagerEntityMCA villager, State state, long now) {
        VillagerEntityMCA target = state.target;
        if (target == null || !target.isAlive() || now >= state.until || target.distanceTo(villager) > FIGHT_RANGE * 2) {
            finish(villager, state, now);
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.8F, 1));
        villager.getLookControl().setLookAt(target);
        if (villager.distanceTo(target) > REACH) {
            return;
        }
        boolean lethal = Config.getServerConfig().villagerMentalBreakLethal;
        if (!lethal && target.getHealth() - PUNCH < target.getMaxHealth() * STOP_AT_HEALTH) {
            finish(villager, state, now);
            return;
        }
        villager.swing(InteractionHand.MAIN_HAND);
        target.hurtServer(level, level.damageSources().mobAttack(villager), PUNCH);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, target.getX(), target.getEyeY() + 0.4, target.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        if (!level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(16.0), VillagerEntityMCA::isGuard).isEmpty()) {
            announce(level, villager, "mental_break.broken_up");
            finish(villager, state, now);
        }
    }

    private static void announce(ServerLevel level, VillagerEntityMCA villager, String key) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) < 24 * 24) {
                player.sendSystemMessage(Component.translatable(key, villager.getName()));
            }
        }
    }
}
