package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;

/**
 * Turning a blueprint around the vertical axis, in quarter turns clockwise (the same direction
 * as vanilla's {@code Rotation}). Positions are relative to the blueprint's lowest corner before
 * and after turning, so a placed blueprint always starts at the clicked spot.
 */
public final class BlueprintTransform {
    private BlueprintTransform() {
    }

    public static int normalize(int rotation) {
        return Math.floorMod(rotation, 4);
    }

    /** Size after turning; odd turns swap width and depth. */
    public static int[] rotatedSize(int sizeX, int sizeY, int sizeZ, int rotation) {
        return normalize(rotation) % 2 == 0
                ? new int[]{sizeX, sizeY, sizeZ}
                : new int[]{sizeZ, sizeY, sizeX};
    }

    /**
     * Where a position of the blueprint ends up after turning it.
     *
     * @param sizeX blueprint width before turning
     * @param sizeZ blueprint depth before turning
     */
    public static BlockPos rotate(int x, int y, int z, int sizeX, int sizeZ, int rotation) {
        return switch (normalize(rotation)) {
            case 1 -> new BlockPos(sizeZ - 1 - z, y, x);
            case 2 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case 3 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    /** The placed box of a blueprint whose lowest corner is {@code origin}. */
    public static WorkArea placedArea(BlockPos origin, int sizeX, int sizeY, int sizeZ, int rotation) {
        int[] size = rotatedSize(sizeX, sizeY, sizeZ, rotation);
        return new WorkArea(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size[0] - 1, origin.getY() + size[1] - 1, origin.getZ() + size[2] - 1);
    }
}
