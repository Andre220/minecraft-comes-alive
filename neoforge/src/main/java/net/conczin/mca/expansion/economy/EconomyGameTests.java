package net.conczin.mca.expansion.economy;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.expansion.economy.task.DigSiteTask;
import net.conczin.mca.expansion.economy.task.FisherTask;
import net.conczin.mca.expansion.economy.task.ForagerTask;
import net.conczin.mca.expansion.economy.task.GathererTask;
import net.conczin.mca.expansion.economy.task.HaulerTask;
import net.conczin.mca.expansion.economy.task.WorkshopProductionTask;
import net.conczin.mca.server.property.PropertyRules;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.conczin.mca.expansion.economy.task.LumberjackTask;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/**
 * The village economy: station POIs, gatherers working around their station and the village's
 * share of the haul ending up in the workshop chest or the storage.
 */
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Boolean previousTeleport;
    private static Boolean previousEnabled;

    private EconomyGameTests() {
    }

    @GameTest(batch = "mca_economy_registry", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void stationsArePointsOfInterest(GameTestHelper helper) {
        for (var entry : Map.of(
                EconomyRegistry.CHOPPING_BLOCK, EconomyRegistry.CHOPPING_BLOCK_POI,
                EconomyRegistry.HUNTING_RACK, EconomyRegistry.HUNTING_RACK_POI,
                EconomyRegistry.FORAGE_BASKET, EconomyRegistry.FORAGE_BASKET_POI,
                EconomyRegistry.SIFTING_SIEVE, EconomyRegistry.SIFTING_SIEVE_POI).entrySet()) {
            Holder<PoiType> poi = PoiTypes.forState(entry.getKey().defaultBlockState()).orElse(null);
            helper.assertTrue(poi != null && poi.is(entry.getValue()), entry.getKey() + " is not its POI: " + poi);
        }
        Holder<PoiType> chopping = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(EconomyRegistry.CHOPPING_BLOCK_POI);
        Holder<PoiType> sieve = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(EconomyRegistry.SIFTING_SIEVE_POI);
        helper.assertTrue(chopping.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "the chopping block must be a job site");
        helper.assertTrue(!sieve.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "the sieve is no job site");
        helper.assertTrue(EconomyRegistry.LUMBERJACK.heldJobSite().test(chopping), "lumberjacks hold the chopping block");

        boolean previous = Config.getServerConfig().villagerEconomy;
        Config.getServerConfig().villagerEconomy = false;
        helper.assertTrue(!EconomyRegistry.isAcquirable(chopping), "with the feature off nobody takes the job");
        helper.assertTrue(EconomyRegistry.isAcquirable(BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.MASON)),
                "vanilla job sites stay available");
        Config.getServerConfig().villagerEconomy = true;
        helper.assertTrue(EconomyRegistry.isAcquirable(chopping), "with the feature on the job is open");
        Config.getServerConfig().villagerEconomy = previous;
        helper.assertTrue(VillageEconomyManager.wantedHaulers(1) == 0, "one workshop needs no hauler");
        helper.assertTrue(VillageEconomyManager.wantedHaulers(2) == 1, "two workshops get a hauler");
        helper.assertTrue(VillageEconomyManager.wantedHaulers(6) == 2, "six workshops get two haulers");
        helper.succeed();
    }

    @GameTest(batch = "mca_economy_lumberjack", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 2400)
    public static void lumberjackFellsReplantsAndDeliversTheVillageShare(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8112, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos station = center.offset(0, 0, -2);
        BlockPos chestPos = center.offset(2, 0, -2);
        Village village = workshop(helper, center, station, EconomyRegistry.CHOPPING_BLOCK.defaultBlockState(), chestPos);
        village.setTaxes(1.0F);

        // a small oak south of the hut, leaves kept so they don't drop anything
        BlockPos tree = center.offset(0, 0, 10);
        setBlock(level, tree.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        for (int y = 0; y < 4; y++) {
            setBlock(level, tree.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (BlockPos pos : BlockPos.betweenClosed(tree.offset(-1, 4, -1), tree.offset(1, 5, 1))) {
            setBlock(level, pos.immutable(), leaves);
        }

        LumberjackTask task = new LumberjackTask();
        VillagerEntityMCA lumberjack = spawnGatherer(helper, center.offset(0, 0, 1), EconomyRegistry.LUMBERJACK, station, task);
        lumberjack.getInventory().addItem(new ItemStack(Items.OAK_SAPLING));

        Container chest = container(helper, chestPos);
        helper.succeedWhen(() -> {
            helper.assertTrue(!level.getBlockState(tree.above()).is(Blocks.OAK_LOG), "the tree still stands; " + describe(lumberjack, task));
            helper.assertTrue(level.getBlockState(tree).is(Blocks.OAK_SAPLING), "no sapling was planted where the tree stood; " + describe(lumberjack, task));
            int delivered = VillageStorage.count(chest, Items.OAK_LOG);
            helper.assertTrue(delivered == 4, "with 100% taxes all 4 logs go to the workshop chest, it holds " + delivered + "; " + describe(lumberjack, task));
        });
    }

    @GameTest(batch = "mca_economy_forager", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 1600)
    public static void foragerPicksBerriesAndKeepsTheBush(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8368, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos station = center.offset(0, 0, -2);
        Village village = workshop(helper, center, station, EconomyRegistry.FORAGE_BASKET.defaultBlockState(), center.offset(2, 0, -2));
        village.setTaxes(0.0F);

        BlockPos bush = center.offset(3, 0, 10);
        setBlock(level, bush.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        setBlock(level, bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));

        VillagerEntityMCA forager = spawnGatherer(helper, center.offset(0, 0, 1), EconomyRegistry.FORAGER, station, new ForagerTask());

        helper.succeedWhen(() -> {
            BlockState state = level.getBlockState(bush);
            helper.assertTrue(state.is(Blocks.SWEET_BERRY_BUSH), "the bush must stay");
            helper.assertTrue(state.getValue(SweetBerryBushBlock.AGE) == 1, "the berries were not picked");
            int berries = VillageStorage.count(forager.getInventory(), Items.SWEET_BERRIES);
            helper.assertTrue(berries >= 2, "with no taxes the forager keeps the berries, has " + berries);
        });
    }

    @GameTest(batch = "mca_economy_dig_site", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 2400)
    public static void minerTakesSandAtTheSieveToTheStorage(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8624, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        // a storage room: lectern and four chests
        for (BlockPos chest : List.of(center.offset(3, 0, -3), center.offset(-3, 0, 1), center.offset(3, 0, 1))) {
            setBlock(level, chest, Blocks.CHEST.defaultBlockState());
        }
        Village village = workshop(helper, center, center.offset(-2, 0, -2), Blocks.LECTERN.defaultBlockState(), center.offset(-3, 0, -3));
        if (!VillageStorage.exists(village)) {
            VillageManager.get(level).forceRoomType(center, VillageStorage.STORAGE_BUILDING);
        }
        helper.assertTrue(VillageStorage.exists(village), "fixture room is not a storage");
        village.setTaxes(1.0F);

        // a sieve with three sand blocks on the surface next to it
        BlockPos sieve = center.offset(0, 0, 10);
        setBlock(level, sieve, EconomyRegistry.SIFTING_SIEVE.defaultBlockState());
        List<BlockPos> sand = List.of(sieve.offset(2, -1, 0), sieve.offset(3, -1, 0), sieve.offset(2, -1, 1));
        sand.forEach(pos -> {
            setBlock(level, pos.below(), Blocks.STONE.defaultBlockState());
            setBlock(level, pos, Blocks.SAND.defaultBlockState());
        });

        DigSiteTask task = new DigSiteTask();
        VillagerEntityMCA miner = spawnGatherer(helper, center.offset(0, 0, 7), ProfessionsMCA.MINER, null, task);

        helper.succeedWhen(() -> {
            sand.forEach(pos -> helper.assertTrue(!level.getBlockState(pos).is(Blocks.SAND), "sand left at " + pos + "; " + describe(miner, task)));
            int stored = VillageStorage.count(level, village, Items.SAND);
            helper.assertTrue(stored == 3, "with 100% taxes the storage gets all 3 sand, holds " + stored + "; " + describe(miner, task));
        });
    }

    @GameTest(batch = "mca_economy_bakery", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 2400)
    public static void bakerBakesBreadFromTheWorkshopChest(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8880, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos smoker = center.offset(0, 0, -2);
        BlockPos chestPos = center.offset(2, 0, -2);
        Village village = room(helper, center, Map.of(
                smoker, Blocks.SMOKER.defaultBlockState(),
                center.offset(-2, 0, -2), Blocks.CAKE.defaultBlockState(),
                chestPos, Blocks.CHEST.defaultBlockState()), true);
        if (!"bakery".equals(ProductionSink.buildingTypeOf(village, smoker))) {
            VillageManager.get(level).forceRoomType(center, "bakery");
        }
        helper.assertTrue("bakery".equals(ProductionSink.buildingTypeOf(village, smoker)),
                "fixture is no bakery: " + ProductionSink.buildingTypeOf(village, smoker));
        village.setTaxes(1.0F);
        Container chest = container(helper, chestPos);
        chest.setItem(0, new ItemStack(Items.WHEAT, 6));

        VillagerEntityMCA baker = spawnWorker(helper, center.offset(0, 0, -1), BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.BUTCHER), smoker,
                ImmutableList.of(Pair.of(5, new WorkshopProductionTask())));

        helper.succeedWhen(() -> {
            int bread = VillageStorage.count(chest, Items.BREAD);
            int wheat = VillageStorage.count(chest, Items.WHEAT);
            helper.assertTrue(bread == 2 && wheat == 0, "6 wheat should become 2 bread in the chest, chest has " + bread + " bread and "
                                                       + wheat + " wheat; baker has " + VillageStorage.count(baker.getInventory(), Items.BREAD) + " bread");
            PropertyRules.Owners owners = PropertyRules.ownersAt(level, chestPos);
            helper.assertTrue(owners != null && owners.villagers().contains(baker.getUUID()), "the workshop chest should be the baker's: " + owners);
        });
    }

    @GameTest(batch = "mca_economy_hauler", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 3600)
    public static void haulerStocksTheBakeryAndStoresItsBread(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(9136, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        Map<BlockPos, BlockState> storageRoom = new LinkedHashMap<>();
        storageRoom.put(center.offset(-2, 0, -2), Blocks.LECTERN.defaultBlockState());
        for (BlockPos chest : List.of(center.offset(-3, 0, -3), center.offset(3, 0, -3), center.offset(-3, 0, 1), center.offset(3, 0, 1))) {
            storageRoom.put(chest, Blocks.CHEST.defaultBlockState());
        }
        Village village = room(helper, center, storageRoom, true);
        if (!VillageStorage.exists(village)) {
            VillageManager.get(level).forceRoomType(center, VillageStorage.STORAGE_BUILDING);
        }
        helper.assertTrue(VillageStorage.exists(village), "fixture room is not a storage");

        BlockPos bakery = center.offset(12, 0, 0);
        BlockPos smoker = bakery.offset(0, 0, -2);
        BlockPos bakeryChestPos = bakery.offset(2, 0, -2);
        room(helper, bakery, Map.of(
                smoker, Blocks.SMOKER.defaultBlockState(),
                bakery.offset(-2, 0, -2), Blocks.CAKE.defaultBlockState(),
                bakeryChestPos, Blocks.CHEST.defaultBlockState()), false);
        if (!"bakery".equals(ProductionSink.buildingTypeOf(village, smoker))) {
            VillageManager.get(level).forceRoomType(bakery, "bakery");
        }
        Container bakeryChest = container(helper, bakeryChestPos);
        bakeryChest.setItem(0, new ItemStack(Items.BREAD, 10));
        container(helper, center.offset(-3, 0, -3)).setItem(0, new ItemStack(Items.WHEAT, 15));
        helper.assertTrue(Workshops.of(level, village).stream().anyMatch(w -> w.station().equals(smoker)),
                "the bakery is not a workshop: " + Workshops.of(level, village));

        HaulerTask task = new HaulerTask(false);
        VillagerEntityMCA hauler = spawnWorker(helper, center.offset(6, 0, 7), EconomyRegistry.HAULER, null,
                ImmutableList.of(Pair.of(0, new SmarterOpenDoorsTask()), Pair.of(1, new WanderOrTeleportToTargetTask()), Pair.of(5, task)));

        helper.succeedWhen(() -> {
            String state = "; hauler at " + hauler.blockPosition() + " " + task.getStatus() + " " + task.debugState();
            int wheat = VillageStorage.count(bakeryChest, Items.WHEAT);
            helper.assertTrue(wheat == 15, "the bakery should be stocked with 5 batches (15 wheat), has " + wheat + state);
            int bread = VillageStorage.count(level, village, Items.BREAD);
            helper.assertTrue(bread == 10 && VillageStorage.count(bakeryChest, Items.BREAD) == 0,
                    "the bread should be in the storage, storage holds " + bread + state);
        });
    }

    @GameTest(batch = "mca_economy_fisher", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 3000)
    public static void fishermanFishesFromTheShore(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(9392, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos barrel = center.offset(0, 0, -2);
        Village village = room(helper, center, Map.of(barrel, Blocks.BARREL.defaultBlockState()), true);
        village.setTaxes(1.0F);
        // a 3x3 pond south of the hut
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -2, 8), center.offset(1, -1, 10))) {
            setBlock(level, pos.immutable(), Blocks.WATER.defaultBlockState());
        }

        FisherTask task = new FisherTask();
        VillagerEntityMCA fisherman = spawnWorker(helper, center.offset(0, 0, 1),
                BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.FISHERMAN), barrel,
                ImmutableList.of(Pair.of(0, new SmarterOpenDoorsTask()), Pair.of(1, new WanderOrTeleportToTargetTask()), Pair.of(5, task)));
        fisherman.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        fisherman.setVillagerXp(1); // MCA drafts jobless-looking villagers (no experience) as guards

        // a catch every 200 to 400 ticks; delivering to the barrel is covered by the lumberjack test
        helper.succeedWhen(() -> {
            int caught = 0;
            for (int slot = 0; slot < fisherman.getInventory().getContainerSize(); slot++) {
                ItemStack stack = fisherman.getInventory().getItem(slot);
                caught += stack.is(Items.FISHING_ROD) ? 0 : stack.getCount();
            }
            helper.assertTrue(caught >= 3, "caught only " + caught + "; " + describe(fisherman, task));
        });
    }

    @AfterBatch(batch = "mca_economy_fisher")
    public static void cleanupFisher(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_economy_bakery")
    public static void cleanupBakery(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_economy_hauler")
    public static void cleanupHauler(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_economy_lumberjack")
    public static void cleanupLumberjack(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_economy_forager")
    public static void cleanupForager(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_economy_dig_site")
    public static void cleanupDigSite(ServerLevel level) {
        cleanup(level);
    }

    private static void prepare(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        if (previousTeleport == null) {
            previousTeleport = Config.getInstance().allowVillagerTeleporting;
        }
        Config.getInstance().allowVillagerTeleporting = false;
        if (previousEnabled == null) {
            previousEnabled = Config.getServerConfig().villagerEconomy;
        }
        Config.getServerConfig().villagerEconomy = true;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 8, 16))) {
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
        prepareFlatArea(helper, center, 16, 8);
    }

    /** A stone hut (door to the south) with a station and a chest, registered as a village building. */
    private static Village workshop(GameTestHelper helper, BlockPos center, BlockPos station, BlockState stationState, BlockPos chest) {
        return room(helper, center, Map.of(station, stationState, chest, Blocks.CHEST.defaultBlockState()), true);
    }

    /** A stone hut (door to the south) with the given blocks inside, registered as a village building. */
    private static Village room(GameTestHelper helper, BlockPos center, Map<BlockPos, BlockState> furniture, boolean newVillage) {
        ServerLevel level = helper.getLevel();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4 || y == 3) {
                        setBlock(level, center.offset(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        BlockPos door = center.south(4);
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        setBlock(level, door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        setBlock(level, door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        furniture.forEach((pos, state) -> setBlock(level, pos, state));

        VillageManager manager = VillageManager.get(level);
        if (newVillage) {
            helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(), "fixture would modify an existing village");
        }
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "workshop registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        VILLAGES.add(village.getId());
        village.setAutoScan(false);
        return village;
    }

    /** A gatherer whose brain only walks, opens doors and runs its job, so schedules can't interfere. */
    private static VillagerEntityMCA spawnGatherer(GameTestHelper helper, BlockPos pos, VillagerProfession profession,
                                                   BlockPos station, GathererTask task) {
        ServerLevel level = helper.getLevel();
        level.getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Economy Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setProfession(profession);
        villager.setVillagerXp(1); // MCA drafts villagers without experience as guards, which replaces the test brain
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        if (station != null) {
            brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), station));
        }
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new SmarterOpenDoorsTask()),
                Pair.of(1, new WanderOrTeleportToTargetTask()),
                Pair.of(5, task)
        ), Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        villager.setNoAi(false);
        VILLAGERS.add(villager);
        return villager;
    }

    private static String describe(VillagerEntityMCA villager, GathererTask task) {
        StringBuilder inventory = new StringBuilder();
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                inventory.append(stack.getCount()).append(' ').append(stack.getItem()).append(", ");
            }
        }
        return "villager at " + villager.blockPosition() + " status=" + task.getStatus() + " " + task.debugState()
               + " enabled=" + EconomyFeature.enabled() + " panicking=" + villager.getVillagerBrain().isPanicking()
               + " time=" + villager.level().getGameTime() + " activity=" + villager.getBrain().getActiveNonCoreActivity()
               + " inventory=[" + inventory + "]";
    }

    /** A worker with only the given behaviours, so schedules can't interfere. */
    private static VillagerEntityMCA spawnWorker(GameTestHelper helper, BlockPos pos, VillagerProfession profession, BlockPos station,
                                                 ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> tasks) {
        ServerLevel level = helper.getLevel();
        level.getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Economy Worker")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setProfession(profession);
        villager.setVillagerXp(1); // MCA drafts villagers without experience as guards, which replaces the test brain
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        if (station != null) {
            brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), station));
        }
        brain.addActivity(Activity.CORE, tasks, Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        villager.setNoAi(false);
        VILLAGERS.add(villager);
        return villager;
    }

    private static Container container(GameTestHelper helper, BlockPos pos) {
        return VillageStorage.getContainer(helper.getLevel(), pos).orElseThrow(() -> new AssertionError("no chest at " + pos));
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
            Config.getServerConfig().villagerEconomy = previousEnabled;
            previousEnabled = null;
        }
        if (previousTeleport != null) {
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousTeleport = null;
        }
    }
}
