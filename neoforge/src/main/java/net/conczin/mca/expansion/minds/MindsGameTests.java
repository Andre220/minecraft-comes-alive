package net.conczin.mca.expansion.minds;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
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
 * Smarter villagers: profession skill, food variety, job by aptitude, school and home quality.
 */
@PrefixGameTestTemplate(false)
public final class MindsGameTests {
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Boolean previousSkills;
    private static Boolean previousEconomy;

    private MindsGameTests() {
    }

    @GameTest(batch = "mca_minds_skills", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void skillGrowsUpToTheIntelligenceCap(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(10112, 2, 400));
        prepare(helper, center);
        VillagerEntityMCA dull = spawn(helper, center, null, ImmutableList.of());
        dull.getGenetics().setGene(Genetics.INTELLIGENCE, 0.0F);
        for (int i = 0; i < 2000; i++) {
            Skills.gain(dull, Skills.GAIN_BATCH);
        }
        float skill = Skills.get(dull);
        helper.assertTrue(skill > 35 && skill <= 40, "a dull villager should end near its cap of 40, has " + skill);

        VillagerEntityMCA bright = spawn(helper, center.east(2), null, ImmutableList.of());
        bright.getGenetics().setGene(Genetics.INTELLIGENCE, 1.0F);
        helper.assertTrue(Skills.cap(bright) == 100.0F, "the brightest reach 100");
        float before = Skills.get(bright);
        Skills.gain(bright, Skills.GAIN_BATCH);
        Skills.gain(dull, 0); // no-op
        helper.assertTrue(Skills.get(bright) - before > 1.4F, "bright villagers learn faster: " + (Skills.get(bright) - before));

        Skills.set(bright, bright.getProfession(), 100.0F);
        helper.assertTrue(Skills.ticks(bright, 400) == 200, "a master works twice as fast: " + Skills.ticks(bright, 400));
        helper.assertTrue(Skills.rareFactor(bright) == 3.0, "a master finds rare things three times as often");
        Config.getServerConfig().villagerSkills = false;
        helper.assertTrue(Skills.ticks(bright, 400) == 400, "with the feature off nothing changes");
        Config.getServerConfig().villagerSkills = true;
        helper.succeed();
    }

    @GameTest(batch = "mca_minds_diet", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void repeatedFoodFillsLessAndVarietyCheersUp(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(10368, 2, 400));
        prepare(helper, center);
        VillagerEntityMCA villager = spawn(helper, center, null, ImmutableList.of());
        ItemStack bread = new ItemStack(Items.BREAD);
        float first = Diet.onAte(villager, bread);
        float second = Diet.onAte(villager, bread);
        float third = Diet.onAte(villager, bread);
        helper.assertTrue(first == 1.0F && Math.abs(second - 2 / 3F) < 0.01 && third == 0.5F,
                "bread fills 1, 2/3, 1/2: " + first + " " + second + " " + third);
        Diet.onAte(villager, bread);
        helper.assertTrue(Diet.thoughts(villager).stream().anyMatch(t -> t.key().equals("needs.thought.diet.monotonous")),
                "four breads in a row are boring");

        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(0, new ItemStack(Items.BREAD, 4));
        inventory.setItem(1, new ItemStack(Items.CARROT, 4));
        helper.assertTrue(Diet.pickFood(villager, inventory, stack -> stack.has(net.minecraft.core.component.DataComponents.FOOD)) == 1,
                "after so much bread the carrot comes first");

        for (ItemStack food : List.of(new ItemStack(Items.CARROT), new ItemStack(Items.APPLE), new ItemStack(Items.COOKED_BEEF))) {
            Diet.onAte(villager, food);
        }
        List<Needs.Thought> thoughts = Diet.thoughts(villager);
        helper.assertTrue(thoughts.stream().anyMatch(t -> t.key().equals("needs.thought.diet.varied")),
                "bread, carrot, apple and beef are varied: " + Diet.recent(villager));
        helper.succeed();
    }

    @GameTest(batch = "mca_minds_aptitude", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void joblessVillagerPicksTheJobItIsBestAt(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(10624, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos composter = center.offset(2, 0, 0);
        BlockPos chopping = center.offset(-6, 0, 0);
        setBlock(level, composter, Blocks.COMPOSTER.defaultBlockState());
        setBlock(level, chopping, EconomyRegistry.CHOPPING_BLOCK.defaultBlockState());

        VillagerEntityMCA villager = spawn(helper, center, null, ImmutableList.of(Pair.of(5, new AptitudeJobTask())));
        Skills.set(villager, EconomyRegistry.LUMBERJACK, 30.0F);

        helper.succeedWhen(() -> {
            GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
            helper.assertTrue(site != null && site.pos().equals(chopping),
                    "a skilled lumberjack should go for the chopping block, not the nearer composter: " + site);
        });
    }

    @GameTest(batch = "mca_minds_school", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void schoolIsRecognizedAndLessonsMakeSmarter(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(10880, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        Map<BlockPos, BlockState> furniture = new LinkedHashMap<>();
        furniture.put(center.offset(0, 0, -3), Blocks.LECTERN.defaultBlockState());
        for (int x = -2; x <= 1; x++) {
            furniture.put(center.offset(x, 0, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
            furniture.put(center.offset(x, 0, 3), Blocks.BOOKSHELF.defaultBlockState());
        }
        Village village = room(helper, center, furniture);
        helper.assertTrue(village.getBuildingsOfType(SchoolTask.SCHOOL).findAny().isPresent(),
                "the room should be a school: " + village.getBuildingAt(center.offset(2, 0, -2)).map(Building::getType));

        VillagerEntityMCA child = spawn(helper, center.offset(2, 0, -2), null, ImmutableList.of());
        child.getGenetics().setGene(Genetics.INTELLIGENCE, 0.5F);
        for (int i = 0; i < 10; i++) {
            SchoolTask.learn(child, 1);
        }
        float intelligence = child.getGenetics().getGene(Genetics.INTELLIGENCE);
        helper.assertTrue(Math.abs(intelligence - 0.52F) < 0.001, "ten lesson steps add 2% intelligence: " + intelligence);
        helper.succeed();
    }

    @GameTest(batch = "mca_minds_home", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void decoratedRoomyHomeIsCozyAndBareCrowdedOneCramped(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(11136, 2, 400));
        ServerLevel level = helper.getLevel();
        prepare(helper, center);
        BlockPos bed = center.offset(-2, 0, -2);
        Map<BlockPos, BlockState> furniture = new LinkedHashMap<>();
        BlockState bedState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        furniture.put(bed, bedState.setValue(BedBlock.PART, BedPart.HEAD));
        furniture.put(bed.north(), bedState.setValue(BedBlock.PART, BedPart.FOOT));
        furniture.put(center.offset(2, 0, -3), Blocks.CHEST.defaultBlockState());
        furniture.put(center.offset(-1, 0, -2), Blocks.LANTERN.defaultBlockState());
        for (int x = -2; x <= 3; x++) {
            furniture.put(center.offset(x, 0, 3), Blocks.FLOWER_POT.defaultBlockState());
        }
        Village village = room(helper, center, furniture);
        Building home = village.getBuildingAt(center.offset(1, 0, 0)).orElseThrow(() -> new AssertionError("no home building"));

        // light spreads a few ticks after placing the lantern
        helper.runAfterDelay(20, () -> {
            int cozy = HomeQuality.compute(level, home, 1, bed);
            helper.assertTrue(cozy >= HomeQuality.COZY, "a roomy, lit, decorated home with a chest is cozy: " + cozy);
            // the same room bare and dark for four people
            for (int x = -2; x <= 3; x++) {
                level.setBlock(center.offset(x, 0, 3), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(center.offset(-1, 0, -2), Blocks.AIR.defaultBlockState(), 3);
            helper.runAfterDelay(60, () -> {
                int cramped = HomeQuality.compute(level, home, 4, bed);
                helper.assertTrue(cramped < HomeQuality.CRAMPED, "a bare, dark home for four is cramped: " + cramped + " floor=" + home.getFloorFootprintArea() + " light=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bed.above()));
                helper.succeed();
            });
        });
    }

    @AfterBatch(batch = "mca_minds_skills")
    public static void cleanupSkills(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_minds_diet")
    public static void cleanupDiet(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_minds_aptitude")
    public static void cleanupAptitude(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_minds_school")
    public static void cleanupSchool(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_minds_home")
    public static void cleanupHome(ServerLevel level) {
        cleanup(level);
    }

    private static void prepare(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        if (previousSkills == null) {
            previousSkills = Config.getServerConfig().villagerSkills;
            previousEconomy = Config.getServerConfig().villagerEconomy;
        }
        Config.getServerConfig().villagerSkills = true;
        Config.getServerConfig().villagerEconomy = true;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 6, 12))) {
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
        prepareFlatArea(helper, center, 12, 6);
    }

    /** A stone hut (door to the south) with the given blocks inside, registered as a village building. */
    private static Village room(GameTestHelper helper, BlockPos center, Map<BlockPos, BlockState> furniture) {
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
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(), "fixture would modify an existing village");
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        VILLAGES.add(village.getId());
        village.setAutoScan(false);
        return village;
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos pos, VillagerProfession profession,
                                           ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> tasks) {
        ServerLevel level = helper.getLevel();
        level.getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Minds Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        if (profession != null) {
            villager.setProfession(profession);
        }
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, tasks, Set.of(), Set.of());
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
        if (previousSkills != null) {
            Config.getServerConfig().villagerSkills = previousSkills;
            Config.getServerConfig().villagerEconomy = previousEconomy;
            previousSkills = null;
            previousEconomy = null;
        }
    }
}
