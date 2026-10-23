package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Walks to the job site and works at it for a while, playing the profession's work animation,
 * then rests so the villager still strolls, trades and chats during work hours.
 */
public class WorkAtJobSiteTask extends Behavior<VillagerEntityMCA> {
    private static final int MIN_WORK_TICKS = 300;
    private static final int MAX_WORK_TICKS = 800;
    private static final int MIN_REST_TICKS = 400;
    private static final int MAX_REST_TICKS = 1200;
    private static final int WALK_TIMEOUT = 200;
    private static final double START_DISTANCE = 12.0;
    /** Same reach vanilla villagers work their station from. */
    private static final double WORK_DISTANCE = 1.73;
    private static final float SPEED = 0.5F;

    private long nextStart;
    private long deadline;
    private boolean working;
    private @Nullable BlockPos station;

    public WorkAtJobSiteTask() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_WORK_TICKS + WALK_TIMEOUT);
    }

    /** True while walking to or working at the station, so other work strolls keep out of the way. */
    public boolean isActive() {
        return getStatus() == Status.RUNNING;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (level.getGameTime() < nextStart || !canWork(villager)) {
            return false;
        }
        if (WorkAnimation.forProfession(villager.getProfessionId()) == WorkAnimation.NONE) {
            return false;
        }
        return jobSite(level, villager)
                .filter(pos -> pos.closerToCenterThan(villager.position(), START_DISTANCE))
                .isPresent();
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        station = jobSite(level, villager).orElse(null);
        working = false;
        deadline = time + WALK_TIMEOUT;
        if (station != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station, SPEED, 1));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return station != null
               && time < deadline
               && canWork(villager)
               && jobSite(level, villager).filter(station::equals).isPresent()
               && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (station == null) return;
        Brain<?> brain = villager.getBrain();

        if (!working) {
            if (!station.closerToCenterThan(villager.position(), WORK_DISTANCE)) {
                return;
            }
            working = true;
            deadline = time + MIN_WORK_TICKS + villager.getRandom().nextInt(MAX_WORK_TICKS - MIN_WORK_TICKS);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();

            // the same bookkeeping as vanilla's work at the station
            brain.setMemory(MemoryModuleType.LAST_WORKED_AT_POI, time);
            if (villager.shouldRestock(level)) {
                villager.restock();
            }
        }

        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(station));
        Vec3 center = Vec3.atCenterOf(station);
        villager.getLookControl().setLookAt(center.x, center.y, center.z);
        villager.getVillagerBrain().setWorkAnimation(WorkAnimation.forProfession(villager.getProfessionId()), 10);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getVillagerBrain().stopWorkAnimation();
        if (!working) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextStart = time + MIN_REST_TICKS + villager.getRandom().nextInt(MAX_REST_TICKS - MIN_REST_TICKS);
        working = false;
        station = null;
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    private static boolean canWork(VillagerEntityMCA villager) {
        return !villager.getVillagerBrain().isPanicking()
               && !villager.isSleeping()
               && !villager.isTrading()
               && !villager.isBaby();
    }

    private static Optional<BlockPos> jobSite(ServerLevel level, VillagerEntityMCA villager) {
        return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }
}
