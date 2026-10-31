package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Head items (carved pumpkins, skulls, banners) on human-like villagers: worn on the animated head (Fresh Animations
 * included) and sized for its player-shaped head, not the vanilla villager's taller one. Zombie villagers with an
 * MCA look get the player head's sizes too.
 */
@Mixin(CustomHeadLayer.class)
public abstract class MixinCustomHeadLayer {
    @Unique
    private static final List<String> HEAD = List.of("head", "nose");

    /** Villagers and zombie villagers with an MCA look: player-shaped heads. */
    @Unique
    private static boolean mca$humanLike(LivingEntityRenderState state) {
        return state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null;
    }

    @ModifyExpressionValue(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer;transforms:Lnet/minecraft/client/renderer/entity/layers/CustomHeadLayer$Transforms;")
    )
    private CustomHeadLayer.Transforms mca$playerHeadTransforms(CustomHeadLayer.Transforms original, PoseStack poseStack,
                                                               SubmitNodeCollector collector, int light, LivingEntityRenderState state) {
        return mca$humanLike(state) ? CustomHeadLayer.Transforms.DEFAULT : original;
    }

    @WrapOperation(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HeadedModel;translateToHead(Lcom/mojang/blaze3d/vertex/PoseStack;)V")
    )
    @SuppressWarnings("unchecked")
    private void mca$animatedHead(HeadedModel model, PoseStack poseStack, Operation<Void> original,
                                  PoseStack outerPose, SubmitNodeCollector collector, int light, LivingEntityRenderState state) {
        // the zombie villager is a plain humanoid model: its own head is right; the villager model's parts are animated
        if (!mca$humanLike(state) || !(state instanceof VillagerRenderState)
            || !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof VillagerModel villagerModel)) {
            original.call(model, poseStack);
            return;
        }
        VillagerParts.poseNow(villagerModel, (VillagerRenderState) state);
        VillagerParts.Placement head = VillagerParts.placement(VillagerParts.placements(villagerModel.root(), new PoseStack()), HEAD);
        if (head == null) {
            original.call(model, poseStack);
            return;
        }
        // onto the head box, lined up as on a vanilla head (box from -4, -8, -4)
        poseStack.mulPose(head.pose().pose());
        poseStack.translate((head.minX() + 4.0F) / 16.0F, (head.minY() + 8.0F) / 16.0F, (head.minZ() + 4.0F) / 16.0F);
    }
}
