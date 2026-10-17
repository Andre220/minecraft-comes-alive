package net.conczin.mca.client.gui.workorder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

/**
 * A free flying camera for planning: the view is moved to an invisible client-side marker
 * entity above the player, the player itself stays where it is (and is visible).
 */
public final class PlanningCamera {
    /** How far the camera may leave the player; the server only sends chunks around the player anyway. */
    static final double MAX_RANGE = 96.0;
    private static final double MAX_HEIGHT_ABOVE = 64.0;
    private static final double SPEED = 0.6;
    private static final double FAST_SPEED = 1.6;

    private final Minecraft minecraft;
    private final Marker entity;
    private float yaw;
    private float pitch;

    public PlanningCamera(Minecraft minecraft, LocalPlayer player) {
        this.minecraft = minecraft;
        this.entity = new Marker(EntityTypes.MARKER, player.level());
        this.entity.noPhysics = true;

        // start above and behind the player, looking down at what is in front of it
        yaw = player.getYRot();
        pitch = 50.0F;
        Vec3 back = forward(yaw).scale(-8.0);
        Vec3 start = player.position().add(back.x, 9.0, back.z);
        entity.setPos(start.x, start.y, start.z);
        applyRotation();
        entity.setOldPosAndRot();
    }

    public void activate() {
        minecraft.setCameraEntity(entity);
    }

    public void deactivate() {
        if (minecraft.getCameraEntity() == entity) {
            minecraft.setCameraEntity(minecraft.player);
        }
    }

    public boolean isActive() {
        return minecraft.getCameraEntity() == entity;
    }

    public Vec3 position() {
        return entity.position();
    }

    /**
     * Moves the camera for one tick.
     *
     * @param forward  1 forwards, -1 backwards
     * @param strafe   1 left, -1 right
     * @param vertical 1 up, -1 down
     */
    public void tick(int forward, int strafe, int vertical, boolean fast) {
        entity.setOldPosAndRot();
        double speed = fast ? FAST_SPEED : SPEED;
        Vec3 move = forward(yaw).scale(forward).add(left(yaw).scale(strafe));
        if (move.lengthSqr() > 1.0E-4) {
            move = move.normalize().scale(speed);
        }
        moveBy(move.x, vertical * speed, move.z);
    }

    public void moveBy(double dx, double dy, double dz) {
        LocalPlayer player = minecraft.player;
        double x = entity.getX() + dx;
        double y = entity.getY() + dy;
        double z = entity.getZ() + dz;
        if (player != null) {
            // keep the camera within reach of the player's loaded chunks
            double ox = x - player.getX();
            double oz = z - player.getZ();
            double distance = Math.sqrt(ox * ox + oz * oz);
            if (distance > MAX_RANGE) {
                x = player.getX() + ox / distance * MAX_RANGE;
                z = player.getZ() + oz / distance * MAX_RANGE;
            }
            y = Mth.clamp(y, player.level().getMinY() + 1.0, player.getY() + MAX_HEIGHT_ABOVE);
        }
        entity.setPos(x, y, z);
    }

    public void rotate(double deltaYaw, double deltaPitch) {
        yaw = Mth.wrapDegrees(yaw + (float) deltaYaw);
        pitch = Mth.clamp(pitch + (float) deltaPitch, -89.0F, 89.0F);
        applyRotation();
    }

    private void applyRotation() {
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        // rotation follows the mouse every frame, so skip the tick interpolation
        entity.yRotO = yaw;
        entity.xRotO = pitch;
    }

    static Vec3 forward(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
    }

    static Vec3 left(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(rad), 0.0, Mth.sin(rad));
    }
}
