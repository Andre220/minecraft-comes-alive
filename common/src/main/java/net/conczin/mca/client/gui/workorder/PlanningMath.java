package net.conczin.mca.client.gui.workorder;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;

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
}
