package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.Config;
import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Breasts of human-like villagers, with MCA's geometry and sizing (see CommonVillagerModel): a box
 * that reuses the chest texture, plus the jacket's breastplate, attached to the animated body. The
 * per-villager size goes into the pose, so villagers sharing the model do not affect each other.
 */
public class HumanLikeVillagerBreastLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final List<String> BODY = List.of("body");
    // The body box's corner in vanilla model space; EMF parts may use other local coordinates.
    private static final float BODY_X = -4.0F;
    private static final float BODY_Y = 0.0F;
    private static final float BODY_Z = -2.0F;

    private final ModelPart breasts;

    public HumanLikeVillagerBreastLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("breasts", CubeListBuilder.create()
                .texOffs(18, 21).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, CubeDeformation.NONE)
                .texOffs(18, 37).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, new CubeDeformation(0.1F)), PartPose.ZERO);
        this.breasts = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("breasts");
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !Config.getInstance().enableBoobs || !(state instanceof VanillaVillagerLookHolder holder)) {
            return;
        }
        VillagerVisuals visuals = holder.mca$getLookVisuals();
        if (visuals == null || !visuals.female()) {
            return;
        }
        float size = visuals.breastSize() * visuals.dimensions().getBreasts();
        if (size <= 0.0F) {
            return;
        }
        VillagerParts.poseNow(getParentModel(), state);
        VillagerParts.Placement body = VillagerParts.placement(VillagerParts.placements(getParentModel().root(), poseStack), BODY);
        if (body == null) {
            return;
        }

        // MCA's breast placement (CommonVillagerModel), relative to the body.
        PoseStack stack = new PoseStack();
        stack.last().set(body.pose());
        stack.translate((body.minX() - BODY_X) / 16.0F, (body.minY() - BODY_Y) / 16.0F, (body.minZ() - BODY_Z) / 16.0F);
        stack.scale(size * 0.2F + 1.05F, size * 0.75F + 0.75F, size * 0.75F + 0.75F);
        stack.translate(0.25F / 16.0F, (float) (5.0F - Math.pow(size, 0.5) * 2.5F) / 16.0F, (-1.5F + size * 0.25F) / 16.0F);
        stack.mulPose(Axis.XP.rotation((float) Math.PI * 0.3F));

        Identifier texture = VanillaVillagerLook.textureFor(visuals);
        submitNodeCollector.submitModelPart(breasts, stack, RenderTypes.entityCutout(texture), lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F), null);
    }
}
