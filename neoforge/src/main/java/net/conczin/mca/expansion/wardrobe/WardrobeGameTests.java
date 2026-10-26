package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Wardrobes and dressers: put down and broken as one piece; villagers put new clothes on there. */
@PrefixGameTestTemplate(false)
public final class WardrobeGameTests {
    private WardrobeGameTests() {
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        BlockState state = ((FurnitureBlock) block).connect(level, pos, block.defaultBlockState().setValue(FurnitureBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void furnitureIsOnePiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17536, 2, 400));
        prepareFlatArea(helper, center, 6, 4);
        Block wardrobe = Wardrobes.WARDROBE_BLOCKS.get("oak");
        Block dresser = Wardrobes.DRESSER_BLOCKS.get("oak");
        BlockPos w = center.offset(-2, 0, 0);
        BlockPos d = center.offset(1, 0, 0);
        place(level, w, wardrobe);
        place(level, d, dresser);

        helper.assertTrue(level.getBlockState(w.above()).getValue(WardrobeBlock.PART) == FurnitureBlock.Part.TOP_LEFT,
                "a wardrobe is two blocks tall");
        // facing south, the player's right is east
        helper.assertTrue(level.getBlockState(d.east()).getValue(DresserBlock.PART) == FurnitureBlock.Part.BOTTOM_RIGHT, "a dresser is two wide");
        helper.assertTrue(level.getBlockState(d.above()).isAir(), "and one block tall, to see over it");

        level.setBlockAndUpdate(w.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(d.east(), Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(w).isAir(), "breaking the top of a wardrobe breaks all of it");
            helper.assertTrue(level.getBlockState(d).isAir(), "breaking one side of a dresser breaks all of it");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void wardrobesJoinInThrees(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17736, 2, 400));
        prepareFlatArea(helper, center, 6, 4);
        Block wardrobe = Wardrobes.WARDROBE_BLOCKS.get("oak");
        // facing south, the player's right is east: four in a row make a wardrobe of three and one alone
        for (int i = 0; i < 4; i++) {
            place(level, center.offset(i - 2, 0, 0), wardrobe);
        }
        helper.runAfterDelay(2, () -> {
            String[] expected = {"false/true", "true/true", "true/false", "false/false"};
            for (int i = 0; i < 4; i++) {
                for (BlockPos pos : new BlockPos[]{center.offset(i - 2, 0, 0), center.offset(i - 2, 1, 0)}) {
                    BlockState state = level.getBlockState(pos);
                    String joins = state.getValue(WardrobeBlock.LEFT) + "/" + state.getValue(WardrobeBlock.RIGHT);
                    helper.assertTrue(joins.equals(expected[i]), "door " + i + " at " + pos + " joins " + joins + ", expected " + expected[i]);
                }
            }
            level.setBlockAndUpdate(center.offset(-2, 0, 0), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(2, () -> {
                BlockState last = level.getBlockState(center.offset(1, 0, 0));
                helper.assertTrue(last.getValue(WardrobeBlock.LEFT) && !last.getValue(WardrobeBlock.RIGHT),
                        "taking the first away, the last joins the other two");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void closetsHoldOutfits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17836, 2, 400));
        prepareFlatArea(helper, center, 6, 4);
        BlockPos w = center.offset(-2, 0, 0);
        BlockPos d = center.offset(1, 0, 0);
        place(level, w, Wardrobes.WARDROBE_BLOCKS.get("oak"));
        place(level, d, Wardrobes.DRESSER_BLOCKS.get("oak"));
        helper.assertTrue(level.getBlockEntity(w) instanceof ClosetBlockEntity wardrobe && wardrobe.size() == WardrobeBlock.SHELVES,
                "a wardrobe door holds three outfits");
        helper.assertTrue(!(level.getBlockEntity(w.above()) instanceof ClosetBlockEntity), "its top holds nothing of its own");
        helper.assertTrue(level.getBlockEntity(d.east()) instanceof ClosetBlockEntity dresser && dresser.size() == DresserBlock.DRAWERS,
                "each side of a dresser holds four");

        // the shelves from the bottom up, the drawers from the bottom left
        WardrobeBlock wardrobeBlock = (WardrobeBlock) Wardrobes.WARDROBE_BLOCKS.get("oak");
        helper.assertTrue(wardrobeBlock.slotAt(0.5, 0.3) == 0 && wardrobeBlock.slotAt(0.5, 1.0) == 1 && wardrobeBlock.slotAt(0.5, 1.8) == 2, "shelves");
        DresserBlock dresserBlock = (DresserBlock) Wardrobes.DRESSER_BLOCKS.get("oak");
        helper.assertTrue(dresserBlock.compartmentAt(0.2, 0.2) == 0 && dresserBlock.compartmentAt(0.8, 0.2) == 1
                          && dresserBlock.compartmentAt(0.2, 0.8) == 2 && dresserBlock.compartmentAt(0.8, 0.8) == 3, "drawers");
        // a point on the right of a piece facing west is to the north
        Vec3 east = FurnitureBlock.toSouth(new Vec3(0.5, 0.5, 0.1), Direction.WEST);
        helper.assertTrue(east.x < 0.5, "turned back to facing south: " + east);

        ClosetBlockEntity closet = (ClosetBlockEntity) level.getBlockEntity(w);
        closet.setItem(1, OutfitItem.of(Wardrobes.OUTFIT, "mca:skins/clothing/normal/male/none.png"));
        closet.setOpen(0, true);
        helper.assertTrue(closet.isOpen(0) && OutfitItem.clothes(closet.getItem(1)).endsWith("none.png"), "keeps what is put in, and opens");
        level.setBlockAndUpdate(w, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            boolean dropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(w).inflate(2),
                    e -> e.getItem().getItem() instanceof OutfitItem).isEmpty();
            helper.assertTrue(dropped, "breaking it drops the outfits inside");
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(w).inflate(3)).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void wardrobeDoorShuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17846, 2, 400));
        prepareFlatArea(helper, center, 4, 4);
        BlockPos w = center;
        place(level, w, Wardrobes.WARDROBE_BLOCKS.get("oak"));
        ClosetBlockEntity closet = (ClosetBlockEntity) level.getBlockEntity(w);
        for (int slot = 0; slot < WardrobeBlock.SHELVES; slot++) {
            closet.setItem(slot, OutfitItem.of(Wardrobes.OUTFIT, "mca:skins/clothing/normal/male/none.png"));
        }
        closet.setOpen(0, true);
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(w.getX() + 0.5, w.getY(), w.getZ() + 3.5);

        // a click on the door that reaches the server as a use of the block leaves it: the client shuts it by message
        BlockState top = level.getBlockState(w.above());
        top.useWithoutItem(level, player, new BlockHitResult(new Vec3(w.getX() + 0.05, w.getY() + 1.5, w.getZ() + 1.3), Direction.WEST, w.above(), false));
        helper.assertTrue(closet.isOpen(0), "a door click as a block use leaves it to the message");
        FurnitureBlock.shut(player, w, 0);
        helper.assertTrue(!closet.isOpen(0), "the message shuts it");

        // with every shelf full, a click in the empty space above an outfit still shuts it
        closet.setOpen(0, true);
        top.useWithoutItem(level, player, new BlockHitResult(new Vec3(w.getX() + 0.5, w.getY() + 1.17, w.getZ() + 0.9375), Direction.SOUTH, w.above(), false));
        helper.assertTrue(!closet.isOpen(0), "a click above the outfits shuts it");

        closet.setOpen(0, true);
        player.setPos(w.getX() + 0.5, w.getY(), w.getZ() + 20.5);
        FurnitureBlock.shut(player, w, 0);
        helper.assertTrue(closet.isOpen(0), "not from far away");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void playerWearsTheOutfit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        String before = "mca:skins/clothing/normal/neutral/none/5.png";
        String after = "mca:skins/clothing/normal/neutral/none/3.png";
        VillagerEntityMCA look = net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER.create(level, EntitySpawnReason.LOAD);
        look.setClothes(before);
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        look.saveWithoutId(output);
        net.conczin.mca.server.world.data.PlayerSaveData data = net.conczin.mca.server.world.data.PlayerSaveData.get(player);
        data.setEntityData(output.buildResult());

        helper.assertTrue(Wardrobes.dress(player, after).equals(before), "gives back what was worn");
        VillagerEntityMCA dressed = net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER.create(level, EntitySpawnReason.LOAD);
        dressed.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(),
                data.getEntityData()));
        helper.assertTrue(dressed.getClothes().equals(after), "the player's look wears it: " + dressed.getClothes());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void newClothesWaitForTheWardrobe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17636, 2, 400));
        prepareFlatArea(helper, center, 6, 4);
        boolean previous = Config.getInstance().villagerWardrobes;
        Config.getInstance().villagerWardrobes = true;
        try {
            VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                    .withAge(0)
                    .withPosition(Vec3.atBottomCenterOf(center))
                    .withName("Wardrobe Probe")
                    .spawn(EntitySpawnReason.STRUCTURE);
            villager.setVillagerXp(1);
            villager.setClothes("mca:skins/clothing/normal/male/none.png");
            String before = villager.getClothes();
            villager.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.FARMER));
            helper.assertTrue(Wardrobes.hasPendingClothes(villager), "a new job brings new clothes, waiting");
            helper.assertTrue(villager.getClothes().equals(before), "not worn yet: " + villager.getClothes());
            String pending = villager.getTrackedValue(Wardrobes.PENDING_CLOTHES);
            java.util.List<String> owned = Wardrobes.ownedClothes(villager);
            helper.assertTrue(owned.size() > 1 && owned.size() <= Wardrobes.OWNED && owned.contains(pending),
                    "a villager owns up to six outfits, the new one among them: " + owned);

            ChangeClothesTask task = new ChangeClothesTask();
            task.tryStart(level, villager, level.getGameTime());
            helper.assertTrue(Wardrobes.hasPendingClothes(villager), "no wardrobe nearby: still waiting");

            place(level, center.offset(1, 0, 0), Wardrobes.WARDROBE_BLOCKS.get("oak"));
            task.tryStart(level, villager, level.getGameTime());
            helper.assertTrue(villager.getClothes().equals(pending), "changed at the wardrobe: " + villager.getClothes() + " / " + pending);
            helper.assertTrue(!Wardrobes.hasPendingClothes(villager), "nothing left waiting");

            Config.getInstance().villagerWardrobes = false;
            villager.setProfession(ProfessionsMCA.ARCHER);
            helper.assertTrue(!Wardrobes.hasPendingClothes(villager), "with the feature off, clothes change at once");
            villager.discard();
        } finally {
            Config.getInstance().villagerWardrobes = previous;
        }
        helper.succeed();
    }
}
