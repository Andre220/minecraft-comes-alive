package net.conczin.mca.expansion.minds;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.expansion.minds.routine.IdleRoutineTask;
import net.conczin.mca.expansion.minds.routine.Seats;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/**
 * Free time routines: villagers sit on benches and seek company instead of random strolls.
 */
@PrefixGameTestTemplate(false)
public final class RoutineGameTests {
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static Boolean previous;

    private RoutineGameTests() {
    }

    @GameTest(batch = "mca_routine_sit", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void villagerSitsOnABenchAndGetsUpAgain(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(15112, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos bench = center.offset(4, 0, 0);
        set(level, bench, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        // a roof: stairs up in the air, nothing to stand on in front; never a seat
        BlockPos roof = center.offset(-4, 3, 0);
        set(level, roof.below(), Blocks.OAK_PLANKS.defaultBlockState());
        set(level, roof, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        helper.assertTrue(!Seats.isSeat(level, roof), "a roof stair must not be a seat");
        set(level, roof, Blocks.AIR.defaultBlockState());
        set(level, roof.below(), Blocks.AIR.defaultBlockState());

        IdleRoutineTask routine = new IdleRoutineTask();
        VillagerEntityMCA villager = spawn(helper, center, routine);

        helper.succeedWhen(() -> {
            helper.assertTrue(Seats.isSitting(villager), "the villager should sit on the bench; it is at " + villager.blockPosition()
                    + " doing " + (routine.current() == null ? "nothing" : routine.current().kind()));
            helper.assertTrue(bench.equals(Seats.seatOf(villager)), "it sits on the wrong spot: " + Seats.seatOf(villager));
            routine.doStop(level, villager, level.getGameTime());
            helper.assertTrue(!villager.isPassenger(), "it must get up when the routine ends");
            helper.assertTrue(level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(bench).inflate(1),
                    d -> d.entityTags().contains(Seats.TAG)).isEmpty(), "the seat must be removed");
        });
    }

    @GameTest(batch = "mca_routine_company", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void villagerSeeksCompanyInsteadOfStrollingAround(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(15368, 2, 400));
        prepare(helper, center);
        VillagerEntityMCA friend = spawn(helper, center.offset(8, 0, 0), null);
        VillagerEntityMCA villager = spawn(helper, center.offset(-2, 0, 0), new IdleRoutineTask());

        helper.succeedWhen(() -> helper.assertTrue(villager.distanceTo(friend) <= 3.0,
                "the villager should go to the other one, distance " + villager.distanceTo(friend)));
    }

    @GameTest(batch = "mca_routine_inspect", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void villagerHoldsACopyOfWhatIsOnDisplay(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(15624, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        net.minecraft.world.entity.decoration.ArmorStand stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
        stand.setPos(Vec3.atBottomCenterOf(center.offset(5, 0, 0)));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        level.addFreshEntity(stand);
        IdleRoutineTask routine = new IdleRoutineTask();
        VillagerEntityMCA villager = spawn(helper, center, routine);

        helper.succeedWhen(() -> {
            boolean holds = villager.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)
                            || villager.getOffhandItem().is(net.minecraft.world.item.Items.IRON_SWORD);
            helper.assertTrue(holds, "the villager should hold a copy of the sword; doing "
                    + (routine.current() == null ? "nothing" : routine.current().kind()) + " at " + villager.blockPosition());
            helper.assertTrue(stand.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD), "the stand keeps its sword");
            routine.doStop(level, villager, level.getGameTime());
            helper.assertTrue(villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty(), "the copy goes away afterwards");
            stand.discard();
        });
    }

    @GameTest(batch = "mca_routine_chair", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void villagerSitsOnAnotherModsChair(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(15880, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        // a Tables & Chairs 2 chair is an item display tagged "tac" and "chair" that players ride
        Display.ItemDisplay chair = net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        chair.addTag("tac");
        chair.addTag("chair");
        chair.setPos(Vec3.atBottomCenterOf(center.offset(4, 0, 0)).add(0, 0.3, 0));
        level.addFreshEntity(chair);
        IdleRoutineTask routine = new IdleRoutineTask();
        VillagerEntityMCA villager = spawn(helper, center, routine);

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getVehicle() == chair, "the villager should sit on the chair; doing "
                    + (routine.current() == null ? "nothing" : routine.current().kind()) + " at " + villager.blockPosition());
            routine.doStop(level, villager, level.getGameTime());
            helper.assertTrue(!villager.isPassenger(), "it gets up again");
            helper.assertTrue(chair.isAlive(), "the chair belongs to the player and stays");
            chair.discard();
        });
    }

    @GameTest(batch = "mca_routine_read", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void villagerReadsAtTheLecternWithABook(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16136, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        set(level, center.offset(4, 0, 0), Blocks.LECTERN.defaultBlockState());
        IdleRoutineTask routine = new IdleRoutineTask();
        VillagerEntityMCA villager = spawn(helper, center, routine);

        helper.succeedWhen(() -> helper.assertTrue(villager.getMainHandItem().is(net.minecraft.world.item.Items.BOOK),
                "the villager should read with a book in hand; doing " + (routine.current() == null ? "nothing" : routine.current().kind())));
    }

    @GameTest(batch = "mca_routine_feast", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void aServingIsTakenFromTheTable(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16236, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos cake = center.offset(1, 0, 0);
        set(level, cake, Blocks.CAKE.defaultBlockState());
        VillagerEntityMCA villager = spawn(helper, center, null);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(net.conczin.mca.expansion.minds.routine.Feasts.takeServing(level, villager, cake), "a piece should be taken");
            helper.assertTrue(level.getBlockState(cake).getValue(net.minecraft.world.level.block.CakeBlock.BITES) == 1, "one bite should be gone");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_routine_shutters", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void shuttersOpenTogetherAndFollowTheDay(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16336, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        net.minecraft.world.level.block.Block block = net.conczin.mca.expansion.building.Shutters.SHUTTERS.get("oak");
        net.minecraft.world.level.block.state.BlockState closed = block.defaultBlockState()
                .setValue(net.conczin.mca.expansion.building.ShutterBlock.OPEN, false);
        BlockPos low = center.offset(2, 0, 0);
        set(level, low, closed);
        set(level, low.above(), closed);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(low.east()).getBlock() instanceof net.conczin.mca.expansion.building.ShutterLeafBlock,
                    "a closed shutter puts its clickable panel in front of the window");
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            level.getBlockState(low).useWithoutItem(level, player,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(low), net.minecraft.core.Direction.SOUTH, low, false));
            helper.assertTrue(level.getBlockState(low.above()).getValue(net.conczin.mca.expansion.building.ShutterBlock.OPEN),
                    "the shutter above should open with the one used");
            helper.assertTrue(level.getBlockState(low.east()).isAir(), "an open shutter takes its panel back");
            var toggle = net.conczin.mca.expansion.minds.routine.DailyToggles.forBlock(closed);
            helper.assertTrue(toggle != null, "shutters should be switched by villagers");
            helper.assertTrue(toggle.wanted(closed, 1000) != null, "closed shutters should be opened in the morning");
            helper.assertTrue(toggle.wanted(closed, 14000) == null, "closed shutters stay closed at night");
            helper.assertTrue(toggle.wanted(level.getBlockState(low), 14000) != null, "open shutters should be closed at night");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_routine_casement", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void casementOpensLikeAGate(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16436, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        net.minecraft.world.level.block.Block block = net.conczin.mca.expansion.building.Shutters.CASEMENTS.get("oak");
        BlockPos window = center.offset(2, 0, 0);
        set(level, window, block.defaultBlockState());

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockEntity(window) instanceof net.conczin.mca.expansion.building.SwingBlockEntity,
                    "a casement remembers its swing for the animation");
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            level.getBlockState(window).useWithoutItem(level, player,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(window), net.minecraft.core.Direction.NORTH, window, false));
            var opened = level.getBlockState(window);
            helper.assertTrue(opened.getValue(net.conczin.mca.expansion.building.CasementBlock.OPEN), "using it opens it");
            helper.assertTrue(opened.getValue(net.conczin.mca.expansion.building.CasementBlock.LEAF) == net.conczin.mca.expansion.building.CasementBlock.Leaf.NONE,
                    "the leaf property only exists for the models");
            var toggle = net.conczin.mca.expansion.minds.routine.DailyToggles.forBlock(opened);
            helper.assertTrue(toggle != null && toggle.wanted(opened, 14000) != null, "villagers close it at night");
            helper.succeed();
        });
    }

    @AfterBatch(batch = "mca_routine_casement")
    public static void cleanupCasement(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_shutters")
    public static void cleanupShutters(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_feast")
    public static void cleanupFeast(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_inspect")
    public static void cleanupInspect(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_chair")
    public static void cleanupChair(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_read")
    public static void cleanupRead(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_sit")
    public static void cleanupSit(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_routine_company")
    public static void cleanupCompany(ServerLevel level) {
        cleanup(level);
    }

    private static void prepare(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        if (previous == null) {
            previous = Config.getServerConfig().villagerRoutines;
        }
        Config.getServerConfig().villagerRoutines = true;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-14, -3, -14), center.offset(14, 6, 14))) {
            BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos min = ChunkPos.containing(center.offset(-20, 0, -20));
        ChunkPos max = ChunkPos.containing(center.offset(20, 0, 20));
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
        prepareFlatArea(helper, center, 14, 6);
    }

    /** A villager in free time whose idle activity is only the routine (or nothing at all). */
    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos pos, IdleRoutineTask routine) {
        ServerLevel level = helper.getLevel();
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Routine Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setVillagerXp(1); // not drafted as a guard
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new SmarterOpenDoorsTask()),
                Pair.of(1, new WanderOrTeleportToTargetTask())
        ), Set.of(), Set.of());
        brain.addActivity(Activity.IDLE, routine == null ? ImmutableList.of() : ImmutableList.of(Pair.of(2, routine)), Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        villager.setNoAi(false);
        VILLAGERS.add(villager);
        return villager;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private static void cleanup(ServerLevel level) {
        VILLAGERS.forEach(villager -> {
            Seats.standUp(villager);
            villager.discard();
        });
        VILLAGERS.clear();
        BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        BLOCKS.clear();
        CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        CHUNKS.clear();
        if (previous != null) {
            Config.getServerConfig().villagerRoutines = previous;
            previous = null;
        }
    }
}
