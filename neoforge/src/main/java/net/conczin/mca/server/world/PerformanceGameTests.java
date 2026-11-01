package net.conczin.mca.server.world;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.brain.BrainThrottle;
import net.conczin.mca.entity.ai.navigation.AsyncPathfinding;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

/** The block index, the slower brain far from players and background pathfinding. */
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    private PerformanceGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void blockIndexFollowsBlockChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(18000, 2, 400));
        prepareFlatArea(helper, center, 8, 4);
        boolean previous = Config.getInstance().villagerBlockIndex;
        Config.getInstance().villagerBlockIndex = true;
        try {
            // the first search makes the lists for the sections around
            helper.assertTrue(Needs.findDrinkingWater(level, center, 8, 2) == null, "no water yet");
            helper.assertTrue(BlockIndex.findClosest(level, center, 8, 2, BlockIndex.Kind.CRAFTING_TABLE, pos -> true).isEmpty(), "no table yet");

            BlockPos cauldron = center.east(5);
            level.setBlockAndUpdate(cauldron, Blocks.WATER_CAULDRON.defaultBlockState());
            helper.assertTrue(cauldron.equals(Needs.findDrinkingWater(level, center, 8, 2)), "finds the cauldron put down after the lists were made");
            level.setBlockAndUpdate(cauldron, Blocks.CAULDRON.defaultBlockState());
            helper.assertTrue(Needs.findDrinkingWater(level, center, 8, 2) == null, "an empty cauldron has nothing to drink");

            BlockPos table = center.west(3).north(2);
            level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
            helper.assertTrue(BlockIndex.findClosest(level, center, 8, 2, BlockIndex.Kind.CRAFTING_TABLE, pos -> true).filter(table::equals).isPresent(),
                    "finds the crafting table");
            level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
            helper.assertTrue(BlockIndex.findClosest(level, center, 8, 2, BlockIndex.Kind.CRAFTING_TABLE, pos -> true).isEmpty(), "forgets the broken table");

            // a well: a mouth ringed by fences over water, found by its rim
            BlockPos mouth = center.south(4);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                level.setBlockAndUpdate(mouth.relative(side), Blocks.OAK_FENCE.defaultBlockState());
                level.setBlockAndUpdate(mouth.relative(side).relative(side.getClockWise()), Blocks.OAK_FENCE.defaultBlockState());
            }
            level.setBlockAndUpdate(mouth.below(), Blocks.WATER.defaultBlockState());
            helper.assertTrue(mouth.equals(Needs.findDrinkingWater(level, center, 8, 2)), "finds the well's mouth");

            level.setBlockAndUpdate(cauldron, Blocks.WATER_CAULDRON.defaultBlockState());
            BlockPos indexed = Needs.findDrinkingWater(level, center, 8, 2);
            Config.getInstance().villagerBlockIndex = false;
            BlockPos scanned = Needs.findDrinkingWater(level, center, 8, 2);
            helper.assertTrue(mouth.equals(indexed) && Objects.equals(indexed, scanned),
                    "the nearest water is the same with and without the index: " + indexed + " / " + scanned);
            helper.succeed();
        } finally {
            Config.getInstance().villagerBlockIndex = previous;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void brainThinksLessFarFromPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(18020, 2, 400));
        prepareFlatArea(helper, center, 3, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(center))
                .withName("Throttle Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        int previous = Config.getInstance().villagerBrainNearDistance;
        try {
            Config.getInstance().villagerBrainNearDistance = 32;
            int stride = BrainThrottle.stride(level, villager);
            helper.assertTrue(level.players().isEmpty() ? stride == 8 : stride >= 1 && stride <= 8,
                    "far from every player it thinks every 8th tick, got " + stride);
            villager.hurtTime = 10;
            helper.assertTrue(BrainThrottle.stride(level, villager) == 1, "hurt, it thinks every tick");
            villager.hurtTime = 0;
            Config.getInstance().villagerBrainNearDistance = 0;
            helper.assertTrue(BrainThrottle.stride(level, villager) == 1 && BrainThrottle.shouldTick(level, villager), "0 turns it off");
            villager.discard();
            helper.succeed();
        } finally {
            Config.getInstance().villagerBrainNearDistance = previous;
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void backgroundPathReachesTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(18040, 2, 400));
        BlockPos target = start.east(12);
        prepareFlatPath(helper, start, target);
        boolean previous = Config.getInstance().villagerAsyncPathfinding;
        Config.getInstance().villagerAsyncPathfinding = true;
        long searchesBefore = AsyncPathfinding.searches();
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Async Path Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0] < 0) {
                return;
            }
            ticks[0]++;
            // idle wandering may pick its own target: keep ours
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0));
            boolean arrived = villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.25;
            if (arrived || ticks[0] >= 280) {
                ticks[0] = -1;
                Config.getInstance().villagerAsyncPathfinding = previous;
                Vec3 position = villager.position();
                villager.discard();
                if (!arrived) {
                    helper.fail("did not get there, at " + position);
                } else if (AsyncPathfinding.searches() == searchesBefore) {
                    helper.fail("the path was not searched in the background");
                } else {
                    helper.succeed();
                }
            }
        });
    }
}
