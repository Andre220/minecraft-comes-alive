package net.conczin.mca.expansion.wardrobe;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

/**
 * A villager with new clothes waiting walks to the wardrobe or dresser at home and changes there. With none to go
 * to, it changes where it stands once a day has passed.
 */
public class ChangeClothesTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 1200;
    private static final int RETRY_COOLDOWN = 600;

    private @Nullable BlockPos wardrobe;
    private long nextAttempt;

    public ChangeClothesTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getTrackedValue(Wardrobes.OWNED_CLOTHES).isEmpty()) {
            Wardrobes.ownedClothes(villager); // the six outfits it owns, drawn once
        }
        if (!Wardrobes.hasPendingClothes(villager)) {
            return false;
        }
        if (Wardrobes.isNextToWardrobe(level, villager)) {
            Wardrobes.change(villager); // passing by
            return false;
        }
        if (level.getGameTime() < nextAttempt) {
            return false;
        }
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.WORK && activity != Activity.MEET
            || villager.isSleeping() || villager.getVillagerBrain().isPanicking()) {
            return false;
        }
        wardrobe = Wardrobes.findWardrobe(level, villager);
        if (wardrobe == null) {
            if (Wardrobes.waitedTooLong(level, villager)) {
                Wardrobes.change(villager);
            }
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
        return wardrobe != null && Wardrobes.hasPendingClothes(villager)
               && level.getBlockState(wardrobe).is(Wardrobes.WARDROBES)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (wardrobe == null) {
            return;
        }
        if (villager.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(wardrobe)) < Wardrobes.REACH * Wardrobes.REACH) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(wardrobe));
            Wardrobes.change(villager);
        } else if (villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty()) {
            walk(villager);
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (Wardrobes.hasPendingClothes(villager)) {
            nextAttempt = time + RETRY_COOLDOWN;
            if (Wardrobes.waitedTooLong(level, villager)) {
                Wardrobes.change(villager); // couldn't get there
            }
        }
        wardrobe = null;
    }

    private void walk(VillagerEntityMCA villager) {
        if (wardrobe != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(wardrobe, 0.5F, 1));
        }
    }
}
