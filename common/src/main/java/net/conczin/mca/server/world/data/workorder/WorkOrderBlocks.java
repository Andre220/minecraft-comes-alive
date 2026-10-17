package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What still needs doing at a single position of a work order.
 */
public final class WorkOrderBlocks {
    private WorkOrderBlocks() {
    }

    public static boolean needsWork(BlockGetter level, WorkOrder order, BlockPos pos) {
        return switch (order.getType()) {
            case DIG -> needsDigging(level, pos);
            case BUILD -> order.getBlock() != null && needsBuilding(level, pos, order.getBlock());
        };
    }

    /**
     * Anything solid and breakable is dug. Fluids are left alone, and so are blocks with block
     * entities: chests, beds, signs and the like are never destroyed by a planned job. Blocks
     * holding back lava stay too, nobody wants to flood the village with it.
     */
    public static boolean needsDigging(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return false;
            }
        }
        return true;
    }

    /** Only replaceable spots (air, water, grass, snow...) are filled; other blocks are never overwritten. */
    public static boolean needsBuilding(BlockGetter level, BlockPos pos, BlockState target) {
        BlockState state = level.getBlockState(pos);
        if (state.is(target.getBlock())) {
            return false;
        }
        return state.canBeReplaced();
    }

    /** A block placed here would touch something solid, like a player placing it would need. */
    public static boolean isSupported(BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (!neighbour.isAir() && !neighbour.canBeReplaced()) {
                return true;
            }
        }
        return false;
    }
}
