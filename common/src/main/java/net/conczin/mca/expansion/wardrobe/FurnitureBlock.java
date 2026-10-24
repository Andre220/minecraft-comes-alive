package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Furniture of several blocks, put down as one piece: a wardrobe (two tall) or a dresser (two wide). Each block knows
 * its part; breaking one breaks the whole piece, which drops from its first part.
 * <p>
 * Its doors or drawers open with a click and show the outfits inside. With one open, a click on an outfit asks
 * whether to wear it, an outfit in hand goes into an empty place, a sneaking click with an empty hand takes one out,
 * and a click on an empty place shuts it again.
 */
public abstract class FurnitureBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** What a block state's model draws: the body in the world; a door or drawer only for the closet renderer. */
    public enum Piece implements StringRepresentable {
        BODY, DOOR, DRAWER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Where a block sits in the piece, seen from the front: to the right and up from the first part. */
    public enum Part implements StringRepresentable {
        BOTTOM_LEFT(0, 0), BOTTOM_RIGHT(1, 0), TOP_LEFT(0, 1), TOP_RIGHT(1, 1);

        final int right;
        final int up;

        Part(int right, int up) {
            this.right = right;
            this.up = up;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    protected FurnitureBlock(Properties properties) {
        super(properties);
    }

    protected abstract EnumProperty<Part> partProperty();

    protected abstract List<Part> parts();

    /** How many outfits one closet block holds. */
    public abstract int slots();

    /** Whether this block holds the closet (its outfits and doors), or a neighbour in the piece does. */
    protected abstract boolean holdsCloset(BlockState state);

    /** The block holding the closet this block belongs to. */
    protected abstract BlockPos closetPos(BlockPos pos, BlockState state);

    /** The door or drawer at a point given for a piece facing south, relative to the closet block. */
    protected abstract int compartmentAt(double x, double y);

    /** The outfit's place at that point. */
    protected abstract int slotAt(double x, double y);

    protected abstract SoundEvent openSound();

    protected abstract SoundEvent closeSound();

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return holdsCloset(state) ? new ClosetBlockEntity(pos, state) : null;
    }

    /** The player's right, looking at the front. */
    protected static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The state with how it joins its neighbours, for furniture that joins up (wardrobes side by side). */
    protected BlockState connect(LevelReader level, BlockPos pos, BlockState state) {
        return state;
    }

    private BlockPos origin(BlockPos pos, BlockState state) {
        Part part = state.getValue(partProperty());
        return pos.below(part.up).relative(right(state.getValue(FACING)), -part.right);
    }

    private static BlockPos at(BlockPos origin, Direction facing, Part part) {
        return origin.above(part.up).relative(right(facing), part.right);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        for (Part part : parts()) {
            BlockPos pos = at(context.getClickedPos(), facing, part);
            if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        return connect(level, context.getClickedPos(), defaultBlockState().setValue(FACING, facing).setValue(partProperty(), Part.BOTTOM_LEFT));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (Part part : parts()) {
            if (part != Part.BOTTOM_LEFT) {
                BlockPos at = at(pos, facing, part);
                level.setBlock(at, connect(level, at, state.setValue(partProperty(), part)), Block.UPDATE_ALL);
            }
        }
    }

    /** A part whose neighbour in the piece is gone goes too, so the piece always breaks whole. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        BlockPos origin = origin(pos, state);
        Direction facing = state.getValue(FACING);
        for (Part part : parts()) {
            if (at(origin, facing, part).equals(neighborPos)
                && !(neighborState.is(this) && neighborState.getValue(FACING) == facing && neighborState.getValue(partProperty()) == part)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return connect(level, pos, super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random));
    }

    /** In creative, nothing drops: the first part (which drops the piece) is taken away quietly first. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(partProperty()) != Part.BOTTOM_LEFT) {
            BlockPos origin = origin(pos, state);
            if (level.getBlockState(origin).is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return use(state, level, pos, player, ItemStack.EMPTY, hitResult);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof OutfitItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return use(state, level, pos, player, stack, hitResult);
    }

    private InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, ItemStack held, BlockHitResult hitResult) {
        BlockPos closetPos = closetPos(pos, state);
        if (!(level.getBlockEntity(closetPos) instanceof ClosetBlockEntity closet)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 hit = hitResult.getLocation().subtract(closetPos.getX(), closetPos.getY(), closetPos.getZ());
        Vec3 local = toSouth(hit, state.getValue(FACING));
        int compartment = compartmentAt(local.x, local.y);
        int slot = slotAt(local.x, local.y);
        ItemStack stored = closet.getItem(slot);
        // standing out in front: the open door, or an open drawer's front (from above, a drawer is for its outfit)
        boolean onDoor = local.z > 1.0 + 1.0E-3 && hitResult.getDirection() != Direction.UP;
        if (onDoor && closet.isOpen(compartment)) {
            closet.setOpen(compartment, false);
            level.playSound(null, pos, closeSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        } else if (!closet.isOpen(compartment)) {
            closet.setOpen(compartment, true);
            level.playSound(null, pos, openSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        } else if (held.getItem() instanceof OutfitItem && stored.isEmpty()) {
            closet.setItem(slot, held.copyWithCount(1));
            held.consume(1, player);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.8F, 1.0F);
        } else if (!stored.isEmpty() && held.isEmpty() && player.isShiftKeyDown()) {
            player.getInventory().placeItemBackInInventory(closet.takeItem(slot));
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
        } else if (!stored.isEmpty()) {
            Network.sendToPlayer(new OutfitMessages.Choose(closetPos, slot), serverPlayer);
        } else {
            closet.setOpen(compartment, false);
            level.playSound(null, pos, closeSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /** A point in a block turned back to how it would be for a piece facing south (x from the left, z to the front). */
    static Vec3 toSouth(Vec3 point, Direction facing) {
        return switch (facing) {
            case WEST -> new Vec3(point.z, point.y, 1.0 - point.x);
            case NORTH -> new Vec3(1.0 - point.x, point.y, 1.0 - point.z);
            case EAST -> new Vec3(1.0 - point.z, point.y, point.x);
            default -> point;
        };
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** A box given for a piece facing south (front at +z), turned to the facing. */
    protected static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case NORTH -> px(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> px(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> px(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> px(x0, y0, z0, x1, y1, z1);
        };
    }

    /** A box in pixels, which may stand out of the block (an open door does). */
    private static VoxelShape px(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
    }

    /** The furniture itself, without its doors or drawers standing open: what bumps into things. */
    protected abstract VoxelShape bodyShape(BlockState state);

    /** Its open doors and drawers, standing out in front: only there to be clicked. */
    protected abstract VoxelShape openShape(BlockState state, ClosetBlockEntity closet);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape body = bodyShape(state);
        if (level.getBlockEntity(closetPos(pos, state)) instanceof ClosetBlockEntity closet) {
            return Shapes.or(body, openShape(state, closet));
        }
        return body;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return bodyShape(state);
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
