package net.conczin.mca.expansion.minds.routine;

import net.minecraft.core.Direction;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Sitting for real: stairs and bottom slabs are benches. The villager rides an invisible display
 * entity placed on the seat (displays have no size, so it sits right there) and MCA's model draws
 * the riding pose.
 */
public final class Seats {
    public static final String TAG = "mca_seat";
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

    private Seats() {
    }

    /** Whether a block is something to sit on: bottom stairs or a bottom slab with room above. */
    public static boolean isSeat(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean seat = state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM
                       || state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
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
        if (state.getBlock() instanceof StairBlock) {
            return OpportunityScanner.canStand(level, pos.relative(state.getValue(StairBlock.FACING).getOpposite()));
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
        // on a stair: on its low half, facing away from the back
        BlockState state = level.getBlockState(pos);
        Direction front = state.getBlock() instanceof StairBlock ? state.getValue(StairBlock.FACING).getOpposite() : null;
        double forward = front == null ? 0.0 : STAIR_FORWARD;
        seat.setPos(pos.getX() + 0.5 + (front == null ? 0 : front.getStepX() * forward), pos.getY() + SEAT_HEIGHT + SIT_OFFSET,
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
