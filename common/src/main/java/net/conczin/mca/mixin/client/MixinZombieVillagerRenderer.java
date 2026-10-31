package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Human-like zombie villagers: MCA's look (its zombie clothes and sickly skin for MCA's infected villagers) on a
 * player-shaped mesh, sized by age and genes like the villagers, instead of the vanilla zombie villager.
 */
@Mixin(ZombieVillagerRenderer.class)
public abstract class MixinZombieVillagerRenderer {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;Lnet/minecraft/client/renderer/entity/state/ZombieVillagerRenderState;F)V",
            at = @At("TAIL")
    )
    private void mca$extractLook(ZombieVillager entity, ZombieVillagerRenderState state, float partialTicks, CallbackInfo ci) {
        VillagerVisuals visuals = VanillaVillagerLook.visualsFor(entity);
        ((VanillaVillagerLookHolder) state).mca$setLookVisuals(visuals);
        if (visuals != null) {
            // children use the adult mesh, sized by their age
            state.isBaby = false;
            if (state.nameTagAttachment != null && entity.getBbHeight() > 0.0F) {
                double factor = VanillaVillagerLook.renderedHeight(visuals) / entity.getBbHeight();
                state.nameTagAttachment = state.nameTagAttachment.multiply(1.0, factor, 1.0);
            }
        }
    }

    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieVillagerRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$useMcaTexture(ZombieVillagerRenderState state, CallbackInfoReturnable<Identifier> cir) {
        VillagerVisuals visuals = ((VanillaVillagerLookHolder) state).mca$getLookVisuals();
        if (visuals != null) {
            cir.setReturnValue(VanillaVillagerLook.textureFor(visuals));
        }
    }
}
