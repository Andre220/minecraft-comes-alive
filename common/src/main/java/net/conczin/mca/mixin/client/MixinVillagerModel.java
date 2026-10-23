package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.conczin.mca.client.render.voxel.VoxelLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With EMF, hides the human-like mesh parts it keeps next to their animated copies. Flat outer
 * layers drawn as cubes instead are hidden through the texture, which EMF cannot override.
 */
@Mixin(VillagerModel.class)
public abstract class MixinVillagerModel extends EntityModel<VillagerRenderState> {
    protected MixinVillagerModel(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
    private void mca$togglePartVisibility(VillagerRenderState state, CallbackInfo ci) {
        // Children's bigger heads; Fresh Animations scales child heads itself.
        if (!VillagerParts.isEmf(this.root) && state instanceof VanillaVillagerLookHolder holder && holder.mca$getLookVisuals() != null) {
            float head = holder.mca$getLookVisuals().dimensions().getHead();
            ModelPart headPart = ((VillagerModel) (Object) this).getHead();
            headPart.xScale = head;
            headPart.yScale = head;
            headPart.zScale = head;
        }

        if (VillagerParts.isEmf(this.root)) {
            VoxelLayer.VANILLA_DUPLICATES.forEach((vanillaName, modelId) -> {
                ModelPart vanilla = VillagerParts.exact(this.root, vanillaName);
                if (vanilla != null && VillagerParts.exact(this.root, VillagerParts.EMF_PREFIX + modelId) != null) {
                    vanilla.visible = false;
                }
            });
        }

    }
}
