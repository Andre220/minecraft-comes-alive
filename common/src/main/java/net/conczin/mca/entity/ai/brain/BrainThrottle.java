package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * How often a villager's brain runs, by how far the nearest player is ({@link Config#villagerBrainNearDistance}): every
 * tick near one, every 2nd, 4th or 8th further away. Each villager thinks on its own tick of the stride (by its id), so
 * a village far away spreads its thinking over the ticks instead of all thinking on one. Only the brain is slowed:
 * walking along a path, looking around and the needs go on every tick.
 */
public final class BrainThrottle {
    private static final int MAX_STRIDE = 8;
    /** The GameTest server has no players, and its tests expect villagers to answer within a few ticks. */
    private static final boolean GAMETEST = Boolean.getBoolean("mca.gametest.enabled");

    private BrainThrottle() {
    }

    /** Whether the villager's brain runs this tick. */
    public static boolean shouldTick(ServerLevel level, VillagerEntityMCA villager) {
        if (GAMETEST) {
            return true;
        }
        int stride = stride(level, villager);
        return stride <= 1 || (level.getGameTime() + villager.getId()) % stride == 0;
    }

    /** Every how many ticks the villager's brain runs: 1, 2, 4 or 8. */
    public static int stride(ServerLevel level, VillagerEntityMCA villager) {
        int near = Config.getInstance().villagerBrainNearDistance;
        if (near <= 0 || needsFullAttention(villager)) {
            return 1;
        }
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                nearest = Math.min(nearest, player.distanceToSqr(villager));
            }
        }
        int stride = 1;
        double distance = near;
        while (stride < MAX_STRIDE && nearest > distance * distance) {
            stride *= 2;
            distance *= 2;
        }
        return stride;
    }

    /** Fighting, fleeing, hurt or being led: the brain has to answer at once. */
    private static boolean needsFullAttention(VillagerEntityMCA villager) {
        return villager.hurtTime > 0
               || villager.isPassenger()
               || villager.isLeashed()
               || villager.getVillagerBrain().isPanicking()
               || villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
               || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }
}
