package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paths worked out on background threads ({@link Config#villagerAsyncPathfinding}). Before a villager starts walking,
 * {@link MCAGroundPathNavigation#prepareAsyncPath} sends the search off and the villager waits a tick or two; when
 * the search is done the usual path request finds the path ready instead of searching. The search reads the
 * chunks as they are while it runs, with its own node evaluator, so it shares nothing with the server thread but the
 * blocks; a path found from blocks that changed meanwhile is fixed the usual way, when the villager gets stuck.
 */
public final class AsyncPathfinding {
    /** Ticks a villager waits for a background search before searching on the spot. */
    static final long MAX_WAIT = 20;
    /** Ticks a found path is kept for its villager. */
    static final long KEEP = 40;
    /** How far (squared) the villager may have moved since the search started for its path to still be good. */
    static final double MOVED_SQR = 2.25;

    private static @Nullable ExecutorService executor;
    private static final AtomicLong SEARCHES = new AtomicLong();

    private AsyncPathfinding() {
    }

    public static boolean isEnabled() {
        return Config.getInstance().villagerAsyncPathfinding;
    }

    /** What a search was for: a path found for one request answers only the same request. */
    record Request(Set<BlockPos> targets, float maxPathLength, int reachRange, float visitedNodesMultiplier) {
    }

    static final class Job {
        /** The walk target it was searched for. */
        final BlockPos target;
        final Request request;
        final BlockPos from;
        final long submittedAt;
        final CompletableFuture<Path> result;

        private Job(BlockPos target, Request request, BlockPos from, long submittedAt, CompletableFuture<Path> result) {
            this.target = target;
            this.request = request;
            this.from = from;
            this.submittedAt = submittedAt;
            this.result = result;
        }

        boolean isDone() {
            return result.isDone() && !result.isCancelled();
        }

        /** The path found (null for none); only when done. */
        @Nullable Path path() {
            return result.getNow(null);
        }

        void cancel() {
            result.cancel(false);
        }
    }

    /** Background searches started so far (for tests). */
    public static long searches() {
        return SEARCHES.get();
    }

    static Job submit(BlockPos target, Request request, PathNavigationRegion region, Mob mob, PathFinder finder, long now) {
        SEARCHES.incrementAndGet();
        CompletableFuture<Path> result = CompletableFuture.supplyAsync(
                () -> finder.findPath(region, mob, request.targets(), request.maxPathLength(), request.reachRange(), request.visitedNodesMultiplier()),
                executor()
        ).exceptionally(error -> {
            MCA.LOGGER.debug("Background path search failed", error);
            return null;
        });
        return new Job(target.immutable(), request, mob.blockPosition(), now, result);
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            int threads = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 2);
            AtomicInteger count = new AtomicInteger();
            executor = Executors.newFixedThreadPool(threads, task -> {
                Thread thread = new Thread(task, "MCA Pathfinding " + count.incrementAndGet());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
        }
        return executor;
    }

    /** Stops the threads, as the server stops; they start again with the next search. */
    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
