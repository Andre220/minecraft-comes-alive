package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.client.model.HumanLikeVillagerMesh;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Held items for human-like villagers, placed in the arms as ItemInHandLayer does for humanoids: the main hand item
 * in the right arm, the off hand item in the left. The vanilla CrossedArmsItemLayer is skipped for these villagers.
 */
public class HumanLikeVillagerHandItemLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final List<String> RIGHT_ARM = List.of(HumanLikeVillagerMesh.RIGHT_ARM);
    private static final List<String> LEFT_ARM = List.of(HumanLikeVillagerMesh.LEFT_ARM);

    public HumanLikeVillagerHandItemLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state instanceof VanillaVillagerLookHolder holder) || holder.mca$getLookVisuals() == null) {
            return;
        }
        ItemStackRenderState main = holder.mca$getHandItem();
        ItemStackRenderState off = holder.mca$getOffHandItem();
        if (main.isEmpty() && off.isEmpty()) {
            return;
        }
        VillagerParts.poseNow(getParentModel(), state);
        Map<String, VillagerParts.Placement> placements = VillagerParts.placements(getParentModel().root(), poseStack);
        submitInArm(main, VillagerParts.placement(placements, RIGHT_ARM), false, poseStack, submitNodeCollector, lightCoords, state);
        submitInArm(off, VillagerParts.placement(placements, LEFT_ARM), true, poseStack, submitNodeCollector, lightCoords, state);
    }

    private static void submitInArm(ItemStackRenderState item, VillagerParts.@Nullable Placement arm, boolean left, PoseStack poseStack,
                                    SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state) {
        if (item.isEmpty() || arm == null) {
            return;
        }
        // Align to the arm box's top as on a vanilla arm (boxes from x=-3 right, x=-1 left, y=-2), whatever EMF's local space.
        poseStack.pushPose();
        poseStack.last().set(arm.pose());
        poseStack.translate((arm.minX() + (left ? 1.0F : 3.0F)) / 16.0F, (arm.minY() + 2.0F) / 16.0F, (arm.minZ() + 2.0F) / 16.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
