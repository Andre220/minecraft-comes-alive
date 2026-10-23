package net.conczin.mca.expansion.progression;

import net.conczin.mca.Config;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/**
 * Village progression: house tiers by furniture and floor area, workshop levels by stations,
 * the stage path and the record of what was announced.
 */
@PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Boolean previous;

    private ProgressionGameTests() {
    }

    @GameTest(batch = "mca_progression_tiers", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void housesGrowInTiersWithFurnitureAndRoom(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(14112, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center, 24);

        // a big room: four beds, two chests and a furnace make a Big House
        Map<BlockPos, BlockState> furniture = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            bed(furniture, center.offset(-6 + 2 * i, 0, -5));
        }
        furniture.put(center.offset(6, 0, -6), Blocks.CHEST.defaultBlockState());
        furniture.put(center.offset(6, 0, -4), Blocks.CHEST.defaultBlockState());
        furniture.put(center.offset(6, 0, -2), Blocks.FURNACE.defaultBlockState());
        Village village = room(helper, center, 7, furniture, true);
        Building big = village.getBuildingAt(center).orElseThrow(() -> new AssertionError("no building"));
        helper.assertTrue(tier(level, big).equals("big_house"), "four beds, two chests and a furnace in a big room: " + tier(level, big)
                + " area=" + big.getFloorFootprintArea());

        // two more beds, a third chest and decoration make it Huge
        for (int i = 0; i < 2; i++) {
            BlockPos head = center.offset(-6 + 2 * i, 0, 3);
            set(level, head.south(), bedState().setValue(BedBlock.PART, BedPart.FOOT));
            set(level, head, bedState().setValue(BedBlock.PART, BedPart.HEAD));
        }
        set(level, center.offset(6, 0, 0), Blocks.CHEST.defaultBlockState());
        for (int i = 0; i < 6; i++) {
            set(level, center.offset(-6 + 2 * i, 0, 6), Blocks.FLOWER_POT.defaultBlockState());
        }
        helper.assertTrue(tier(level, big).equals("huge_house"), "six beds, three chests and decoration: " + tier(level, big));

        // the same furniture as the Big House in a small room is only a Common House
        BlockPos small = center.offset(18, 0, 0);
        Map<BlockPos, BlockState> crowded = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            bed(crowded, small.offset(-3 + 2 * i, 0, -2));
        }
        bed(crowded, small.offset(-3, 0, 2));
        crowded.put(small.offset(3, 0, -3), Blocks.CHEST.defaultBlockState());
        crowded.put(small.offset(3, 0, -1), Blocks.CHEST.defaultBlockState());
        crowded.put(small.offset(3, 0, 1), Blocks.FURNACE.defaultBlockState());
        room(helper, small, 4, crowded, false);
        Building cramped = village.getBuildingAt(small).orElseThrow(() -> new AssertionError("no small building"));
        helper.assertTrue(tier(level, cramped).equals("common_house"), "a small room can't be a Big House: " + tier(level, cramped)
                + " area=" + cramped.getFloorFootprintArea());
        helper.succeed();
    }

    @GameTest(batch = "mca_progression_workshops", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void workshopLevelFollowsStationsAndArea(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(14368, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center, 12);
        Map<BlockPos, BlockState> furniture = new LinkedHashMap<>();
        furniture.put(center.offset(-2, 0, -3), Blocks.SMOKER.defaultBlockState());
        Village village = room(helper, center, 4, furniture, true);
        Building workshop = village.getBuildingAt(center).orElseThrow();
        helper.assertTrue(BuildingTiers.workshopLevel(level, workshop) == 1, "one smoker is level I");
        set(level, center.offset(0, 0, -3), Blocks.SMOKER.defaultBlockState());
        set(level, center.offset(2, 0, -3), Blocks.SMOKER.defaultBlockState());
        int levelWithThree = BuildingTiers.workshopLevel(level, workshop);
        helper.assertTrue(levelWithThree == 3, "three smokers in a roomy workshop are level III: " + levelWithThree
                + " area=" + workshop.getFloorFootprintArea());
        helper.succeed();
    }

    @GameTest(batch = "mca_progression_stages", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void stagePathShowsWhatTheNextStageNeeds(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(14624, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center, 12);
        Map<BlockPos, BlockState> furniture = new LinkedHashMap<>();
        bed(furniture, center.offset(-2, 0, -2));
        Village village = room(helper, center, 4, furniture, true);
        helper.assertTrue(!VillageStages.stages().isEmpty() && VillageStages.stages().getFirst().id().equals("camp"),
                "the path starts at the camp: " + VillageStages.stages());

        VillageProgress.Report report = VillageProgress.evaluate(level, village);
        helper.assertTrue(report.reached() == 0, "one house is no camp yet");
        VillageProgress.StageStatus camp = report.next();
        helper.assertTrue(camp != null && camp.id().equals("camp"), "the camp is the next goal");
        VillageProgress.Line houses = camp.lines().getFirst();
        helper.assertTrue(houses.have() == 1 && houses.need() == 3, "1 of 3 small houses: " + houses.have() + "/" + houses.need());

        // growth is recorded silently the first time, so villages start where they are
        VillageGrowth.check(level, village);
        ProgressData data = ProgressData.get(level);
        Building house = village.getBuildingAt(center).orElseThrow();
        helper.assertTrue(data.stage(village.getId()) == 0, "stage recorded: " + data.stage(village.getId()));
        helper.assertTrue(data.tier(village.getId(), house.getId()) == 0, "the small house is recorded");
        set(level, center.offset(0, 0, -2).south(), bedState().setValue(BedBlock.PART, BedPart.FOOT));
        set(level, center.offset(0, 0, -2), bedState().setValue(BedBlock.PART, BedPart.HEAD));
        set(level, center.offset(2, 0, -3), Blocks.CHEST.defaultBlockState());
        VillageGrowth.check(level, village);
        helper.assertTrue(data.tier(village.getId(), house.getId()) == 1, "the house grew into a Common House: " + data.tier(village.getId(), house.getId()));
        helper.succeed();
    }

    @AfterBatch(batch = "mca_progression_tiers")
    public static void cleanupTiers(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_progression_workshops")
    public static void cleanupWorkshops(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_progression_stages")
    public static void cleanupStages(ServerLevel level) {
        cleanup(level);
    }

    private static String tier(ServerLevel level, Building building) {
        return BuildingTiers.tierOf(level, building).map(BuildingTiers.Tier::name).orElse("none");
    }

    private static BlockState bedState() {
        return Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
    }

    /** A bed with its head at {@code head}, its foot to the south. */
    private static void bed(Map<BlockPos, BlockState> furniture, BlockPos head) {
        furniture.put(head.south(), bedState().setValue(BedBlock.PART, BedPart.FOOT));
        furniture.put(head, bedState().setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void prepare(GameTestHelper helper, BlockPos center, int radius) {
        ServerLevel level = helper.getLevel();
        if (previous == null) {
            previous = Config.getServerConfig().villageProgression;
        }
        Config.getServerConfig().villageProgression = true;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 6, radius))) {
            BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos min = ChunkPos.containing(center.offset(-radius - 8, 0, -radius - 8));
        ChunkPos max = ChunkPos.containing(center.offset(radius + 8, 0, radius + 8));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForceLoadedChunks().contains(chunk.pack())) {
                    CHUNKS.add(chunk);
                }
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        prepareFlatArea(helper, center, radius, 6);
    }

    /** A stone room {@code 2 * half + 1} blocks wide (door to the south) with furniture, registered as a building. */
    private static Village room(GameTestHelper helper, BlockPos center, int half, Map<BlockPos, BlockState> furniture, boolean newVillage) {
        ServerLevel level = helper.getLevel();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == half || Math.abs(z) == half || y == 3) {
                        set(level, center.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        BlockPos door = center.south(half);
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(level, door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        furniture.forEach((pos, state) -> set(level, pos, state));

        VillageManager manager = VillageManager.get(level);
        if (newVillage) {
            helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(), "fixture would modify an existing village");
        }
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        VILLAGES.add(village.getId());
        village.setAutoScan(false);
        return village;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private static void cleanup(ServerLevel level) {
        VILLAGES.forEach(id -> VillageManager.get(level).removeVillage(id));
        VILLAGES.clear();
        BLOCKS.forEach((pos, state) -> {
            if (level.getBlockEntity(pos) instanceof Container container) {
                container.clearContent();
            }
            level.setBlock(pos, state, 3);
        });
        BLOCKS.clear();
        CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        CHUNKS.clear();
        if (previous != null) {
            Config.getServerConfig().villageProgression = previous;
            previous = null;
        }
    }
}
