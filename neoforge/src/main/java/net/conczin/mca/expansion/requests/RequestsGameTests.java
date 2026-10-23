package net.conczin.mca.expansion.requests;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.expansion.HomeChests;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
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
 * Villager requests: escrowed rewards from the home chest, delivery for hearts, expiry and savings.
 */
@PrefixGameTestTemplate(false)
public final class RequestsGameTests {
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Boolean previous;

    private RequestsGameTests() {
    }

    @GameTest(batch = "mca_requests_deliver", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void requestEscrowsTheRewardAndPaysHeartsOnDelivery(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12112, 2, 400));
        ServerLevel level = helper.getLevel();
        VillagerEntityMCA villager = homeOwner(helper, center);
        Requests.fillSavings(level, villager); // marks the savings as given, the chest is filled by hand below
        Container chest = chest(helper, center);
        chest.clearContent();
        chest.setItem(0, new ItemStack(Items.EMERALD, 3));
        chest.setItem(1, new ItemStack(Items.BREAD, 10));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Requests.interact(player, villager);
        Requests.Request request = Requests.get(villager).orElseThrow(() -> new AssertionError("no request was made"));
        helper.assertTrue(!request.rewards().isEmpty(), "the reward should come from the home chest");
        int emeraldsLeft = VillageStorage.count(chest, Items.EMERALD);
        int promised = request.rewards().stream().filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(emeraldsLeft + promised == 3, "escrow takes the reward out of the chest: " + emeraldsLeft + " left, " + promised + " promised");

        int heartsBefore = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        player.getInventory().add(new ItemStack(request.item(), request.count()));
        Requests.interact(player, villager);
        helper.assertTrue(Requests.get(villager).isEmpty(), "the request should be done");
        helper.assertTrue(player.getInventory().countItem(request.item()) == 0 || request.rewards().stream().anyMatch(s -> s.is(request.item())),
                "the delivered items are gone");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) >= promised, "the player got the reward");
        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        helper.assertTrue(hearts == heartsBefore + request.hearts(), "hearts " + heartsBefore + " -> " + hearts + ", expected +" + request.hearts());
        helper.succeed();
    }

    @GameTest(batch = "mca_requests_expire", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void expiredRequestReturnsTheRewardAndCostsHearts(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12368, 2, 400));
        ServerLevel level = helper.getLevel();
        VillagerEntityMCA villager = homeOwner(helper, center);
        Requests.fillSavings(level, villager);
        Container chest = chest(helper, center);
        chest.clearContent();
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 2));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Requests.Request request = Requests.create(level, player, villager);
        helper.assertTrue(VillageStorage.count(chest, Items.GOLD_INGOT) == 0, "both gold ingots are set aside (worth 16)");
        Requests.set(villager, new Requests.Request(request.player(), request.item(), request.target(), request.count(),
                request.baseline(), request.rewards(), request.hearts(), level.getGameTime() - 1));
        int heartsBefore = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        Requests.expireIfDue(level, villager);
        helper.assertTrue(Requests.get(villager).isEmpty(), "the request should be over");
        helper.assertTrue(VillageStorage.count(chest, Items.GOLD_INGOT) == 2, "the reward goes back home");
        helper.assertTrue(villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() == heartsBefore - 3, "letting it run out costs 3 hearts");
        helper.succeed();
    }

    @GameTest(batch = "mca_requests_savings", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void firstRequestFillsTheHomeChestWithSavings(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12624, 2, 400));
        ServerLevel level = helper.getLevel();
        VillagerEntityMCA villager = homeOwner(helper, center);
        Container chest = chest(helper, center);
        helper.assertTrue(chest.isEmpty(), "the chest starts empty");
        helper.assertTrue(HomeChests.of(level, villager).size() == 1, "the chest is the villager's: " + HomeChests.of(level, villager));
        Requests.fillSavings(level, villager);
        helper.assertTrue(!chest.isEmpty(), "the savings loot table should fill the chest");
        ItemStack first = chest.getItem(0).copy();
        chest.clearContent();
        Requests.fillSavings(level, villager);
        helper.assertTrue(chest.isEmpty(), "savings are given only once (first was " + first + ")");
        helper.succeed();
    }

    @GameTest(batch = "mca_requests_hunt", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void huntRequestsLoadAndSurviveSaving(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12880, 2, 400));
        VillagerEntityMCA villager = homeOwner(helper, center);
        helper.assertTrue(RequestPool.objectives().stream().anyMatch(o -> o.target() == net.minecraft.world.entity.EntityTypes.ZOMBIE),
                "the default pool should ask for zombies too");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Requests.set(villager, new Requests.Request(player.getUUID(), Items.AIR, net.minecraft.world.entity.EntityTypes.ZOMBIE, 5, 7,
                List.of(new ItemStack(Items.EMERALD, 2)), 6, helper.getLevel().getGameTime() + 1000));
        Requests.Request read = Requests.get(villager).orElseThrow();
        helper.assertTrue(read.isHunt() && read.target() == net.minecraft.world.entity.EntityTypes.ZOMBIE && read.baseline() == 7
                          && read.count() == 5, "the hunt request must be saved: " + read);
        helper.assertTrue(read.rewards().size() == 1 && read.rewards().getFirst().is(Items.EMERALD), "rewards saved: " + read.rewards());
        helper.succeed();
    }

    @GameTest(batch = "mca_requests_board", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void requestBoardGetsARequestFromAResident(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(13136, 2, 400));
        VillagerEntityMCA villager = homeOwner(helper, center);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Requests.takeFromBoard(helper.getLevel(), center.offset(1, 0, 1), player);
        Requests.Request request = Requests.get(villager).orElse(null);
        helper.assertTrue(request != null && request.player().equals(player.getUUID()), "the resident should post a request for the player");
        Requests.takeFromBoard(helper.getLevel(), center.offset(1, 0, 1), player);
        helper.assertTrue(Requests.get(villager).map(r -> r.deadline() == request.deadline()).orElse(false),
                "a villager with a request doesn't post another one");
        helper.succeed();
    }

    @AfterBatch(batch = "mca_requests_board")
    public static void cleanupBoard(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_requests_hunt")
    public static void cleanupHunt(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_requests_deliver")
    public static void cleanupDeliver(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_requests_expire")
    public static void cleanupExpire(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_requests_savings")
    public static void cleanupSavings(ServerLevel level) {
        cleanup(level);
    }

    private static Container chest(GameTestHelper helper, BlockPos center) {
        return VillageStorage.getContainer(helper.getLevel(), center.offset(2, 0, -3)).orElseThrow();
    }

    /** A villager living in a one-bed stone hut with a chest. */
    private static VillagerEntityMCA homeOwner(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        if (previous == null) {
            previous = Config.getServerConfig().villagerRequests;
        }
        Config.getServerConfig().villagerRequests = true;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -3, -10), center.offset(10, 6, 10))) {
            BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos min = ChunkPos.containing(center.offset(-16, 0, -16));
        ChunkPos max = ChunkPos.containing(center.offset(16, 0, 16));
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
        prepareFlatArea(helper, center, 10, 6);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4 || y == 3) {
                        level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(center.south(4), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(center.south(4).above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos bed = center.offset(-2, 0, -2);
        BlockState bedState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        level.setBlock(bed.north(), bedState.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(bed, bedState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        level.setBlock(center.offset(2, 0, -3), Blocks.CHEST.defaultBlockState(), 3);

        VillageManager manager = VillageManager.get(level);
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(), "fixture would modify an existing village");
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "home registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        VILLAGES.add(village.getId());
        village.setAutoScan(false);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(center))
                .withName("Request Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, ImmutableList.of(), Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
        villager.getResidency().onHomeClaimed();
        VILLAGERS.add(villager);
        return villager;
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
        if (previous != null) {
            Config.getServerConfig().villagerRequests = previous;
            previous = null;
        }
    }
}
