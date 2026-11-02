package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Creative digging and building, done a slice at a time: {@link Config#workOrderBlocksPerTick} positions each server
 * tick, the orders one after the other, so a large area does not stall the server. Small ones finish in the first tick.
 * Not saved: work left when the server stops is dropped.
 */
public final class CreativeWork {
    private static final Deque<Job> JOBS = new ArrayDeque<>();

    private CreativeWork() {
    }

    private static final class Job {
        final ServerLevel level;
        final WorkOrderType type;
        final @Nullable BlockState block;
        final boolean demolish;
        final @Nullable Map<BlockPos, BlockState> plan;
        final UUID player;
        final Iterator<BlockPos> next;
        int changed;

        Job(ServerLevel level, WorkOrderType type, WorkArea area, @Nullable BlockState block, boolean demolish,
            @Nullable Map<BlockPos, BlockState> plan, UUID player) {
            this.level = level;
            this.type = type;
            this.block = block;
            this.demolish = demolish;
            this.plan = plan;
            this.player = player;
            this.next = plan != null ? plan.keySet().iterator()
                    : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ()).iterator();
        }
    }

    /** Queues the area; work starts this tick. */
    public static void add(ServerLevel level, WorkOrderType type, WorkArea area, @Nullable BlockState block, ServerPlayer player) {
        add(level, type, area, block, false, player);
    }

    /** Queues the area; with {@code demolish} digging takes chests and the like too, contents and all. */
    public static void add(ServerLevel level, WorkOrderType type, WorkArea area, @Nullable BlockState block, boolean demolish, ServerPlayer player) {
        JOBS.add(new Job(level, type, area, block, demolish, null, player.getUUID()));
    }

    /** Queues blocks planned one by one, each with its exact state. */
    public static void addPlan(ServerLevel level, Map<BlockPos, BlockState> plan, ServerPlayer player) {
        JOBS.add(new Job(level, WorkOrderType.BLUEPRINT, WorkArea.around(plan.keySet()), null, false, plan, player.getUUID()));
    }

    /** Whether any creative work is still going on. */
    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    /** Works through as many positions as one tick allows. */
    public static void tick() {
        int budget = Math.max(1, Config.getInstance().workOrderBlocksPerTick);
        // left from a world that was closed (single player keeps this queue between worlds)
        JOBS.removeIf(job -> !job.level.getServer().isRunning());
        while (budget > 0 && !JOBS.isEmpty()) {
            Job job = JOBS.peekFirst();
            while (budget > 0 && job.next.hasNext()) {
                BlockPos pos = job.next.next();
                budget--;
                if (job.plan != null) {
                    BlockState target = job.plan.get(pos);
                    if (target != null && job.level.getBlockState(pos) != target
                        && (!target.isAir() || WorkOrderBlocks.needsDigging(job.level, pos))) {
                        job.level.setBlock(pos, target, Block.UPDATE_CLIENTS);
                        job.changed++;
                    }
                } else if (job.type == WorkOrderType.DIG && WorkOrderBlocks.needsDigging(job.level, pos, job.demolish)) {
                    if (job.demolish) {
                        WorkOrderBlocks.emptyContainer(job.level, pos);
                    }
                    job.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    job.changed++;
                } else if (job.type == WorkOrderType.BUILD && job.block != null && WorkOrderBlocks.needsBuilding(job.level, pos, job.block)) {
                    job.level.setBlock(pos, job.block, Block.UPDATE_ALL);
                    job.changed++;
                }
            }
            if (!job.next.hasNext()) {
                JOBS.pollFirst();
                finished(job);
            }
        }
    }

    private static void finished(Job job) {
        if (!(job.level.getPlayerByUUID(job.player) instanceof ServerPlayer player)) {
            return;
        }
        if (job.changed == 0) {
            player.sendSystemMessage(Component.translatable("workorder.error.nothing_to_do"));
            return;
        }
        player.sendSystemMessage(Component.translatable(job.type == WorkOrderType.DIG ? "workorder.done_now.dig" : "workorder.done_now.build",
                job.changed).withStyle(ChatFormatting.GREEN));
    }

    /** Whether an area takes more than a tick, to tell the player it will take a moment. */
    static boolean isLarge(WorkArea area) {
        return area.volume() > Config.getInstance().workOrderBlocksPerTick;
    }
}
