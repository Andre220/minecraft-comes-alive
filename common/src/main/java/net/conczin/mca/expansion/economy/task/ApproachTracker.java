package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Walks a villager towards a spot and notices when it stops getting closer.
 */
final class ApproachTracker {
    enum Approach {WALKING, ARRIVED, STUCK}

    /** Ticks without getting closer before a target counts as unreachable. */
    private static final int STUCK_TICKS = 160;
    private static final float SPEED = 0.5F;

    private int stuckTicks;
    private double bestDistance = Double.MAX_VALUE;

    Approach approach(VillagerEntityMCA villager, BlockPos pos, double reachSqr) {
        double distance = villager.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
        if (distance <= reachSqr) {
            reset();
            villager.lookAt(pos);
            return Approach.ARRIVED;
        }
        if (distance < bestDistance - 0.25) {
            bestDistance = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > STUCK_TICKS) {
            reset();
            return Approach.STUCK;
        }
        villager.moveTowardsPersistent(pos, SPEED, 1);
        return Approach.WALKING;
    }

    void reset() {
        stuckTicks = 0;
        bestDistance = Double.MAX_VALUE;
    }
}
