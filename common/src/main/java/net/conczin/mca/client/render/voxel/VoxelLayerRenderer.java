package net.conczin.mca.client.render.voxel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Submits the cube meshes of a texture's outer layers, each placed with the pose and box of the
 * model part it belongs to. Call after the model was posed for the state being drawn.
 */
public final class VoxelLayerRenderer {
    // Beyond this distance in blocks the flat outer layers are drawn instead of cubes.
    public static final double MAX_DISTANCE = 12.0;

    private VoxelLayerRenderer() {
    }

    public static boolean inRange(double distanceToCameraSq) {
        return distanceToCameraSq < MAX_DISTANCE * MAX_DISTANCE;
    }

    public static void submit(ModelPart root, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                              int lightCoords, int overlayCoords, Identifier texture) {
        Map<VoxelLayer, VoxelMesh> meshes = VoxelMeshCache.get(texture);
        if (meshes == null) {
            return;
        }

        Map<String, VillagerParts.Placement> placements = VillagerParts.placements(root, poseStack);
        RenderType renderType = RenderTypes.entityCutout(texture);
        for (VoxelLayer layer : VoxelLayer.values()) {
            VoxelMesh mesh = meshes.get(layer);
            VillagerParts.Placement placement = mesh == null || mesh.isEmpty() ? null : VillagerParts.placement(placements, layer.names());
            if (placement == null) {
                continue;
            }
            // Cubes are built around the layout box; move them onto the part's actual box.
            VoxelBox box = layer.box();
            PoseStack partStack = new PoseStack();
            partStack.last().set(placement.pose());
            partStack.translate((placement.minX() - box.x()) / 16.0F, (placement.minY() - box.y()) / 16.0F, (placement.minZ() - box.z()) / 16.0F);
            submitNodeCollector.submitCustomGeometry(partStack, renderType, (pose, buffer) -> mesh.emit(pose, buffer, lightCoords, overlayCoords));
        }
    }
}
