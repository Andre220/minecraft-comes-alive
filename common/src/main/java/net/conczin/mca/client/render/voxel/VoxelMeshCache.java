package net.conczin.mca.client.render.voxel;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cube meshes of the outer layers, built once per composite texture. Composite textures are
 * immutable for a given identifier, so meshes stay valid while the texture exists.
 */
public final class VoxelMeshCache {
    private static final Map<Identifier, Map<VoxelLayer, VoxelMesh>> CACHE = new MaxSizeHashMap<>(256, true);

    private VoxelMeshCache() {
    }

    /** Null when the texture is not a generated composite (or not loaded yet). */
    public static @Nullable Map<VoxelLayer, VoxelMesh> get(Identifier texture) {
        Map<VoxelLayer, VoxelMesh> meshes = CACHE.get(texture);
        if (meshes != null) {
            return meshes;
        }
        if (!(Minecraft.getInstance().getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic)) {
            return null;
        }
        NativeImage image = dynamic.getPixels();
        if (image == null) {
            return null;
        }
        meshes = new EnumMap<>(VoxelLayer.class);
        for (VoxelLayer layer : VoxelLayer.values()) {
            meshes.put(layer, VoxelMesh.build(layer.box(), image));
        }
        CACHE.put(texture, meshes);
        return meshes;
    }

    public static void clear() {
        CACHE.clear();
    }
}
