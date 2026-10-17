package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Inclusive, axis aligned box of blocks a work order covers.
 */
public record WorkArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public WorkArea {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted work area");
        }
    }

    public static WorkArea of(BlockPos a, BlockPos b) {
        return new WorkArea(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())
        );
    }

    public static WorkArea fromArray(int[] values) {
        if (values.length != 6) {
            throw new IllegalArgumentException("Work area needs 6 coordinates, got " + values.length);
        }
        return new WorkArea(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    public int[] toArray() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /** True when the position lies inside the box grown by {@code margin} blocks on every side. */
    public boolean isNear(BlockPos pos, int margin) {
        return pos.getX() >= minX - margin && pos.getX() <= maxX + margin
               && pos.getY() >= minY - margin && pos.getY() <= maxY + margin
               && pos.getZ() >= minZ - margin && pos.getZ() <= maxZ + margin;
    }

    public BlockPos center() {
        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /** Layer heights in working order: from the top for {@code topDown}, else from the bottom. */
    public int[] layerOrder(boolean topDown) {
        int[] layers = new int[sizeY()];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = topDown ? maxY - i : minY + i;
        }
        return layers;
    }

    public List<BlockPos> layer(int y) {
        List<BlockPos> positions = new ArrayList<>(sizeX() * sizeZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new BlockPos(x, y, z));
            }
        }
        return positions;
    }
}
