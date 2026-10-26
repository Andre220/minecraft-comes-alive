package net.conczin.mca.expansion.mirror;

import net.conczin.mca.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A mirror: a pane of glass in a wooden frame, its front facing {@link #FACING}. A click from in front of it opens the
 * hair editor. One of its blocks holds the glass (and the block entity the reflection is drawn for).
 * <p>
 * Measures are in pixels for a mirror facing south (front at +z), x running from the left to the right of someone
 * looking into it, and turned to the real facing.
 */
public abstract class MirrorBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MirrorBlock(Properties properties) {
        super(properties);
    }

    /** Whether this block holds the glass, or the one below does. */
    protected abstract boolean holdsGlass(BlockState state);

    /** The block holding this mirror's glass. */
    protected abstract BlockPos glassPos(BlockPos pos, BlockState state);

    /** The glass's front, relative to the block holding it: {x0, y0, x1, y1, z} in pixels. */
    public abstract float[] glass(BlockState state);

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return holdsGlass(state) ? new MirrorBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!Mirrors.enabled()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos at = glassPos(pos, state);
            if (Mirrors.canLookInto(player, at, level.getBlockState(at))) {
                Network.sendToPlayer(new MirrorMessages.Look(), serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The way the glass faces, out of the mirror. */
    public static Vec3 normal(BlockState state) {
        Direction facing = state.getValue(FACING);
        return new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
    }

    /** A point given in pixels for a mirror facing south, in the world. */
    public static Vec3 toWorld(BlockPos pos, Direction facing, double x, double y, double z) {
        Vec3 turned = switch (facing) {
            case NORTH -> new Vec3(16 - x, y, 16 - z);
            case EAST -> new Vec3(z, y, 16 - x);
            case WEST -> new Vec3(16 - z, y, x);
            default -> new Vec3(x, y, z);
        };
        return turned.scale(1.0 / 16.0).add(pos.getX(), pos.getY(), pos.getZ());
    }

    public Vec3 glassCenter(BlockPos glassPos, BlockState state) {
        float[] glass = glass(state);
        return toWorld(glassPos, state.getValue(FACING), (glass[0] + glass[2]) / 2, (glass[1] + glass[3]) / 2, glass[4]);
    }

    /** A box given in pixels for a mirror facing south, turned to the facing. */
    protected static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case NORTH -> px(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> px(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> px(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> px(x0, y0, z0, x1, y1, z1);
        };
    }

    private static VoxelShape px(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
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
