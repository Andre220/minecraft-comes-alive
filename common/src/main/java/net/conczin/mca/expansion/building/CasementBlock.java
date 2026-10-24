package net.conczin.mca.expansion.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A casement window: two leaves side by side in the window opening that swing outwards like a gate, away from
 * whoever opens them. Villagers open them in the morning and close them in the evening.
 */
public class CasementBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Only used to look up the model of one leaf for the renderer; always NONE in the world. */
    public static final EnumProperty<Leaf> LEAF = EnumProperty.create("leaf", Leaf.class);
    /** In the middle of the block instead of flush with the outer face of the wall. */
    public static final BooleanProperty CENTERED = BooleanProperty.create("centered");

    public enum Leaf implements StringRepresentable {
        NONE, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // flush with the outer face of the wall (facing points outwards)
    private static final VoxelShape CLOSED_SOUTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape CLOSED_NORTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape CLOSED_EAST = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape CLOSED_WEST = Block.box(0, 0, 0, 2, 16, 16);

    private final WoodType wood;

    public CasementBlock(WoodType wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(OPEN, false).setValue(LEAF, Leaf.NONE)
                .setValue(CENTERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LEAF, CENTERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwingBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // drawn by the swing renderer
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // placed from inside, looking out: that way is outside, where it opens to
        Direction facing = context.getHorizontalDirection();
        // clicked near the middle of the block (from inside to outside): in the middle; else flush with the outside
        double along = facing.getAxis().choose(
                context.getClickLocation().x - context.getClickedPos().getX(), 0.0,
                context.getClickLocation().z - context.getClickedPos().getZ());
        double outwards = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1.0 - along;
        boolean centered = outwards > 0.25 && outwards < 0.75;
        return defaultBlockState().setValue(FACING, facing).setValue(CENTERED, centered);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(CENTERED)) {
            boolean alongZ = facing.getAxis() == Direction.Axis.Z;
            if (!state.getValue(OPEN)) {
                return alongZ ? Block.box(0, 0, 7, 16, 16, 9) : Block.box(7, 0, 0, 9, 16, 16);
            }
            return alongZ ? Shapes.or(Block.box(0, 0, 7, 2, 16, 9), Block.box(14, 0, 7, 16, 16, 9))
                    : Shapes.or(Block.box(7, 0, 0, 9, 16, 2), Block.box(7, 0, 14, 9, 16, 16));
        }
        if (!state.getValue(OPEN)) {
            return switch (facing) {
                case NORTH -> CLOSED_NORTH;
                case EAST -> CLOSED_EAST;
                case WEST -> CLOSED_WEST;
                default -> CLOSED_SOUTH;
            };
        }
        // open, the leaves stand outside the wall; only their hinges are left in here
        return switch (facing) {
            case NORTH -> Shapes.or(Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2));
            case EAST -> Shapes.or(Block.box(14, 0, 0, 16, 16, 2), Block.box(14, 0, 14, 16, 16, 16));
            case WEST -> Shapes.or(Block.box(0, 0, 0, 2, 16, 2), Block.box(0, 0, 14, 2, 16, 16));
            default -> Shapes.or(Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16));
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(player, pos, open ? wood.fenceGateOpen() : wood.fenceGateClose(), SoundSource.BLOCKS, 1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F);
        return InteractionResult.SUCCESS;
    }

    /** Never a solid face for others: glass panes, fences and walls don't join onto it. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
