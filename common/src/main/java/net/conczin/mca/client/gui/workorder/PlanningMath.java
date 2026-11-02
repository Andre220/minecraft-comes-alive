package net.conczin.mca.client.gui.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Turning the mouse cursor into a ray into the world.
 */
public final class PlanningMath {
    private PlanningMath() {
    }

    /**
     * Normalized device coordinates of a cursor: -1 at the left/bottom edge, 1 at the right/top edge.
     */
    public static float[] toNdc(double mouseX, double mouseY, double width, double height) {
        if (width <= 0 || height <= 0) {
            return new float[]{0.0F, 0.0F};
        }
        float x = (float) (mouseX / width * 2.0 - 1.0);
        float y = (float) (1.0 - mouseY / height * 2.0);
        return new float[]{Math.clamp(x, -1.0F, 1.0F), Math.clamp(y, -1.0F, 1.0F)};
    }

    /** Where a ray crosses the horizontal plane at {@code planeY}, if it does so in front of its origin. */
    public static Optional<Vec3> intersectHorizontal(Vec3 origin, Vec3 direction, double planeY, double maxDistance) {
        if (Math.abs(direction.y) < 1.0E-6) {
            return Optional.empty();
        }
        double t = (planeY - origin.y) / direction.y;
        if (t <= 0 || t * direction.length() > maxDistance) {
            return Optional.empty();
        }
        return Optional.of(origin.add(direction.scale(t)));
    }

    /**
     * Walks the ray block by block (Amanatides and Woo) and returns the first block {@code solid} accepts, for blocks
     * that only exist in the plan and so are no use to the world's own ray test.
     */
    public static Optional<BlockPos> march(Vec3 origin, Vec3 direction, double maxDistance, Predicate<BlockPos> solid) {
        Vec3 dir = direction.normalize();
        int x = net.minecraft.util.Mth.floor(origin.x);
        int y = net.minecraft.util.Mth.floor(origin.y);
        int z = net.minecraft.util.Mth.floor(origin.z);
        int stepX = dir.x > 0 ? 1 : -1;
        int stepY = dir.y > 0 ? 1 : -1;
        int stepZ = dir.z > 0 ? 1 : -1;
        double deltaX = Math.abs(1.0 / dir.x);
        double deltaY = Math.abs(1.0 / dir.y);
        double deltaZ = Math.abs(1.0 / dir.z);
        double maxX = (dir.x > 0 ? x + 1 - origin.x : origin.x - x) * deltaX;
        double maxY = (dir.y > 0 ? y + 1 - origin.y : origin.y - y) * deltaY;
        double maxZ = (dir.z > 0 ? z + 1 - origin.z : origin.z - z) * deltaZ;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double travelled = 0;
        while (travelled <= maxDistance) {
            if (solid.test(pos.set(x, y, z))) {
                return Optional.of(pos.immutable());
            }
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                travelled = maxX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                y += stepY;
                travelled = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                travelled = maxZ;
                maxZ += deltaZ;
            }
        }
        return Optional.empty();
    }

    /** The face of the box the ray enters it through, if it hits it from outside. */
    public static Optional<Direction> enteredFace(Vec3 origin, Vec3 direction, AABB box, double maxDistance) {
        Vec3 end = origin.add(direction.normalize().scale(maxDistance));
        return box.clip(origin, end).map(hit -> {
            double epsilon = 1.0E-4;
            if (Math.abs(hit.x - box.minX) < epsilon) {
                return Direction.WEST;
            } else if (Math.abs(hit.x - box.maxX) < epsilon) {
                return Direction.EAST;
            } else if (Math.abs(hit.y - box.minY) < epsilon) {
                return Direction.DOWN;
            } else if (Math.abs(hit.y - box.maxY) < epsilon) {
                return Direction.UP;
            } else if (Math.abs(hit.z - box.minZ) < epsilon) {
                return Direction.NORTH;
            }
            return Direction.SOUTH;
        });
    }
}
