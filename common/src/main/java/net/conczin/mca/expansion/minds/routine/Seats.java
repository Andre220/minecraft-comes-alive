package net.conczin.mca.expansion.minds.routine;

import net.minecraft.core.Direction;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Sitting for real: stairs and bottom slabs are benches, and chairs of other mods in the {@link #SEATS} tag (facing the
 * way their {@code facing} property says). The villager rides an invisible display entity placed on the seat
 * (displays have no size, so it sits right there) and MCA's model draws the riding pose.
 */
public final class Seats {
    public static final String TAG = "mca_seat";
    /** Chairs and the like: seats facing the way of their horizontal {@code facing}. */
    public static final TagKey<Block> SEATS = TagKey.create(Registries.BLOCK, MCA.locate("seats"));
    /** Height of the seat surface above the block's bottom for stairs and bottom slabs. */
    private static final double SEAT_HEIGHT = 0.5;
    /** Puts the bottom of the rider's torso on the seat surface (measured in game). */
    private static final double SIT_OFFSET = 0.4;
    /**
     * How far onto the low half of a stair a villager sits: the back of its torso (4 pixels deep) against the step,
     * so the bottom of the torso sits in the stair's corner.
     */
    private static final double STAIR_FORWARD = 0.125;
    private static final double CLEANUP_RANGE = 32.0;
    /** How far a villager sitting on a stair turns its head to either side, in degrees. */
    private static final float STAIR_HEAD_TURN = 60.0F;

    private Seats() {
    }

    /** Whether a block is something to sit on: bottom stairs or a bottom slab with room above. */
    public static boolean isSeat(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean seat = state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM
                       || state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                       || isChair(state);
        return seat
               && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
               && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty()
               && onTheFloor(level, pos, state)
               && !isTaken(level, pos);
    }

    /**
     * A real seat stands on the floor with room to step up to it: firm ground under it and somewhere to stand in
     * front (beside, for a slab). Stairs and slabs of roofs and steps have neither.
     */
    private static boolean onTheFloor(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        Direction front = frontOf(state);
        if (front != null) {
            return OpportunityScanner.canStand(level, pos.relative(front));
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (OpportunityScanner.canStand(level, pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTaken(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(0.2), display -> display.entityTags().contains(TAG)).isEmpty();
    }

    /** Sits the villager down on the seat; false when it can't. */
    public static boolean sit(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        cleanup(level, villager.blockPosition());
        if (villager.isPassenger() || !isSeat(level, pos)) {
            return false;
        }
        Display.ItemDisplay seat = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (seat == null) {
            return false;
        }
        seat.addTag(TAG);
        // on a stair: on its low half, facing away from the back; on a chair: on its seat, facing its front
        BlockState state = level.getBlockState(pos);
        Direction front = frontOf(state);
        double forward = state.getBlock() instanceof StairBlock ? STAIR_FORWARD : 0.0;
        double height = isChair(state) ? seatHeight(level, pos, state) : SEAT_HEIGHT;
        seat.setPos(pos.getX() + 0.5 + (front == null ? 0 : front.getStepX() * forward), pos.getY() + height + SIT_OFFSET,
                pos.getZ() + 0.5 + (front == null ? 0 : front.getStepZ() * forward));
        if (front != null) {
            seat.setYRot(front.toYRot());
        }
        level.addFreshEntity(seat);
        if (!villager.startRiding(seat, true, true)) {
            seat.discard();
            return false;
        }
        return true;
    }

    /** A chair of another mod: in the {@link #SEATS} tag, with a horizontal facing to sit towards. */
    private static boolean isChair(BlockState state) {
        return state.is(SEATS) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
    }

    /** The way someone sitting on a seat faces: out of a stair, along a chair's facing; none on a slab. */
    private static @Nullable Direction frontOf(BlockState state) {
        if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            return state.getValue(StairBlock.FACING).getOpposite();
        }
        return isChair(state) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : null;
    }

    /** How high a chair's seat is: where a line down its middle meets it (its back stands at the rear). */
    private static double seatHeight(BlockGetter level, BlockPos pos, BlockState state) {
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hit = state.getCollisionShape(level, pos).clip(top, top.subtract(0.0, 1.0, 0.0), pos);
        return hit == null ? SEAT_HEIGHT : hit.getLocation().y - pos.getY();
    }

    /**
     * On a stair or a chair, a villager sits facing out of it: its body stays turned that way, and its head turns only
     * so far to either side, never round into the seat's back. Run every tick while riding, on both sides (the client
     * turns the body by itself); the seat is known by the block under it, as clients do not get entity tags.
     */
    public static void faceOutOfStair(LivingEntity rider) {
        if (!(rider.getVehicle() instanceof Display.ItemDisplay seat)) {
            return;
        }
        Direction facing = frontOf(rider.level().getBlockState(seat.blockPosition()));
        if (facing == null) {
            return;
        }
        float front = facing.toYRot();
        rider.setYBodyRot(front);
        rider.setYRot(front);
        rider.setYHeadRot(front + Mth.clamp(Mth.wrapDegrees(rider.getYHeadRot() - front), -STAIR_HEAD_TURN, STAIR_HEAD_TURN));
    }

    public static boolean isSitting(VillagerEntityMCA villager) {
        return villager.getVehicle() instanceof Display.ItemDisplay seat && seat.entityTags().contains(TAG);
    }

    /** Gets the villager up from its seat, if it sits on one, and removes the seat. */
    public static void standUp(VillagerEntityMCA villager) {
        Entity vehicle = villager.getVehicle();
        if (vehicle instanceof Display.ItemDisplay seat && seat.entityTags().contains(TAG)) {
            villager.stopRiding();
            seat.discard();
            villager.setPos(villager.getX(), Math.floor(seat.getY()) + 1.0, villager.getZ());
        }
    }

    /** Removes seats nobody sits on anymore (left after a reload or a crash). */
    public static void cleanup(ServerLevel level, BlockPos near) {
        List<Display.ItemDisplay> orphans = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(near).inflate(CLEANUP_RANGE),
                display -> display.entityTags().contains(TAG) && display.getPassengers().isEmpty());
        orphans.forEach(Entity::discard);
    }

    /** The seat block the villager sits on, if any. */
    public static @Nullable BlockPos seatOf(VillagerEntityMCA villager) {
        return isSitting(villager) && villager.getVehicle() != null ? villager.getVehicle().blockPosition() : null;
    }
}
