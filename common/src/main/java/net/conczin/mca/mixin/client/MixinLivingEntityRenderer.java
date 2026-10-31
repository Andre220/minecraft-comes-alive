package net.conczin.mca.mixin.client;

import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.gui.PreviewEntityAnimation;
import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mca$capturePreviewAnimationState(
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {
        VillagerStateHolder.require(state).mca$setPreviewEntityAnimationState(PreviewEntityAnimation.getActiveState(entity));
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mca$scaleHumanLikeVillager(LivingEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null) {
            float[] scale = VanillaVillagerLook.modelScale(holder.mca$getLookVisuals());
            // the villager renderer scales its mesh to 15/16 itself; the zombie villager's player-sized mesh is not
            float mesh = state instanceof ZombieVillagerRenderState ? VanillaVillagerLook.MESH_SCALE : 1.0F;
            poseStack.scale(scale[0] * mesh, scale[1] * mesh, scale[0] * mesh);
        }
    }

    @ModifyReturnValue(
            method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("RETURN")
    )
    private @Nullable RenderType mca$hideVanillaPlayerModel(
            @Nullable RenderType original,
            LivingEntityRenderState state,
            boolean showBody,
            boolean translucent,
            boolean showOutline
    ) {
        return state instanceof VillagerStateHolder holder && holder.mca$isVillagerRendererActive()
                ? null
                : original;
    }
}
