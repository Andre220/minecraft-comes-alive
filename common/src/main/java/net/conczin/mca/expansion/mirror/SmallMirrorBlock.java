package net.conczin.mca.expansion.mirror;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A small mirror on a foot, put on a table, a dresser, a counter: anything with something to stand on at its top. On
 * a low top (a slab, a low table) it sits down into the block below.
 */
public class SmallMirrorBlock extends MirrorBlock {
    /** Standing on a low top: drawn 8 px lower. */
    public static final BooleanProperty LOW = BooleanProperty.create("low");
    private static final double LOW_DROP = 8.0;
    /** How high a top is still low. */
    private static final double LOW_TOP = 0.5 + 1.0E-4;
    private static final float[] GLASS = {4, 3, 12, 13, 8.25F};
    private static final float[] GLASS_LOW = {4, 3 - 8, 12, 13 - 8, 8.25F};

    public SmallMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LOW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOW);
    }

    @Override
    protected boolean holdsGlass(BlockState state) {
        return true;
    }

    @Override
    protected BlockPos glassPos(BlockPos pos, BlockState state) {
        return pos;
    }

    @Override
    public float[] glass(BlockState state) {
        return state.getValue(LOW) ? GLASS_LOW : GLASS;
    }

    /** How high the top below reaches, 0 to 1, or -1 with nothing to stand on. */
    private static double topBelow(BlockGetter level, BlockPos pos) {
        VoxelShape below = level.getBlockState(pos.below()).getCollisionShape(level, pos.below());
        return below.isEmpty() ? -1.0 : below.max(Direction.Axis.Y);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return topBelow(level, pos) > 0.0;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        double top = topBelow(context.getLevel(), context.getClickedPos());
        return state == null || top <= 0.0 ? null : state.setValue(LOW, top <= LOW_TOP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction != Direction.DOWN) {
            return state;
        }
        double top = topBelow(level, pos);
        return top <= 0.0 ? Blocks.AIR.defaultBlockState() : state.setValue(LOW, top <= LOW_TOP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double drop = state.getValue(LOW) ? LOW_DROP : 0.0;
        return box(state.getValue(FACING), 3, -drop, 6, 13, 14 - drop, 10);
    }
}
