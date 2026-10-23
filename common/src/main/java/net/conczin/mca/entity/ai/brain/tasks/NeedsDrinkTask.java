package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * A thirsty villager walks to the nearest cauldron with water and stays next to it until its
 * thirst is quenched (Needs refills thirst while it is within reach of one).
 */
public class NeedsDrinkTask extends Behavior<VillagerEntityMCA> {
    private static final float THIRSTY = 30.0F;
    private static final float QUENCHED = 90.0F;
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_HEIGHT = 4;
    private static final int MAX_DURATION = 1200;
    private static final int RETRY_COOLDOWN = 1200;

    private @Nullable BlockPos cauldron;
    private long nextAttempt;

    public NeedsDrinkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Needs needs = villager.getNeeds();
        boolean wanted = ActionQueue.isEnabled() ? villager.getActionQueue().isCurrent(ActionType.DRINK) : needs.get(Needs.Need.THIRST) < THIRSTY;
        if (level.getGameTime() < nextAttempt || !needs.hasNeeds() || !wanted) {
            return false;
        }
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.WORK && activity != Activity.MEET
            || villager.isSleeping() || villager.getVillagerBrain().isPanicking()) {
            return false;
        }
        cauldron = BlockPos.findClosestMatch(villager.blockPosition(), SEARCH_RADIUS, SEARCH_HEIGHT,
                pos -> level.getBlockState(pos).is(Blocks.WATER_CAULDRON)).map(BlockPos::immutable).orElse(null);
        if (cauldron == null) {
            nextAttempt = level.getGameTime() + RETRY_COOLDOWN;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        walk(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return cauldron != null
               && villager.getNeeds().get(Needs.Need.THIRST) < QUENCHED
               && (!ActionQueue.isEnabled() || villager.getActionQueue().isCurrent(ActionType.DRINK))
               && level.getBlockState(cauldron).is(Blocks.WATER_CAULDRON)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (cauldron == null) {
            return;
        }
        if (villager.getNeeds().isNextToWater(level)) {
            // Drinking: stay put and look at the water.
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(cauldron));
        } else if (villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty()) {
            walk(villager);
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextAttempt = time + RETRY_COOLDOWN / 4;
        cauldron = null;
    }

    private void walk(VillagerEntityMCA villager) {
        if (cauldron != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(cauldron, 0.5F, 1));
        }
    }
}
