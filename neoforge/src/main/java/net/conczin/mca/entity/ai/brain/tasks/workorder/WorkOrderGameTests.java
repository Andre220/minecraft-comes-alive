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
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.server.world.data.workorder.VillageWorkOrders;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrder;
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
        if (previousTeleport != null) {
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousTeleport = null;
        }
    }
}
