package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.resources.WorkAnimationClip;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * What a work animation carries in front of a human-like villager, such as the hauler's crate; placed as the classic
 * MCA renderer's CarriedItemLayer places it.
 */
public class HumanLikeVillagerCarriedItemLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    public HumanLikeVillagerCarriedItemLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state instanceof VanillaVillagerLookHolder holder)) {
            return;
        }
        WorkAnimationClip.Carry carry = holder.mca$getCarry();
        if (carry == null || holder.mca$getCarriedItem().isEmpty()) {
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
        holder.mca$getCarriedItem().submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
