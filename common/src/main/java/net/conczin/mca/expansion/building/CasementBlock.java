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

    public enum Leaf implements StringRepresentable {
        NONE, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final VoxelShape CLOSED_Z = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape CLOSED_X = Block.box(7, 0, 0, 9, 16, 16);

    private final WoodType wood;

    public CasementBlock(WoodType wood, Properties properties) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(OPEN, false).setValue(LEAF, Leaf.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LEAF);
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
        // opens away from whoever placed it: from inside, outwards
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(OPEN)) {
            return facing.getAxis() == Direction.Axis.Z ? CLOSED_Z : CLOSED_X;
        }
        return switch (facing) {
            case NORTH -> Shapes.or(Block.box(0, 0, 0, 2, 16, 8), Block.box(14, 0, 0, 16, 16, 8));
            case EAST -> Shapes.or(Block.box(8, 0, 0, 16, 16, 2), Block.box(8, 0, 14, 16, 16, 16));
            case WEST -> Shapes.or(Block.box(0, 0, 0, 8, 16, 2), Block.box(0, 0, 14, 8, 16, 16));
            default -> Shapes.or(Block.box(0, 0, 8, 2, 16, 16), Block.box(14, 0, 8, 16, 16, 16));
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean open = !state.getValue(OPEN);
        BlockState next = state.setValue(OPEN, open);
        // like a gate: it swings away from the one opening it
        if (open && player.getDirection() == state.getValue(FACING).getOpposite()) {
            next = next.setValue(FACING, player.getDirection());
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(player, pos, open ? wood.fenceGateOpen() : wood.fenceGateClose(), SoundSource.BLOCKS, 1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F);
        return InteractionResult.SUCCESS;
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
