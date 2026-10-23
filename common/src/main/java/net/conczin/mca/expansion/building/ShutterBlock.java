package net.conczin.mca.expansion.building;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A window shutter: hung on the outside wall beside a window, it swings on its hinge like a door.
 * Open, it lies flat against the wall; closed, it folds over and covers the window next to it.
 * Stacked shutters open and close together, and villagers open them in the morning and close them
 * in the evening (see {@code data/mca/daily_toggles/shutters.json}).
 */
public class ShutterBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How far a column of shutters is opened or closed together. */
    private static final int COLUMN = 4;

    private static final Map<Direction, VoxelShape> AGAINST_WALL = new EnumMap<>(Direction.class);

    static {
        // facing is where the shutter looks to: the wall is behind it
        AGAINST_WALL.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 2));
        AGAINST_WALL.put(Direction.NORTH, Block.box(0, 0, 14, 16, 16, 16));
        AGAINST_WALL.put(Direction.EAST, Block.box(0, 0, 0, 2, 16, 16));
        AGAINST_WALL.put(Direction.WEST, Block.box(14, 0, 0, 16, 16, 16));
    }

    private final BlockSetType type;

    public ShutterBlock(BlockSetType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(HINGE, DoorHingeSide.RIGHT)
                .setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, OPEN);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwingBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // drawn swinging by SwingRenderer
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace()
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(HINGE, hingeFor(context, facing));
    }

    /** The side the window is on: where the wall behind has an opening, else the side clicked. */
    private static DoorHingeSide hingeFor(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos wall = context.getClickedPos().relative(facing.getOpposite());
        Direction right = facing.getCounterClockWise();
        boolean openingRight = isOpening(level, wall.relative(right));
        boolean openingLeft = isOpening(level, wall.relative(right.getOpposite()));
        if (openingRight != openingLeft) {
            return openingRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
        }
        BlockPos pos = context.getClickedPos();
        double x = context.getClickLocation().x - pos.getX();
        double z = context.getClickLocation().z - pos.getZ();
        double along = right.getStepX() * (x - 0.5) + right.getStepZ() * (z - 0.5);
        return along > 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
    }

    private static boolean isOpening(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.canOcclude();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        toggle(level, pos, state, player);
        return InteractionResult.SUCCESS;
    }

    /** Opens or closes the shutter and the ones stacked with it. */
    public void toggle(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        boolean open = !state.getValue(OPEN);
        setColumn(level, pos, state, open);
        SoundEvent sound = open ? type.trapdoorOpen() : type.trapdoorClose();
        level.playSound(player, pos, sound, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /** The side the window is on, where the closed panel goes. */
    public static Direction hingeDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(HINGE) == DoorHingeSide.RIGHT ? facing.getCounterClockWise() : facing.getClockWise();
    }

    /**
     * Closed, the panel lies in front of the window next door: a leaf block there takes the clicks (like the top
     * half of a door). Runs on every change of the shutter, so opening and closing by villagers keeps it too.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level.isClientSide()) {
            return;
        }
        BlockPos leafPos = pos.relative(hingeDirection(state));
        BlockState there = level.getBlockState(leafPos);
        if (!state.getValue(OPEN)) {
            if (there.canBeReplaced() && there.getFluidState().isEmpty() && !(there.getBlock() instanceof ShutterLeafBlock)) {
                level.setBlock(leafPos, Shutters.LEAF.defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(HINGE, state.getValue(HINGE)),
                        Block.UPDATE_ALL);
            }
        } else if (there.getBlock() instanceof ShutterLeafBlock) {
            level.removeBlock(leafPos, false);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (!(level.getBlockState(pos).getBlock() instanceof ShutterBlock)) {
            BlockPos leafPos = pos.relative(hingeDirection(state));
            if (level.getBlockState(leafPos).getBlock() instanceof ShutterLeafBlock) {
                level.removeBlock(leafPos, false);
            }
        }
    }

    /** Opens or closes this shutter and the ones stacked with it (same wall, same side). */
    private void setColumn(Level level, BlockPos pos, BlockState state, boolean open) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos other = pos;
            for (int i = 0; i < COLUMN; i++) {
                other = other.relative(direction);
                BlockState neighbour = level.getBlockState(other);
                if (!(neighbour.getBlock() instanceof ShutterBlock)
                    || neighbour.getValue(FACING) != state.getValue(FACING)
                    || neighbour.getValue(HINGE) != state.getValue(HINGE)) {
                    break;
                }
                level.setBlock(other, neighbour.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // closed, the panel lies in front of the window next door; what's left here is the hinge side
        return AGAINST_WALL.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? AGAINST_WALL.get(state.getValue(FACING)) : net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(HINGE);
    }
}
