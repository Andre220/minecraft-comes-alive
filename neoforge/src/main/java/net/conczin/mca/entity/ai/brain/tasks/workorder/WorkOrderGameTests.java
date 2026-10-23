package net.conczin.mca.entity.ai.brain.tasks.workorder;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.server.world.data.workorder.VillageWorkOrders;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrder;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/**
 * Builders and miners working through real work orders next to a registered storage room.
 */
@PrefixGameTestTemplate(false)
public final class WorkOrderGameTests {
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-00000000b11d");
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Boolean previousTeleport;
    private static Boolean previousEnabled;

    private WorkOrderGameTests() {
    }

    @GameTest(batch = "mca_work_order_build", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 2400)
    public static void builderFillsAreaWithBlocksFromStorage(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4112, 2, 400));
        Village village = prepareVillage(helper, center);
        Container chest = chest(helper, center.offset(-3, 0, -3));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        WorkArea area = WorkArea.of(center.offset(-1, 0, 7), center.offset(1, 0, 9));
        WorkOrder order = createOrder(helper, village, WorkOrderType.BUILD, area, Blocks.COBBLESTONE.defaultBlockState());
        helper.assertTrue(order.getInitialWork() == 9, "expected 9 blocks to place, got " + order.getInitialWork());

        spawnWorker(helper, center.offset(0, 0, 12), ProfessionsMCA.BUILDER, List.of(WorkOrderType.BUILD, WorkOrderType.DIG));

        helper.succeedWhen(() -> {
            for (int y : area.layerOrder(false)) {
                for (BlockPos pos : area.layer(y)) {
                    helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.COBBLESTONE), "not built yet: " + pos);
                }
            }
            helper.assertTrue(village.getWorkOrders().get(order.getId()).isEmpty(), "order was not completed");
            int left = VillageStorage.count(chest, Items.COBBLESTONE);
            helper.assertTrue(left == 55, "builder should take exactly 9 cobblestone from storage, storage holds " + left);
        });
    }

    @GameTest(batch = "mca_work_order_missing_material", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 400)
    public static void builderWaitsWithoutMaterial(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4368, 2, 400));
        Village village = prepareVillage(helper, center);

        WorkArea area = WorkArea.of(center.offset(-1, 0, 7), center.offset(1, 0, 7));
        WorkOrder order = createOrder(helper, village, WorkOrderType.BUILD, area, Blocks.OAK_PLANKS.defaultBlockState());
        spawnWorker(helper, center.offset(0, 0, 10), ProfessionsMCA.BUILDER, List.of(WorkOrderType.BUILD, WorkOrderType.DIG));

        helper.runAfterDelay(300, () -> {
            for (BlockPos pos : area.layer(area.minY())) {
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "built without material: " + pos);
            }
            helper.assertTrue(village.getWorkOrders().get(order.getId()).map(WorkOrder::isWaitingForMaterial).orElse(false),
                    "order should wait for material");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_work_order_dig", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 3600)
    public static void minerDigsPitAndStoresTheHaul(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4624, 2, 400));
        Village village = prepareVillage(helper, center);
        Container chest = chest(helper, center.offset(-3, 0, -3));

        // a 3x3 pit, two blocks deep, in front of the storage door
        WorkArea area = WorkArea.of(center.offset(-1, -2, 7), center.offset(1, -1, 9));
        ServerLevel level = helper.getLevel();
        for (int x = area.minX() - 1; x <= area.maxX() + 1; x++) {
            for (int z = area.minZ() - 1; z <= area.maxZ() + 1; z++) {
                setBlock(level, new BlockPos(x, area.minY() - 1, z), Blocks.STONE.defaultBlockState());
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    setBlock(level, pos, area.contains(pos) ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
        WorkOrder order = createOrder(helper, village, WorkOrderType.DIG, area, null);
        helper.assertTrue(order.getInitialWork() == 18, "expected 18 blocks to dig, got " + order.getInitialWork());

        VillagerEntityMCA miner = spawnWorker(helper, center.offset(0, 0, 12), ProfessionsMCA.MINER, List.of(WorkOrderType.DIG));

        helper.succeedWhen(() -> {
            for (int y : area.layerOrder(true)) {
                for (BlockPos pos : area.layer(y)) {
                    helper.assertTrue(level.getBlockState(pos).isAir(), "not dug yet: " + pos);
                }
            }
            helper.assertTrue(village.getWorkOrders().get(order.getId()).isEmpty(), "order was not completed");
            int stored = VillageStorage.count(level, village, Items.DIRT);
            helper.assertTrue(stored == 18, "the miner should store all 18 dirt, storage holds " + stored);
            helper.assertTrue(miner.getY() >= center.getY() - 0.5, "the miner is still stuck in the pit at " + miner.blockPosition());
        });
    }

    @GameTest(batch = "mca_work_order_persistence", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void workOrdersSurviveVillageReload(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(4880, 2, 400));
        Village village = prepareVillage(helper, center);
        WorkArea area = WorkArea.of(center.offset(-1, 0, 7), center.offset(1, 1, 8));
        WorkOrder order = createOrder(helper, village, WorkOrderType.BUILD, area, Blocks.STONE_BRICKS.defaultBlockState());
        village.getWorkOrders().setPaused(order.getId(), true);

        Village reloaded = new Village(village.save(), helper.getLevel());
        WorkOrder copy = reloaded.getWorkOrders().get(order.getId()).orElse(null);
        helper.assertTrue(copy != null, "work order was not saved");
        helper.assertTrue(copy.getArea().equals(area), "area changed: " + copy.getArea());
        helper.assertTrue(copy.getType() == WorkOrderType.BUILD, "type changed");
        helper.assertTrue(copy.getBlock() != null && copy.getBlock().is(Blocks.STONE_BRICKS), "block changed");
        helper.assertTrue(copy.isPaused(), "pause was lost");
        helper.assertTrue(copy.getInitialWork() == 12, "initial work changed: " + copy.getInitialWork());
        WorkOrder next = createOrder(helper, reloaded, WorkOrderType.DIG, WorkArea.of(center.offset(-1, -1, 7), center.offset(1, -1, 7)), null);
        helper.assertTrue(next.getId() > order.getId(), "ids are reused after reload");
        helper.succeed();
    }

    @GameTest(batch = "mca_work_order_blueprint", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void capturedBlueprintCanBePlacedTurned(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(5136, 2, 400));
        Village village = prepareVillage(helper, center);
        ServerLevel level = helper.getLevel();

        // a 3 wide, 3 high, 2 deep hut south of the storage: planks, a door and a torch
        BlockPos hut = center.offset(-1, 0, 8);
        WorkArea hutArea = WorkArea.of(hut, hut.offset(2, 2, 1));
        for (BlockPos pos : BlockPos.betweenClosed(hut, hut.offset(2, 2, 1))) {
            setBlock(level, pos.immutable(), Blocks.OAK_PLANKS.defaultBlockState());
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        setBlock(level, hut.offset(1, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        setBlock(level, hut.offset(1, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        setBlock(level, hut.offset(1, 2, 1), Blocks.AIR.defaultBlockState());

        BlueprintLibrary.CaptureResult captured = BlueprintLibrary.capture(level, hutArea, "MCA GameTest Hut", "tester");
        helper.assertTrue(captured.blueprint() != null, "capture failed: " + (captured.error() == null ? "?" : captured.error().getString()));
        Blueprint blueprint = captured.blueprint();
        helper.assertTrue(blueprint.id().equals(BlueprintLibrary.idFor("mca_gametest_hut")), "unexpected id " + blueprint.id());
        helper.assertTrue(BlueprintLibrary.list(level.getServer()).contains(blueprint.id()), "blueprint is not listed");
        helper.assertTrue(blueprint.blocks().size() == 18, "a 3x3x2 box has 18 spots, got " + blueprint.blocks().size());
        helper.assertTrue(blueprint.solidBlocks() == 17, "17 spots are not air, got " + blueprint.solidBlocks());

        Blueprint.Materials materials = blueprint.materials();
        helper.assertTrue(materials.items().getOrDefault(Items.OAK_DOOR, 0) == 1, "a door is one item: " + materials.items());
        helper.assertTrue(materials.items().getOrDefault(Items.OAK_PLANKS, 0) == 15, "15 planks: " + materials.items());

        // place it turned once on the open ground next to the hut
        BlockPos origin = center.offset(5, 0, 8);
        VillageWorkOrders.CreateResult created = village.getWorkOrders().createBlueprint(level, blueprint, origin, 1, AUTHOR, "Planner", false);
        WorkOrder order = created.order();
        helper.assertTrue(order != null, "placing failed: " + (created.error() == null ? "?" : created.error().getString()));
        helper.assertTrue(order.getArea().sizeX() == 2 && order.getArea().sizeZ() == 3,
                "a quarter turn swaps width and depth: " + order.getArea());
        helper.assertTrue(order.getInitialWork() == 17, "every solid block is still missing, got " + order.getInitialWork());
        BlockState plannedDoor = order.getPlan().values().stream().filter(state -> state.is(Blocks.OAK_DOOR)).findFirst().orElseThrow();
        helper.assertTrue(plannedDoor.getValue(DoorBlock.FACING) == Direction.EAST, "the door must turn with the blueprint: " + plannedDoor);

        // building part of it by hand counts as progress, also after the village is reloaded
        order.getPlan().forEach((pos, state) -> {
            if (state.is(Blocks.OAK_PLANKS) && pos.getY() == origin.getY()) {
                setBlock(level, pos, state);
            }
        });
        Village reloaded = new Village(village.save(), level);
        WorkOrder copy = reloaded.getWorkOrders().get(order.getId()).orElseThrow();
        helper.assertTrue(copy.getBlueprint() != null && copy.getBlueprint().equals(blueprint.id()) && copy.getRotation() == 1,
                "blueprint and rotation must be saved");
        reloaded.getWorkOrders().tick(level, 0);
        helper.assertTrue(copy.getPlan() != null, "the plan must be rebuilt after loading");
        helper.assertTrue(copy.getRemainingWork() < 17, "blocks built by hand must count, still " + copy.getRemainingWork());
        level.getServer().getStructureManager().remove(blueprint.id());
        helper.succeed();
    }

    @GameTest(batch = "mca_work_order_raise_blueprint", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 24000)
    public static void builderRaisesBundledCottageAndItBecomesAHouse(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(5392, 2, 400));
        Village village = prepareVillage(helper, center);
        ServerLevel level = helper.getLevel();

        Blueprint cottage = BlueprintLibrary.get(level.getServer(), BlueprintLibrary.idFor("small_house")).orElse(null);
        helper.assertTrue(cottage != null, "the bundled cottage blueprint is missing");

        // everything it takes goes into the storage, one chest after the other
        List<BlockPos> chests = VillageStorage.containers(level, village, center);
        int chest = 0;
        for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : cottage.materials().items().entrySet()) {
            int left = entry.getValue();
            while (left > 0) {
                ItemStack stack = new ItemStack(entry.getKey(), Math.min(left, entry.getKey().getDefaultMaxStackSize()));
                ItemStack rest = VillageStorage.insert(chest(helper, chests.get(chest)), stack);
                left -= stack.getCount() - rest.getCount();
                if (!rest.isEmpty()) {
                    chest++;
                }
            }
        }

        // dirt in the way inside the cottage has to be dug out first
        BlockPos origin = center.offset(-3, 0, 8);
        setBlock(level, origin.offset(2, 1, 2), Blocks.DIRT.defaultBlockState());
        setBlock(level, origin.offset(3, 1, 3), Blocks.DIRT.defaultBlockState());

        VillageWorkOrders.CreateResult created = village.getWorkOrders().createBlueprint(level, cottage, origin, 0, AUTHOR, "Planner", false);
        WorkOrder order = created.order();
        helper.assertTrue(order != null, "placing failed: " + (created.error() == null ? "?" : created.error().getString()));
        Map<BlockPos, BlockState> plan = Map.copyOf(order.getPlan());

        spawnWorker(helper, center.offset(6, 0, 12), ProfessionsMCA.BUILDER,
                List.of(WorkOrderType.BLUEPRINT, WorkOrderType.BUILD, WorkOrderType.DIG));

        helper.succeedWhen(() -> {
            helper.assertTrue(village.getWorkOrders().get(order.getId()).isEmpty(),
                    "the cottage is not finished yet, " + order.getRemainingWork() + " blocks left");
            plan.forEach((pos, planned) -> helper.assertTrue(!WorkOrderBlocks.needsBlueprint(level, pos, planned, false),
                    "not as planned at " + pos + ": " + level.getBlockState(pos) + " instead of " + planned));
            BlockState doorTop = level.getBlockState(origin.offset(3, 2, 0));
            helper.assertTrue(doorTop.is(Blocks.OAK_DOOR), "the door was placed without its top half");
            helper.assertTrue(VillageStorage.count(level, village, Items.DIRT) == 2, "the dug out dirt must end up in the storage");
            BlockPos inside = origin.offset(3, 1, 3);
            helper.assertTrue(village.getBuildingAt(inside).map(building -> building.getBuildingType().name().equals("house")).orElse(false),
                    "the finished cottage should count as a house, found " + village.getBuildingAt(inside).map(b -> b.getBuildingType().name()));
        });
    }

    @AfterBatch(batch = "mca_work_order_raise_blueprint")
    public static void cleanupRaiseBlueprint(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_work_order_blueprint")
    public static void cleanupBlueprint(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_work_order_build")
    public static void cleanupBuild(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_work_order_missing_material")
    public static void cleanupMissingMaterial(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_work_order_dig")
    public static void cleanupDig(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_work_order_persistence")
    public static void cleanupPersistence(ServerLevel level) {
        cleanup(level);
    }

    private static WorkOrder createOrder(GameTestHelper helper, Village village, WorkOrderType type, WorkArea area, BlockState block) {
        VillageWorkOrders.CreateResult result = village.getWorkOrders().create(helper.getLevel(), type, area, block, AUTHOR, "Planner", false);
        if (result.order() == null) {
            throw new AssertionError("could not create work order: " + (result.error() == null ? "?" : result.error().getString()));
        }
        return result.order();
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        return VillageStorage.getContainer(helper.getLevel(), pos)
                .orElseThrow(() -> new AssertionError("no chest at " + pos));
    }

    /** A flat area with a registered storage room (lectern and four chests) and its door facing south. */
    private static Village prepareVillage(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        if (previousTeleport == null) {
            previousTeleport = Config.getInstance().allowVillagerTeleporting;
        }
        Config.getInstance().allowVillagerTeleporting = false;
        if (previousEnabled == null) {
            previousEnabled = Config.getServerConfig().villagerWorkOrders;
        }
        Config.getServerConfig().villagerWorkOrders = true;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 5, 16))) {
            BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos min = ChunkPos.containing(center.offset(-24, 0, -24));
        ChunkPos max = ChunkPos.containing(center.offset(24, 0, 24));
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
        prepareFlatArea(helper, center, 16, 5);

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4 || y == 3) {
                        level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockPos door = center.south(4);
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlock(center.offset(-2, 0, -2), Blocks.LECTERN.defaultBlockState(), 3);
        for (BlockPos chest : List.of(center.offset(-3, 0, -3), center.offset(3, 0, -3), center.offset(-3, 0, 1), center.offset(3, 0, 1))) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        }

        VillageManager manager = VillageManager.get(level);
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(),
                "fixture would modify an existing village");
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "storage room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        VILLAGES.add(village.getId());
        village.setAutoScan(false);
        if (!VillageStorage.exists(village)) {
            manager.forceRoomType(center, VillageStorage.STORAGE_BUILDING);
        }
        helper.assertTrue(VillageStorage.exists(village), "fixture room is not a storage");
        helper.assertTrue(VillageStorage.containers(level, village, center).size() == 4,
                "storage should expose 4 chests, got " + VillageStorage.containers(level, village, center));
        return village;
    }

    /** A worker whose brain only walks, opens doors and works on orders, so schedules can't interfere. */
    private static VillagerEntityMCA spawnWorker(GameTestHelper helper, BlockPos pos, net.minecraft.world.entity.npc.villager.VillagerProfession profession,
                                                 List<WorkOrderType> types) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Work Order Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setProfession(profession);
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new SmarterOpenDoorsTask()),
                Pair.of(1, new WanderOrTeleportToTargetTask()),
                Pair.of(5, new WorkOrderTask(types))
        ), Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        villager.setNoAi(false);
        VILLAGERS.add(villager);
        return villager;
    }

    private static void setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private static void cleanup(ServerLevel level) {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
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
        if (previousEnabled != null) {
            Config.getServerConfig().villagerWorkOrders = previousEnabled;
            previousEnabled = null;
        }
        if (previousTeleport != null) {
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousTeleport = null;
        }
    }
}
