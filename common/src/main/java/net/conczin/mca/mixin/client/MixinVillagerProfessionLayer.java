package net.conczin.mca.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Human-like villagers wear MCA clothing; the vanilla biome/profession textures use the villager
 * texture layout, which does not fit the player-shaped mesh.
 */
@Mixin(VillagerProfessionLayer.class)
public class MixinVillagerProfessionLayer {
    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$hideForHumanLike(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, LivingEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null) {
            ci.cancel();
        }
    }
}
