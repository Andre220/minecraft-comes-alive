package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.Config;
import net.conczin.mca.client.model.animation.FreshVillagerAnimations;
import net.conczin.mca.client.model.animation.VillagerAnimationState;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public class VillagerEntityBaseModelMCA<T extends LivingEntity & VillagerLike<T>> extends HumanoidModel<T> implements CommonVillagerModel<T> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        this.breasts = root.getChild(BREASTS);
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0f);
        PartDefinition data = modelData.getRoot();

        data.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);

        return modelData;
    }

    protected static CubeListBuilder newBreasts(CubeDeformation dilation, int oy) {
        CubeListBuilder builder = CubeListBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.texOffs(18, 21 + oy).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(head, hat);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public void prepareMobModel(T entity, float limbAngle, float limbDistance, float tickDelta) {
        super.prepareMobModel(entity, limbDistance, limbAngle, tickDelta);
        riding |= entity.getAgeState() == AgeState.BABY;
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        // Adjust animation parameters for babies
        if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            limbDistance = (float) Math.sin(villager.tickCount / 12F);
            limbAngle = (float) Math.cos(villager.tickCount / 9F) * 3;
            headYaw += (float) Math.sin(villager.tickCount / 2F);
        }

        //remove the boost for babies
        if (villager.isBaby()) {
            limbAngle /= 3.0f;
        }

        //and add our own
        limbAngle /= (0.2f + villager.getRawVerticalScaleFactor());

        // Call parent setup for base positioning
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        // Apply Fresh Animations
        VillagerAnimationState<T> animState = new VillagerAnimationState<>(
            villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch
        );
        
        FreshVillagerAnimations.applyAnimations(
            animState, 
            head, hat, 
            body, 
            rightArm, leftArm, 
            rightLeg, leftLeg
        );

        // Override with panic animation if panicking (preserves original MCA behavior)
        if (villager.getVillagerBrain().isPanicking()) {
            FreshVillagerAnimations.applyPanicAnimation(animationProgress, leftArm, rightArm);
        }

        applyVillagerDimensions(villager, villager.isCrouching());
        
        // CRÍTICO: Seios seguem movimento do torso - aplicado DEPOIS do applyVillagerDimensions
        // O applyVillagerDimensions já copia body.xRot, mas precisamos copiar yRot e zRot também
        // E ADICIONAR as transformações de posição às posições base que foram setadas
        breasts.yRot += body.yRot;  // ADICIONA rotação Y do body
        breasts.zRot += body.zRot;  // ADICIONA rotação Z do body
        
        // ADICIONA as posições do body às posições base (não sobrescreve)
        breasts.x += body.x;
        breasts.y += body.y;
        breasts.z += body.z;
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);

        if (target instanceof VillagerEntityBaseModelMCA<T> m) {
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            m.breasts.copyFrom(breasts);
        }
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        renderCommon(matrices, vertices, light, overlay, color);
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public ModelPart getBodyPart() {
        return body;
    }

    @Override
    public Iterable<ModelPart> getCommonHeadParts() {
        return headParts();
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return bodyParts();
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public VillagerDimensions.Mutable getDimensions() {
        return dimensions;
    }

    @Override
    public float getBreastSize() {
        return breastSize;
    }

    @Override
    public void setBreastSize(float breastSize) {
        this.breastSize = breastSize;
    }
}
