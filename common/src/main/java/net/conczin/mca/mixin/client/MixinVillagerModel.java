package net.conczin.mca.mixin.client;

import java.util.List;
import net.conczin.mca.client.model.HumanLikeVillagerMesh;
import net.conczin.mca.client.model.WorkAnimationPlayer;
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

        // work animations; with EMF, Fresh Animations' model plays them from the mca_work variables instead
        if (!VillagerParts.isEmf(this.root) && state instanceof VanillaVillagerLookHolder holder && holder.mca$getWorkPose() != null) {
            ModelPart head = ((VillagerModel) (Object) this).getHead();
            ModelPart body = this.root.getChild("body");
            ModelPart rightLeg = this.root.getChild("right_leg");
            ModelPart leftLeg = this.root.getChild("left_leg");
            // the arms sit in the body here, so the clip's root movement moves the body and they go along
            WorkAnimationPlayer.apply(holder.mca$getWorkPose(), bone -> switch (bone) {
                case "head" -> head;
                case "body" -> body;
                case "right_arm" -> VillagerParts.exact(this.root, HumanLikeVillagerMesh.RIGHT_ARM);
                case "left_arm" -> VillagerParts.exact(this.root, HumanLikeVillagerMesh.LEFT_ARM);
                case "right_leg" -> rightLeg;
                case "left_leg" -> leftLeg;
                default -> null;
            }, List.of(head, body, rightLeg, leftLeg));
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
