package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Aiming at an open wardrobe door or dresser drawer. They stand out in front of their block, and the game only tries
 * a block's shape where the line of sight passes through that block itself: looking down at an open bottom drawer, or
 * at a door from its side, the look goes on into the floor or past it. So after the game has picked, the furniture
 * near the line of sight is tried too, and taken when it is nearer.
 */
public final class ClosetPicker {
    private ClosetPicker() {
    }

    /** What the player looks at, if furniture is nearer than what the game picked. */
    public static @Nullable HitResult nearer(Minecraft client, float partialTicks) {
        Entity camera = client.getCameraEntity();
        if (!Config.getInstance().villagerWardrobes || camera == null || client.level == null || client.player == null) {
            return null;
        }
        double reach = client.player.blockInteractionRange();
        Vec3 from = camera.getEyePosition(partialTicks);
        Vec3 to = from.add(camera.getViewVector(partialTicks).scale(reach));
        HitResult picked = client.hitResult;
        double best = picked == null || picked.getType() == HitResult.Type.MISS ? reach * reach : picked.getLocation().distanceToSqr(from);
        BlockHitResult nearest = null;
        // a door or drawer stands at most a block out of its own
        AABB around = new AABB(from, to).inflate(1.0);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(around.getMinPosition()), BlockPos.containing(around.getMaxPosition()))) {
            if (!(client.level.getBlockState(pos).getBlock() instanceof FurnitureBlock)) {
                continue;
            }
            VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos, CollisionContext.of(camera));
            BlockHitResult hit = shape.clip(from, to, pos.immutable());
            if (hit != null && hit.getLocation().distanceToSqr(from) < best - 1.0E-7) {
                best = hit.getLocation().distanceToSqr(from);
                nearest = hit;
            }
        }
        return nearest;
    }
}
