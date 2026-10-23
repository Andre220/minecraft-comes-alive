package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

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
            case BLUEPRINT -> order.getPlan() != null && needsBlueprint(level, pos, order.getPlan().get(pos), order.isFree());
        };
    }

    /**
     * A blueprint position needs work while it holds another block than planned: something to
     * clear where the blueprint has air, or the planned block is still missing. Only the block
     * counts, not its exact state, so stairs that connected differently still count as done.
     * Blocks nobody can carry (fluids, fire...) are left out unless the order is free.
     */
    public static boolean needsBlueprint(BlockGetter level, BlockPos pos, @Nullable BlockState target, boolean free) {
        if (target == null) {
            return false;
        }
        if (target.isAir()) {
            return needsDigging(level, pos);
        }
        if (!free && !isObtainable(target)) {
            return false;
        }
        return !level.getBlockState(pos).is(target.getBlock());
    }

    /** Something else stands where the blueprint wants a different block: dig it out first. */
    public static boolean mustClear(BlockGetter level, BlockPos pos, BlockState target) {
        BlockState current = level.getBlockState(pos);
        return !current.isAir() && !current.canBeReplaced() && !current.is(target.getBlock()) && needsDigging(level, pos);
    }

    /** Builders can carry the item of this block from the storage. */
    public static boolean isObtainable(BlockState state) {
        return state.getBlock().asItem() != Items.AIR && !(state.getBlock() instanceof LiquidBlock);
    }

    /** The upper half of a door or tall plant, the head of a bed: placed together with its other half. */
    public static boolean isSecondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            return state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
        }
        return false;
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
