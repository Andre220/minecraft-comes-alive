package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VanillaVillagerLook;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.layer.HumanLikeVillager3dLayer;
import net.conczin.mca.client.render.layer.HumanLikeVillagerBreastLayer;
import net.conczin.mca.client.render.layer.HumanLikeVillagerHandItemLayer;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Human-like squidward villagers: the vanilla renderer draws its (reshaped) model once with a
 * single MCA texture. Covers both MCA villagers registered to this renderer and plain vanilla ones.
 */
@Mixin(VillagerRenderer.class)
@SuppressWarnings("deprecation")
public abstract class MixinVillagerRenderer extends AgeableMobRenderer<Villager, VillagerRenderState, VillagerModel> {
    protected MixinVillagerRenderer(EntityRendererProvider.Context context, VillagerModel adultModel, VillagerModel babyModel, float shadow) {
        super(context, adultModel, babyModel, shadow);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V", at = @At("TAIL"))
    private void mca$addHandItemLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new HumanLikeVillagerHandItemLayer(this));
        this.addLayer(new HumanLikeVillager3dLayer(this));
        this.addLayer(new HumanLikeVillagerBreastLayer(this));
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
            at = @At("TAIL")
    )
    private void mca$extractLook(Villager entity, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
        VanillaVillagerLookHolder holder = (VanillaVillagerLookHolder) state;
        VillagerVisuals visuals = VanillaVillagerLook.visualsFor(entity);
        holder.mca$setLookVisuals(visuals);
        if (visuals != null) {
            // Children use the human-like adult model, sized by their age instead of the baby model.
            state.isBaby = false;
            if (state.nameTagAttachment != null && entity.getBbHeight() > 0.0F) {
                double factor = VanillaVillagerLook.renderedHeight(visuals) / entity.getBbHeight();
                state.nameTagAttachment = state.nameTagAttachment.multiply(1.0, factor, 1.0);
            }
            this.itemModelResolver.updateForLiving(holder.mca$getHandItem(), entity.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        } else {
            holder.mca$getHandItem().clear();
        }
    }

    @Inject(
            method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mca$useMcaTexture(VillagerRenderState state, CallbackInfoReturnable<Identifier> cir) {
        VillagerVisuals visuals = ((VanillaVillagerLookHolder) state).mca$getLookVisuals();
        if (visuals != null) {
            cir.setReturnValue(VanillaVillagerLook.textureFor(visuals, !VanillaVillagerLook.use3dLayers(state)));
        }
    }
}
