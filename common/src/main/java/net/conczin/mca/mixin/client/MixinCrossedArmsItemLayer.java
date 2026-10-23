package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Human-like villagers hold their item in the right arm instead of the folded arms.
 */
@Mixin(CrossedArmsItemLayer.class)
public class MixinCrossedArmsItemLayer {
    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HoldingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$skipForHumanLike(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HoldingEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null) {
            ci.cancel();
        }
    }
}
