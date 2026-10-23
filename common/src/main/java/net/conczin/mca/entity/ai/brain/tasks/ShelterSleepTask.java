package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A homeless villager sheltering indoors (see SeekIndoorShelterTask) sleeps in a free bed of the
 * shelter at rest time, without claiming it as its home: the bed stays free for anyone moving in.
 * WakeUp gets it up when rest time is over.
 */
public class ShelterSleepTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 8;
    private static final int RETRY_TICKS = 200;

    private @Nullable BlockPos bed;
    private long nextAttempt;

    public ShelterSleepTask() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT,
                MemoryModuleTypeMCA.SHELTER_BED, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.isSleeping() || !villager.getBrain().isActive(Activity.REST) || level.getGameTime() < nextAttempt) {
            return false;
        }
        nextAttempt = level.getGameTime() + RETRY_TICKS;
        bed = level.getPoiManager().findAllClosestFirstWithType(poi -> poi.is(PoiTypes.HOME), pos -> isFree(level, pos),
                        villager.blockPosition(), SEARCH_RADIUS, PoiManager.Occupancy.HAS_SPACE)
                .map(pair -> pair.getSecond().immutable()).findFirst().orElse(null);
        return bed != null;
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return bed != null && !villager.isSleeping() && villager.getBrain().isActive(Activity.REST) && isFree(level, bed);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (bed == null) {
            return;
        }
        if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(bed)) <= 2.0 * 2.0) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.startSleeping(bed);
        } else {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bed, 0.5F, 1));
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (!villager.isSleeping()) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        bed = null;
    }
}
