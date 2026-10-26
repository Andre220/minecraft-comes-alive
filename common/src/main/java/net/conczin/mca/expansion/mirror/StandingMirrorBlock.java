package net.conczin.mca.expansion.mirror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A standing mirror, two blocks tall, on the floor. Its lower block holds the glass and drops the mirror. */
public class StandingMirrorBlock extends MirrorBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** The glass runs up from the lower block into the upper one. */
    private static final float[] GLASS = {2.5F, 3, 13.5F, 30, 8.25F};

    public StandingMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected boolean holdsGlass(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockPos glassPos(BlockPos pos, BlockState state) {
        return holdsGlass(state) ? pos : pos.below();
    }

    @Override
    public float[] glass(BlockState state) {
        return GLASS;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        Level level = context.getLevel();
        if (above.getY() >= level.getMaxY() || !level.getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        boolean lower = holdsGlass(state);
        if (direction == (lower ? Direction.UP : Direction.DOWN)
            && !(neighborState.is(this) && neighborState.getValue(HALF) != state.getValue(HALF) && neighborState.getValue(FACING) == state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** In creative, nothing drops: the lower half (which drops the mirror) is taken away quietly first. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && !holdsGlass(state)) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return holdsGlass(state) ? box(facing, 1, 0, 5, 15, 16, 11) : box(facing, 1, 0, 6.5, 15, 15.5, 9.5);
    }
}
