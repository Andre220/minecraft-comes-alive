package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.voxel.VoxelLayerRenderer;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;

/**
 * Draws the outer layers of close human-like villagers as small cubes. The model itself is drawn
 * with a texture whose outer layers are empty, so the flat layers disappear even under EMF.
 */
public class HumanLikeVillager3dLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    public HumanLikeVillager3dLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !VanillaVillagerLook.use3dLayers(state)) {
            return;
        }
        VillagerVisuals visuals = ((VanillaVillagerLookHolder) state).mca$getLookVisuals();
        if (visuals == null) {
            return;
        }
        // LivingEntityRenderer posed the model (and EMF animated it) right before its layers.
        VoxelLayerRenderer.submit(getParentModel().root(), poseStack, submitNodeCollector, lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F), VanillaVillagerLook.textureFor(visuals));
    }
}
