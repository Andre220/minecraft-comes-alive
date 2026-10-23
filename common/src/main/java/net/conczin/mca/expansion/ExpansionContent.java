package net.conczin.mca.expansion;

import net.conczin.mca.expansion.building.Shutters;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.conczin.mca.expansion.requests.RequestBoardBlock;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Blocks and items of the expansions that are not part of one expansion's own registry, and the
 * single bootstrap MCA's registries call to load all of them.
 */
public final class ExpansionContent {
    public static final Block REQUEST_BOARD = BlocksMCA.register("request_board",
            new RequestBoardBlock(BlocksMCA.baseProps("request_board", Blocks.OAK_PLANKS)));

    static {
        ItemsMCA.register("request_board", new BlockItem(REQUEST_BOARD, ItemsMCA.blockProps("request_board")));
    }

    private ExpansionContent() {
    }

    /** Loads every expansion's blocks, items and professions into MCA's registration maps. */
    public static void bootstrap() {
        EconomyRegistry.bootstrap();
        Shutters.bootstrap();
        ExpansionGuide.bootstrap();
    }
}
