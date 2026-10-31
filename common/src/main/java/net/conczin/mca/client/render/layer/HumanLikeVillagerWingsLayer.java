package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

/**
 * An elytra worn by a human-like villager, folded on its back. The vanilla wings layer needs a humanoid model; this
 * one hangs the vanilla elytra on the animated body instead.
 */
public class HumanLikeVillagerWingsLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final List<String> BODY = List.of("body");
    /** The elytra's resting angles, as vanilla has them on a standing player. */
    private static final float FOLDED_X = 0.2617994F;
    private static final float FOLDED_Z = -0.2617994F;

    private final ElytraModel elytra;
    private final EquipmentLayerRenderer equipmentRenderer;

    public HumanLikeVillagerWingsLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.elytra = new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA));
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state instanceof VanillaVillagerLookHolder holder) || holder.mca$getLookVisuals() == null) {
            return;
        }
        ItemStack chest = holder.mca$getArmor()[1];
        Equippable equippable = chest.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty()) {
            return;
        }
        VillagerParts.poseNow(getParentModel(), state);
        VillagerParts.Placement body = VillagerParts.placement(VillagerParts.placements(getParentModel().root(), new PoseStack()), BODY);
        if (body == null) {
            return;
        }
        HumanoidRenderState wings = holder.mca$getWingsState();
        wings.elytraRotX = FOLDED_X;
        wings.elytraRotY = 0.0F;
        wings.elytraRotZ = FOLDED_Z;
        wings.isCrouching = false;
        wings.lightCoords = lightCoords;

        // on the back of the body, lined up as on a vanilla body (box from -4, 0, -2), then where vanilla puts it
        poseStack.pushPose();
        poseStack.mulPose(body.pose().pose());
        poseStack.translate((body.minX() + 4.0F) / 16.0F, body.minY() / 16.0F, (body.minZ() + 2.0F) / 16.0F + 0.125F);
        // only the wings layers draw anything; an armor chestplate has none
        equipmentRenderer.renderLayers(EquipmentClientInfo.LayerType.WINGS, equippable.assetId().get(), elytra, wings, chest, poseStack,
                submitNodeCollector, lightCoords, state.outlineColor);
        poseStack.popPose();
    }
}
