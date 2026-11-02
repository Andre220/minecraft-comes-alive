package net.conczin.mca.client.gui.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns the block picked in the catalog into the state a player would place: stairs face away from the camera and sit
 * on the half of the face that was clicked, logs follow the clicked face. The player can then turn it ({@code turns}
 * quarter turns) and flip it upside down.
 */
final class PlanningPlacement {
    private PlanningPlacement() {
    }

    /**
     * The state for {@code block} placed against the hit face, as seen from a camera turned {@code yaw}/{@code pitch}.
     * Falls back to the default state when the block refuses (it would not fit there in the world as it is).
     */
    static BlockState place(Block block, Player player, BlockHitResult hit, float yaw, float pitch, int turns, boolean flipped) {
        BlockPlaceContext context = new CameraPlaceContext(player, new ItemStack(block.asItem()), hit, yaw, pitch);
        BlockState state = null;
        try {
            state = block.getStateForPlacement(context);
        } catch (RuntimeException ignored) {
            // some blocks look at the world around in ways a planning camera cannot satisfy
        }
        if (state == null) {
            state = block.defaultBlockState();
        }
        return adjust(state, turns, flipped);
    }

    /** Turns a state by quarter turns and flips it upside down when asked. */
    static BlockState adjust(BlockState state, int turns, boolean flipped) {
        BlockState adjusted = state.rotate(Rotation.values()[Math.floorMod(turns, 4)]);
        return flipped ? flip(adjusted) : adjusted;
    }

    /** Stairs and trapdoors to the other half, slabs to the other side; anything else stays. */
    static BlockState flip(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HALF)) {
            return state.setValue(BlockStateProperties.HALF, state.getValue(BlockStateProperties.HALF) == Half.TOP ? Half.BOTTOM : Half.TOP);
        }
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            SlabType type = state.getValue(BlockStateProperties.SLAB_TYPE);
            return type == SlabType.DOUBLE ? state : state.setValue(BlockStateProperties.SLAB_TYPE, type == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            if (facing.getAxis() == Direction.Axis.Y) {
                return state.setValue(BlockStateProperties.FACING, facing.getOpposite());
            }
        }
        return state;
    }

    /**
     * A block and the other half that comes with it: the upper half of doors and tall plants, the head of a bed.
     * Builders place both together, but the plan needs to know about the second one.
     */
    static Map<BlockPos, BlockState> withSecondHalf(BlockPos pos, BlockState state) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(pos, state);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            blocks.put(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        } else if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            blocks.put(pos.relative(state.getValue(BedBlock.FACING)), state.setValue(BedBlock.PART, BedPart.HEAD));
        }
        return blocks;
    }

    /** A place context that looks from the planning camera instead of from the player's head. */
    private static final class CameraPlaceContext extends BlockPlaceContext {
        private final float yaw;
        private final float pitch;

        CameraPlaceContext(Player player, ItemStack stack, BlockHitResult hit, float yaw, float pitch) {
            super(player, InteractionHand.MAIN_HAND, stack, hit);
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public Direction getHorizontalDirection() {
            return Direction.fromYRot(yaw);
        }

        @Override
        public float getRotation() {
            return yaw;
        }

        @Override
        public Direction getNearestLookingDirection() {
            return nearest()[0];
        }

        @Override
        public Direction getNearestLookingVerticalDirection() {
            return pitch < 0 ? Direction.UP : Direction.DOWN;
        }

        @Override
        public Direction[] getNearestLookingDirections() {
            Direction[] directions = nearest();
            if (!replacingClickedOnBlock()) {
                Direction face = getClickedFace();
                // like the game: the direction into the clicked face comes first
                int index = 0;
                while (index < directions.length && directions[index] != face.getOpposite()) {
                    index++;
                }
                if (index > 0 && index < directions.length) {
                    System.arraycopy(directions, 0, directions, 1, index);
                    directions[0] = face.getOpposite();
                }
            }
            return directions;
        }

        /** All directions, the one the camera looks along most first. */
        private Direction[] nearest() {
            Vec3 view = Vec3.directionFromRotation(pitch, yaw);
            return java.util.Arrays.stream(Direction.values())
                    .sorted(java.util.Comparator.comparingDouble(direction ->
                            -(direction.getStepX() * view.x + direction.getStepY() * view.y + direction.getStepZ() * view.z)))
                    .toArray(Direction[]::new);
        }

        @Override
        public boolean isSecondaryUseActive() {
            return false;
        }
    }
}
