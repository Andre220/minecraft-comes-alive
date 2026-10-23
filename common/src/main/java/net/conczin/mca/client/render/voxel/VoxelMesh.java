package net.conczin.mca.client.render.voxel;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One outer skin layer turned into small cubes: every opaque texel of the layer's box faces becomes
 * a cube one texel thick on the surface of the box. Faces hidden by a neighbouring cube, and the face
 * against the body, are skipped. Each cube is colored by sampling its texel.
 * <p>
 * Coordinates are model units (pixels) in the owning part's space; the face to texture mapping and
 * the vertex order follow {@code ModelPart.Cube}, so the cubes line up with the flat layer and
 * render with the same face culling.
 */
public final class VoxelMesh {
    // Per vertex: x, y, z, u, v, nx, ny, nz.
    private static final int STRIDE = 8;

    private final float[] data;
    private final int vertexCount;

    private VoxelMesh(float[] data, int vertexCount) {
        this.data = data;
        this.vertexCount = vertexCount;
    }

    public boolean isEmpty() {
        return vertexCount == 0;
    }

    public void emit(PoseStack.Pose pose, VertexConsumer buffer, int lightCoords, int overlayCoords) {
        for (int i = 0; i < vertexCount; i++) {
            int o = i * STRIDE;
            buffer.addVertex(pose, data[o] / 16.0F, data[o + 1] / 16.0F, data[o + 2] / 16.0F)
                    .setColor(-1)
                    .setUv(data[o + 3], data[o + 4])
                    .setOverlay(overlayCoords)
                    .setLight(lightCoords)
                    .setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
        }
    }

    /**
     * @param box     the layer's box in 64 unit layout space, without inflation
     * @param texture the full composite texture (64 * n pixels wide)
     */
    public static VoxelMesh build(VoxelBox box, NativeImage texture) {
        int scale = Math.max(1, texture.getWidth() / 64);
        float cell = 1.0F / scale;
        Builder builder = new Builder(texture, scale);

        // DOWN face (min y, the visual top): u from x, v from max z.
        builder.face(box.u() + box.d(), box.v(), box.w(), box.d(), (i, j) -> builder.voxel(
                box.x() + i * cell, box.y() - cell, box.z() + box.d() - (j + 1) * cell, Face.DOWN));
        // UP face (max y, the visual bottom).
        builder.face(box.u() + box.d() + box.w(), box.v(), box.w(), box.d(), (i, j) -> builder.voxel(
                box.x() + i * cell, box.y() + box.h(), box.z() + box.d() - (j + 1) * cell, Face.UP));
        // WEST face (min x): u from max z.
        builder.face(box.u(), box.v() + box.d(), box.d(), box.h(), (i, j) -> builder.voxel(
                box.x() - cell, box.y() + j * cell, box.z() + box.d() - (i + 1) * cell, Face.WEST));
        // NORTH face (min z, the front).
        builder.face(box.u() + box.d(), box.v() + box.d(), box.w(), box.h(), (i, j) -> builder.voxel(
                box.x() + i * cell, box.y() + j * cell, box.z() - cell, Face.NORTH));
        // EAST face (max x): u from min z.
        builder.face(box.u() + box.d() + box.w(), box.v() + box.d(), box.d(), box.h(), (i, j) -> builder.voxel(
                box.x() + box.w(), box.y() + j * cell, box.z() + i * cell, Face.EAST));
        // SOUTH face (max z, the back): u from max x.
        builder.face(box.u() + 2 * box.d() + box.w(), box.v() + box.d(), box.w(), box.h(), (i, j) -> builder.voxel(
                box.x() + box.w() - (i + 1) * cell, box.y() + j * cell, box.z() + box.d(), Face.SOUTH));

        return builder.finish();
    }

    /** Box faces; the outward direction of the layer face a voxel belongs to. */
    private enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), WEST(-1, 0, 0), EAST(1, 0, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1);

        final int dx;
        final int dy;
        final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    @FunctionalInterface
    private interface TexelVisitor {
        void visit(int i, int j);
    }

    private record Voxel(float x, float y, float z, int texelX, int texelY, Face face) {
    }

    private static final class Builder {
        private final NativeImage texture;
        private final int scale;
        private final float cell;
        private final List<Voxel> voxels = new ArrayList<>();
        private final Set<Long> occupied = new HashSet<>();
        private int texelX;
        private int texelY;

        Builder(NativeImage texture, int scale) {
            this.texture = texture;
            this.scale = scale;
            this.cell = 1.0F / scale;
        }

        /** Visits the opaque texels of a face rectangle given in layout units. */
        void face(float u, float v, float width, float height, TexelVisitor visitor) {
            int x0 = Math.round(u * scale);
            int y0 = Math.round(v * scale);
            int columns = Math.round(width * scale);
            int rows = Math.round(height * scale);
            for (int j = 0; j < rows; j++) {
                for (int i = 0; i < columns; i++) {
                    int tx = x0 + i;
                    int ty = y0 + j;
                    if (tx >= texture.getWidth() || ty >= texture.getHeight() || ARGB.alpha(texture.getPixel(tx, ty)) == 0) {
                        continue;
                    }
                    texelX = tx;
                    texelY = ty;
                    visitor.visit(i, j);
                }
            }
        }

        void voxel(float x, float y, float z, Face face) {
            voxels.add(new Voxel(x, y, z, texelX, texelY, face));
            occupied.add(key(x, y, z));
        }

        private long key(float x, float y, float z) {
            long cx = Math.round(x * scale) + (1 << 20);
            long cy = Math.round(y * scale) + (1 << 20);
            long cz = Math.round(z * scale) + (1 << 20);
            return (cx << 42) | (cy << 21) | cz;
        }

        private boolean hasNeighbour(Voxel voxel, int dx, int dy, int dz) {
            return occupied.contains(key(voxel.x + dx * cell, voxel.y + dy * cell, voxel.z + dz * cell));
        }

        VoxelMesh finish() {
            float[] data = new float[voxels.size() * 5 * 4 * STRIDE];
            int[] count = {0};
            float width = texture.getWidth();
            float height = texture.getHeight();
            for (Voxel voxel : voxels) {
                float u = (voxel.texelX + 0.5F) / width;
                float v = (voxel.texelY + 0.5F) / height;
                float x0 = voxel.x;
                float y0 = voxel.y;
                float z0 = voxel.z;
                float x1 = x0 + cell;
                float y1 = y0 + cell;
                float z1 = z0 + cell;
                Face inward = voxel.face;
                // Vertex orders from ModelPart.Cube (t = min z corners, l = max z corners).
                if (!skip(voxel, inward, 0, -1, 0)) {
                    data = quad(data, count, u, v, 0, -1, 0, x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0);
                }
                if (!skip(voxel, inward, 0, 1, 0)) {
                    data = quad(data, count, u, v, 0, 1, 0, x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1);
                }
                if (!skip(voxel, inward, -1, 0, 0)) {
                    data = quad(data, count, u, v, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
                }
                if (!skip(voxel, inward, 0, 0, -1)) {
                    data = quad(data, count, u, v, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
                }
                if (!skip(voxel, inward, 1, 0, 0)) {
                    data = quad(data, count, u, v, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
                }
                if (!skip(voxel, inward, 0, 0, 1)) {
                    data = quad(data, count, u, v, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
                }
            }
            return new VoxelMesh(data, count[0]);
        }

        /** Skips the face against the body and faces covered by a neighbouring cube. */
        private boolean skip(Voxel voxel, Face face, int dx, int dy, int dz) {
            boolean towardsBody = dx == -face.dx && dy == -face.dy && dz == -face.dz;
            return towardsBody || hasNeighbour(voxel, dx, dy, dz);
        }

        private static float[] quad(float[] data, int[] count, float u, float v, float nx, float ny, float nz,
                                    float ax, float ay, float az, float bx, float by, float bz,
                                    float cx, float cy, float cz, float dx, float dy, float dz) {
            int needed = (count[0] + 4) * STRIDE;
            if (needed > data.length) {
                data = Arrays.copyOf(data, Math.max(needed, data.length * 2));
            }
            count[0] = put(data, count[0], ax, ay, az, u, v, nx, ny, nz);
            count[0] = put(data, count[0], bx, by, bz, u, v, nx, ny, nz);
            count[0] = put(data, count[0], cx, cy, cz, u, v, nx, ny, nz);
            count[0] = put(data, count[0], dx, dy, dz, u, v, nx, ny, nz);
            return data;
        }

        private static int put(float[] data, int index, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            int o = index * STRIDE;
            data[o] = x;
            data[o + 1] = y;
            data[o + 2] = z;
            data[o + 3] = u;
            data[o + 4] = v;
            data[o + 5] = nx;
            data[o + 6] = ny;
            data[o + 7] = nz;
            return index + 1;
        }
    }
}
