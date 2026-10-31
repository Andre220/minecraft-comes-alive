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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The fishing line of a human-like villager's rod: from the rod's tip, in the animated right arm, to the bobber,
 * drawn like the classic MCA renderer's.
 */
public class HumanLikeVillagerFishingLineLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final List<String> RIGHT_ARM = List.of(HumanLikeVillagerMesh.RIGHT_ARM);

    public HumanLikeVillagerFishingLineLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state instanceof VanillaVillagerLookHolder holder) || holder.mca$getLookVisuals() == null) {
            return;
        }
        Vec3 hookWorld = holder.mca$getFishingHook();
        if (hookWorld == null) {
            return;
        }
        VillagerParts.poseNow(getParentModel(), state);
        VillagerParts.Placement arm = VillagerParts.placement(VillagerParts.placements(getParentModel().root(), new PoseStack()), RIGHT_ARM);
        if (arm == null) {
            return;
        }
        // the rod's tip: where the hand item layer holds the rod, then as the classic layer finds it
        PoseStack hand = new PoseStack();
        hand.last().set(arm.pose());
        hand.translate((arm.minX() + 3.0F) / 16.0F, (arm.minY() + 2.0F) / 16.0F, (arm.minZ() + 2.0F) / 16.0F);
        hand.mulPose(Axis.XP.rotationDegrees(-90.0F));
        hand.mulPose(Axis.YP.rotationDegrees(180.0F));
        hand.translate(1.0F / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        Vector3f tip = hand.last().pose().transformPosition(new Vector3f());

        Vector3f hook = VillagerFishingLineLayer.getHookInCurrentModelSpace(poseStack, hookWorld);
        VillagerFishingLineLayer.renderLine(poseStack, submitNodeCollector, hook, tip);
    }
}
