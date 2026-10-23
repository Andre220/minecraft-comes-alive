package net.conczin.mca.expansion.building;

import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockSetType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Window shutters, one per wood, built from that wood's trapdoor. */
public final class Shutters {
    public static final Map<String, Block> SHUTTERS = new LinkedHashMap<>();
    /** Casement windows: two leaves swinging outwards like a gate. */
    public static final Map<String, Block> CASEMENTS = new LinkedHashMap<>();
    /** Remembers when a shutter or casement last swung, for its animation. */
    public static BlockEntityType<SwingBlockEntity> SWING;
    /** Where a closed shutter's panel lies; takes the clicks. */
    public static final Block LEAF = BlocksMCA.register("shutter_leaf",
            new ShutterLeafBlock(BlocksMCA.baseProps("shutter_leaf", Blocks.OAK_TRAPDOOR).noOcclusion().noLootTable()));

    static {
        shutter("oak", Blocks.OAK_TRAPDOOR, BlockSetType.OAK, WoodType.OAK);
        shutter("spruce", Blocks.SPRUCE_TRAPDOOR, BlockSetType.SPRUCE, WoodType.SPRUCE);
        shutter("birch", Blocks.BIRCH_TRAPDOOR, BlockSetType.BIRCH, WoodType.BIRCH);
        shutter("jungle", Blocks.JUNGLE_TRAPDOOR, BlockSetType.JUNGLE, WoodType.JUNGLE);
        shutter("acacia", Blocks.ACACIA_TRAPDOOR, BlockSetType.ACACIA, WoodType.ACACIA);
        shutter("dark_oak", Blocks.DARK_OAK_TRAPDOOR, BlockSetType.DARK_OAK, WoodType.DARK_OAK);
        shutter("mangrove", Blocks.MANGROVE_TRAPDOOR, BlockSetType.MANGROVE, WoodType.MANGROVE);
        shutter("cherry", Blocks.CHERRY_TRAPDOOR, BlockSetType.CHERRY, WoodType.CHERRY);
        shutter("pale_oak", Blocks.PALE_OAK_TRAPDOOR, BlockSetType.PALE_OAK, WoodType.PALE_OAK);
        shutter("bamboo", Blocks.BAMBOO_TRAPDOOR, BlockSetType.BAMBOO, WoodType.BAMBOO);
        shutter("crimson", Blocks.CRIMSON_TRAPDOOR, BlockSetType.CRIMSON, WoodType.CRIMSON);
        shutter("warped", Blocks.WARPED_TRAPDOOR, BlockSetType.WARPED, WoodType.WARPED);
    }

    private Shutters() {
    }

    private static void shutter(String wood, Block trapdoor, BlockSetType type, WoodType woodType) {
        String casement = wood + "_casement";
        Block window = BlocksMCA.register(casement, new CasementBlock(woodType, BlocksMCA.baseProps(casement, trapdoor).noOcclusion()));
        ItemsMCA.register(casement, new BlockItem(window, ItemsMCA.blockProps(casement)));
        CASEMENTS.put(wood, window);

        String name = wood + "_shutter";
        Block block = BlocksMCA.register(name, new ShutterBlock(type, BlocksMCA.baseProps(name, trapdoor).noOcclusion()));
        ItemsMCA.register(name, new BlockItem(block, ItemsMCA.blockProps(name)));
        SHUTTERS.put(wood, block);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerBlockEntityTypes(BlockEntityTypesMCA.TriFunction register) {
        java.util.List<Block> blocks = new java.util.ArrayList<>(SHUTTERS.values());
        blocks.addAll(CASEMENTS.values());
        SWING = register.apply(MCA.locate("swing"), (BlockEntityTypesMCA.BlockEntitySupplier) SwingBlockEntity::new, blocks.toArray(new Block[0]));
    }

    /** Loads the class, so the shutters are in MCA's registration maps. */
    public static void bootstrap() {
    }
}
