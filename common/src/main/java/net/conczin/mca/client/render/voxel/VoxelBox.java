package net.conczin.mca.client.render.voxel;

/**
 * A box of the MCA villager layout in 64 unit space: position and size in model units (as in
 * {@code addBox}, without inflation) and its texture offset.
 */
public record VoxelBox(float x, float y, float z, float w, float h, float d, float u, float v) {
}
