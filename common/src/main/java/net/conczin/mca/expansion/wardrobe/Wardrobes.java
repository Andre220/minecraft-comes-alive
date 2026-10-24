package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.Config;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.conczin.mca.resources.WeightedPool;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wardrobes, dressers and outfits. With {@link Config#villagerWardrobes} on, a villager's new clothes (a new
 * profession, growing up) wait until it passes by the wardrobe or dresser at home; the player changes clothes there
 * any time.
 */
public final class Wardrobes {
    /** Anything villagers change clothes at: MCA's wardrobes and dressers, and whatever else is added here. */
    public static final TagKey<Block> WARDROBES = TagKey.create(Registries.BLOCK, MCA.locate("wardrobes"));
    /** Clothes waiting to be put on at a wardrobe, or empty. */
    public static final CDataParameter<String> PENDING_CLOTHES = CParameter.create("PendingClothes", "");
    /** The day the pending clothes were picked; without a wardrobe to go to, they are put on a day later. */
    public static final CDataParameter<Integer> PENDING_SINCE = CParameter.create("PendingClothesDay", 0);
    /** The clothes a villager owns, separated by {@link #SEPARATOR}. */
    public static final CDataParameter<String> OWNED_CLOTHES = CParameter.create("OwnedClothes", "");
    private static final String SEPARATOR = "|";
    /** How many outfits a villager owns. */
    public static final int OWNED = 6;
    /** How far a player can be from a closet to change there. */
    private static final double REACH_PLAYER = 8.0;
    /** The doors and drawers of wardrobes and dressers, and what is in them. Set once registered. */
    public static BlockEntityType<ClosetBlockEntity> CLOSET;
    private static final long DAY = 24000L;
    /** How close a villager comes to a wardrobe to change. */
    static final double REACH = 2.5;
    /** How far from home a villager looks for its wardrobe. */
    static final int HOME_RADIUS = 16;

    public static final Map<String, Block> WARDROBE_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Block> DRESSER_BLOCKS = new LinkedHashMap<>();
    public static final Item OUTFIT = ItemsMCA.register("outfit", new OutfitItem(ItemsMCA.baseProps("outfit").stacksTo(1)));

    static {
        furniture("oak", Blocks.OAK_PLANKS);
        furniture("spruce", Blocks.SPRUCE_PLANKS);
        furniture("birch", Blocks.BIRCH_PLANKS);
        furniture("jungle", Blocks.JUNGLE_PLANKS);
        furniture("acacia", Blocks.ACACIA_PLANKS);
        furniture("dark_oak", Blocks.DARK_OAK_PLANKS);
        furniture("mangrove", Blocks.MANGROVE_PLANKS);
        furniture("cherry", Blocks.CHERRY_PLANKS);
        furniture("pale_oak", Blocks.PALE_OAK_PLANKS);
        furniture("bamboo", Blocks.BAMBOO_PLANKS);
        furniture("crimson", Blocks.CRIMSON_PLANKS);
        furniture("warped", Blocks.WARPED_PLANKS);
    }

    private static void furniture(String wood, Block planks) {
        String wardrobe = wood + "_wardrobe";
        Block block = BlocksMCA.register(wardrobe, new WardrobeBlock(BlocksMCA.baseProps(wardrobe, planks).noOcclusion()));
        ItemsMCA.register(wardrobe, new BlockItem(block, ItemsMCA.blockProps(wardrobe)));
        WARDROBE_BLOCKS.put(wood, block);

        String dresser = wood + "_dresser";
        block = BlocksMCA.register(dresser, new DresserBlock(BlocksMCA.baseProps(dresser, planks).noOcclusion()));
        ItemsMCA.register(dresser, new BlockItem(block, ItemsMCA.blockProps(dresser)));
        DRESSER_BLOCKS.put(wood, block);
    }

    private Wardrobes() {
    }

    /** Loads the class, so its blocks and items are in MCA's registration maps. */
    public static void bootstrap() {
    }

    public static boolean enabled() {
        return Config.getInstance().villagerWardrobes;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(PENDING_CLOTHES, PENDING_SINCE, OWNED_CLOTHES);
    }

    /**
     * New clothes for a villager whose job or age changed: picked now, put on at the wardrobe. With the feature off
     * (or the clothes locked), they are put on at once as before.
     */
    public static void newClothes(VillagerEntityMCA villager) {
        if (!enabled() || villager.isClothingLocked() || !(villager.level() instanceof ServerLevel level)) {
            villager.randomizeClothes();
            return;
        }
        String clothes = ClothingList.getInstance().getPool(villager).pickOne();
        villager.setTrackedValue(PENDING_CLOTHES, clothes);
        addOwned(villager, clothes);
        villager.setTrackedValue(PENDING_SINCE, (int) (level.getGameTime() / DAY));
    }

    public static boolean hasPendingClothes(VillagerEntityMCA villager) {
        return !villager.getTrackedValue(PENDING_CLOTHES).isEmpty();
    }

    /** Puts the pending clothes on, with a rustle. */
    public static void change(VillagerEntityMCA villager) {
        String clothes = villager.getTrackedValue(PENDING_CLOTHES);
        if (clothes.isEmpty()) {
            return;
        }
        villager.setClothes(clothes);
        villager.setTrackedValue(PENDING_CLOTHES, "");
        villager.level().playSound(null, villager.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        villager.swing(InteractionHand.MAIN_HAND);
    }

    /** Waited a day without getting to a wardrobe: changes where it stands. */
    public static boolean waitedTooLong(ServerLevel level, VillagerEntityMCA villager) {
        return level.getGameTime() / DAY - villager.getTrackedValue(PENDING_SINCE) >= 1;
    }

    /** The wardrobe or dresser at the villager's home (or near it, without one), if any. */
    public static @Nullable BlockPos findWardrobe(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos around = villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                .filter(home -> home.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .orElse(villager.blockPosition());
        Optional<BlockPos> found = BlockPos.findClosestMatch(around, HOME_RADIUS, 4, pos -> level.getBlockState(pos).is(WARDROBES));
        return found.map(BlockPos::immutable).orElse(null);
    }

    public static boolean isNextToWardrobe(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos feet = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
            if (level.getBlockState(pos).is(WARDROBES)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player said yes to an outfit in a closet: puts it on, and the clothes taken off go into its place (none if
     * the player wore none).
     */
    public static void wearFrom(ServerPlayer player, BlockPos pos, int slot) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH_PLAYER * REACH_PLAYER
            || !(player.level().getBlockEntity(pos) instanceof ClosetBlockEntity closet)) {
            return;
        }
        ItemStack stored = closet.getItem(slot);
        if (!(stored.getItem() instanceof OutfitItem)) {
            return;
        }
        String wearing = dress(player, OutfitItem.clothes(stored));
        closet.setItem(slot, wearing.isEmpty() ? ItemStack.EMPTY : OutfitItem.of(OUTFIT, wearing));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** Puts clothes on the player's MCA look and tells everyone; gives back the clothes worn before. */
    static String dress(ServerPlayer player, String clothes) {
        PlayerSaveData data = PlayerSaveData.get(player);
        CompoundTag look = data.getEntityData();
        String wearing = look.getStringOr("Clothes", "");
        look.putString("Clothes", clothes);
        data.setEntityData(look);
        CompoundTag saved = data.getEntityData();
        player.level().players().forEach(p -> Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), saved), p));
        return wearing;
    }

    /**
     * The clothes a villager owns: six, drawn the first time they are asked for (what it wears among them). A new
     * outfit (a new job) takes the place of one of them.
     */
    public static List<String> ownedClothes(VillagerEntityMCA villager) {
        String owned = villager.getTrackedValue(OWNED_CLOTHES);
        if (owned.isEmpty()) {
            List<String> drawn = new ArrayList<>();
            if (!villager.getClothes().isEmpty()) {
                drawn.add(villager.getClothes());
            }
            WeightedPool<String> pool = ClothingList.getInstance().getPool(villager);
            for (int i = 0; drawn.size() < OWNED && i < OWNED * 4; i++) {
                String clothes = pool.pickOne();
                if (clothes != null && !clothes.isEmpty() && !drawn.contains(clothes)) {
                    drawn.add(clothes);
                }
            }
            villager.setTrackedValue(OWNED_CLOTHES, String.join(SEPARATOR, drawn));
            return drawn;
        }
        return new ArrayList<>(List.of(owned.split(java.util.regex.Pattern.quote(SEPARATOR))));
    }

    static void addOwned(VillagerEntityMCA villager, String clothes) {
        List<String> owned = ownedClothes(villager);
        if (owned.contains(clothes)) {
            return;
        }
        if (owned.size() >= OWNED) {
            owned.remove(villager.getRandom().nextInt(owned.size()));
        }
        owned.add(clothes);
        villager.setTrackedValue(OWNED_CLOTHES, String.join(SEPARATOR, owned));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerBlockEntityTypes(BlockEntityTypesMCA.TriFunction register) {
        List<Block> blocks = new ArrayList<>(WARDROBE_BLOCKS.values());
        blocks.addAll(DRESSER_BLOCKS.values());
        CLOSET = register.apply(MCA.locate("closet"), (BlockEntityTypesMCA.BlockEntitySupplier) ClosetBlockEntity::new, blocks.toArray(new Block[0]));
    }
}
