package net.conczin.mca.expansion.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where a closed shutter's panel lies, in front of the window next to it: invisible (the shutter draws the panel)
 * but there to be clicked, like the top half of a door. Using it opens the shutter again.
 */
public class ShutterLeafBlock extends Block {
    private static final Map<Direction, VoxelShape> PANEL = new EnumMap<>(Direction.class);

    static {
        PANEL.put(Direction.SOUTH, Block.box(0, 0, 2, 16, 16, 4));
        PANEL.put(Direction.NORTH, Block.box(0, 0, 12, 16, 16, 14));
        PANEL.put(Direction.EAST, Block.box(2, 0, 0, 4, 16, 16));
        PANEL.put(Direction.WEST, Block.box(12, 0, 0, 14, 16, 16));
    }

    public ShutterLeafBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ShutterBlock.FACING, Direction.SOUTH).setValue(ShutterBlock.HINGE, DoorHingeSide.RIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ShutterBlock.FACING, ShutterBlock.HINGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PANEL.get(state.getValue(ShutterBlock.FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Never a solid face for others: glass panes, fences and walls don't join onto it. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** The shutter this panel belongs to. */
    static BlockPos shutterOf(BlockState state, BlockPos pos) {
        return pos.relative(ShutterBlock.hingeDirection(state).getOpposite());
    }

    private static boolean hasClosedShutter(BlockState state, LevelReader level, BlockPos pos) {
        BlockState shutter = level.getBlockState(shutterOf(state, pos));
        return shutter.getBlock() instanceof ShutterBlock && !shutter.getValue(ShutterBlock.OPEN)
               && shutter.getValue(ShutterBlock.FACING) == state.getValue(ShutterBlock.FACING)
               && shutter.getValue(ShutterBlock.HINGE) == state.getValue(ShutterBlock.HINGE);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasClosedShutter(state, level, pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return hasClosedShutter(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos shutterPos = shutterOf(state, pos);
        BlockState shutter = level.getBlockState(shutterPos);
        if (shutter.getBlock() instanceof ShutterBlock block) {
            block.toggle(level, shutterPos, shutter, player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // breaking the panel just opens the shutter
        BlockPos shutterPos = shutterOf(state, pos);
        BlockState shutter = level.getBlockState(shutterPos);
        if (!level.isClientSide() && shutter.getBlock() instanceof ShutterBlock block && !shutter.getValue(ShutterBlock.OPEN)) {
            block.toggle(level, shutterPos, shutter, null);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
