package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.client.resources.WorkAnimationClip;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws what a work animation carries in front of the body, such as the hauler's crate.
 */
public final class CarriedItemLayer extends RenderLayer<VillagerRenderState, VillagerEntityModelMCA> {
    public CarriedItemLayer(RenderLayerParent<VillagerRenderState, VillagerEntityModelMCA> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       VillagerRenderState state, float yRot, float xRot) {
        WorkAnimationClip.Carry carry = state.carry;
        if (carry == null || state.carriedItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        float[] position = carry.position();
        poseStack.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
        float[] rotation = carry.rotation();
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation[1]));
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation[0]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
        // model space is upside down, items are not
        poseStack.scale(-carry.scale(), -carry.scale(), carry.scale());
        state.carriedItem.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
