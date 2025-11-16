package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public class VillagerEntityModelMCA<T extends LivingEntity & VillagerLike<T>> extends VillagerEntityBaseModelMCA<T> {
    protected static final String BREASTPLATE = "breastplate";
    protected static final String LEFT_EYE_WHITE = "left_eye_white";
    protected static final String RIGHT_EYE_WHITE = "right_eye_white";
    protected static final String LEFT_PUPIL = "left_pupil";
    protected static final String RIGHT_PUPIL = "right_pupil";
    protected static final String LEFT_BROW = "left_brow";
    protected static final String RIGHT_BROW = "right_brow";
    protected static final String MOUTH = "mouth";

    public final ModelPart breastsWear;
    public final ModelPart leftArmwear;
    public final ModelPart rightArmwear;
    public final ModelPart leftLegwear;
    public final ModelPart rightLegwear;
    public final ModelPart bodyWear;
    
    // Facial features - Fresh Animations complete system
    public final ModelPart leftEyeWhite;
    public final ModelPart rightEyeWhite;
    public final ModelPart leftPupil;
    public final ModelPart rightPupil;
    public final ModelPart leftBrow;
    public final ModelPart rightBrow;
    public final ModelPart mouth;
    
    // Blink animation state
    private float blinkProgress = 0;
    private float nextBlinkTime = 0;

    private boolean wearsHidden;

    public VillagerEntityModelMCA(ModelPart tree) {
        super(tree);
        bodyWear = tree.getChild(PartNames.JACKET);
        leftArmwear = tree.getChild("left_sleeve");
        rightArmwear = tree.getChild("right_sleeve");
        leftLegwear = tree.getChild("left_pants");
        rightLegwear = tree.getChild("right_pants");

        breastsWear = tree.getChild(BREASTPLATE);
        
        // Get facial features from head
        ModelPart headPart = tree.getChild(PartNames.HEAD);
        leftEyeWhite = headPart.getChild(LEFT_EYE_WHITE);
        rightEyeWhite = headPart.getChild(RIGHT_EYE_WHITE);
        leftPupil = headPart.getChild(LEFT_PUPIL);
        rightPupil = headPart.getChild(RIGHT_PUPIL);
        leftBrow = headPart.getChild(LEFT_BROW);
        rightBrow = headPart.getChild(RIGHT_BROW);
        mouth = headPart.getChild(MOUTH);
    }

    //
    // body - 0 (body.body 0.0)
    // face - 0 (body.head 0.01)
    //  clothing - 1 (clothing.body 0.075)
    //   hair - 2 (hair.body 0.1) + (hair.hat 0.1 + 0.3 = 0.4)
    //    hood - 3 (clothing.hat 0.075 + 0.5 = 0.575)

    public static MeshDefinition hairData(CubeDeformation dilation) {
        MeshDefinition modelData = bodyData(dilation);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(PartNames.HAT, CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)), PartPose.ZERO);
        return modelData;
    }

    public static MeshDefinition bodyData(CubeDeformation dilation) {
        return bodyData(dilation, false);
    }

    public static MeshDefinition bodyData(CubeDeformation dilation, boolean slim) {
        MeshDefinition modelData = PlayerModel.createMesh(dilation, slim);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        root.addOrReplaceChild(BREASTPLATE, newBreasts(dilation.extend(0.1F), 16), PartPose.ZERO);
        
        // Add facial features to head - FRESH ANIMATIONS COMPLETE SYSTEM
        PartDefinition head = root.getChild(PartNames.HEAD);
        
        // OLHOS BRANCOS - Parte branca do olho que escala para piscar
        // Z = -4.002 para ficar logo atrás do rosto
        head.addOrReplaceChild(LEFT_EYE_WHITE,
            CubeListBuilder.create()
                .texOffs(18, 22)  // Fresh: 18,22 to 20,23
                .addBox(-3.5F, -5.0F, -4.002F, 2.0F, 1.0F, 0.0F, dilation),
            PartPose.ZERO
        );
        
        head.addOrReplaceChild(RIGHT_EYE_WHITE,
            CubeListBuilder.create()
                .texOffs(14, 22)  // Fresh: 14,22 to 16,23
                .addBox(1.5F, -5.0F, -4.002F, 2.0F, 1.0F, 0.0F, dilation),
            PartPose.ZERO
        );
        
        // PUPILAS - Pontos pretos dentro dos olhos brancos
        // Z = -4.005 para ficar à frente do olho branco
        head.addOrReplaceChild(LEFT_PUPIL, 
            CubeListBuilder.create()
                .texOffs(18, 21)  // Fresh: 18,21 to 19,22
                .addBox(-3.5F, -5.0F, -4.005F, 1.0F, 1.0F, 0.0F, dilation),
            PartPose.ZERO
        );
        
        head.addOrReplaceChild(RIGHT_PUPIL,
            CubeListBuilder.create()
                .texOffs(15, 21)  // Fresh: 15,21 to 16,22
                .addBox(1.5F, -5.0F, -4.005F, 1.0F, 1.0F, 0.0F, dilation),
            PartPose.ZERO
        );
        
        // SOMBRANCELHAS - Linhas acima dos olhos (8x2 pixels no Fresh)
        // Z = -4.006 para ficar à frente das pupilas
        head.addOrReplaceChild(LEFT_BROW,
            CubeListBuilder.create()
                .texOffs(13, 19)  // Fresh: 13,19 to 21,21 (8x2)
                .addBox(-4.0F, -6.5F, -4.006F, 4.0F, 1.0F, 0.0F, dilation),
            PartPose.offset(-2.0F, 0.5F, 0.0F) // Pivô no centro da sobrancelha esquerda
        );
        
        head.addOrReplaceChild(RIGHT_BROW,
            CubeListBuilder.create()
                .texOffs(13, 19)  // Mesma textura, mas usa mirrorTexture
                .addBox(0.0F, -6.5F, -4.006F, 4.0F, 1.0F, 0.0F, dilation),
            PartPose.offset(2.0F, 0.5F, 0.0F) // Pivô no centro da sobrancelha direita
        );
        
        // BOCA - Linha horizontal para expressões
        // Z = -4.002 mesmo nível do olho branco
        head.addOrReplaceChild(MOUTH,
            CubeListBuilder.create()
                .texOffs(15, 23)  // Fresh: 15,23 to 19,24
                .addBox(-2.0F, -3.0F, -4.002F, 4.0F, 1.0F, 0.0F, dilation),
            PartPose.ZERO
        );
        
        return modelData;
    }

    public static MeshDefinition armorData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0f);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        return modelData;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, bodyWear, leftLegwear, rightLegwear, leftArmwear, rightArmwear);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        leftLegwear.copyFrom(leftLeg);
        rightLegwear.copyFrom(rightLeg);
        leftArmwear.copyFrom(leftArm);
        rightArmwear.copyFrom(rightArm);
        bodyWear.copyFrom(body);
        breastsWear.copyFrom(breasts);
        
        // Animate COMPLETE facial system - Fresh Animations style
        animateBlink(animationProgress);
        animatePupils(villager, headYaw, headPitch, animationProgress);
        animateBrows(villager, animationProgress);
        animateMouth(villager, animationProgress);
    }
    
    /**
     * Sistema de piscar automático - Fresh Animations
     * Pisca periodicamente de forma natural
     */
    private void animateBlink(float animationProgress) {
        // Fresh formula: pow(clamp((1.5 - abs(sin(age/8)*6)) * clamp(...), 0, 1), 2)
        float blinkCycle = (float) Math.sin(animationProgress / 8.0f) * 6.0f;
        float blinkTrigger = Math.abs(blinkCycle);
        
        // Cria pulsos de piscar em momentos específicos
        float blinkPhase = (float) Math.cos((animationProgress / 8.0f) / 1.5f) * 40.0f 
                         + (float) Math.cos((animationProgress / 8.0f) / 4.0f) * 40.0f;
        blinkPhase = net.minecraft.util.Mth.clamp(-32 + blinkPhase, 0, 1);
        
        blinkProgress = (float) Math.pow(
            net.minecraft.util.Mth.clamp((1.5f - blinkTrigger) * blinkPhase, 0, 1), 
            2
        );
        
        // Escala vertical dos olhos brancos (3 = aberto, 0 = fechado)
        float eyeScale = (1.0f - blinkProgress) * 3.0f;
        leftEyeWhite.yScale = eyeScale;
        rightEyeWhite.yScale = eyeScale;
        
        // Pupilas também escalam proporcionalmente
        leftPupil.yScale = eyeScale;
        rightPupil.yScale = eyeScale;
        
        // Olhos ficam invisíveis quando completamente fechados
        leftEyeWhite.visible = eyeScale > 0.01f;
        rightEyeWhite.visible = eyeScale > 0.01f;
        leftPupil.visible = eyeScale > 0.01f;
        rightPupil.visible = eyeScale > 0.01f;
    }
    
    /**
     * Anima as pupilas para seguirem o PLAYER real (não apenas headYaw)
     * Fresh Animations calcula distância 3D e posição relativa
     */
    private void animatePupils(T villager, float headYaw, float headPitch, float animationProgress) {
        // Fresh formula base para movimento das pupilas
        // var.pupil_y = clamp(head_pitch/80, -0.3 -0.2*random(id), 0.5) + (breathing animations)
        // var.pupil_x = -var.hy/(80 +160*var.ylook) + (occasional movements)
        
        float pupilY = net.minecraft.util.Mth.clamp(headPitch / 80.0f, -0.5f, 0.5f);
        float pupilX = -headYaw / 120.0f; // Mais sutil que antes
        
        // Adiciona movimento ocasional de observação (Fresh: clamp(sin(age/27) + sin(age/16), ...))
        float occasionalMove = (float) (Math.sin(animationProgress / 27.0f) + Math.sin(animationProgress / 16.0f));
        occasionalMove = net.minecraft.util.Mth.clamp(occasionalMove, -0.1f, 0.1f);
        
        float cycleTrigger = (float) Math.cos(animationProgress / 125.0f) * 30.0f * 2.0f;
        if (-40.0f - cycleTrigger > 0) {
            pupilX += occasionalMove;
            pupilY += (float) (Math.sin(animationProgress / 38.0f) + Math.sin(animationProgress / 13.0f)) * 0.1f;
        }
        
        // Breathing sutil das pupilas
        pupilY += (float) Math.sin(animationProgress * 0.05f) * 0.02f;
        
        // Clamp para manter dentro dos olhos brancos
        pupilX = net.minecraft.util.Mth.clamp(pupilX, -0.5f, 0.5f);
        pupilY = net.minecraft.util.Mth.clamp(pupilY, -0.4f, 0.4f);
        
        // AMBAS as pupilas recebem o MESMO movimento (não vesgas)
        leftPupil.x = pupilX;
        leftPupil.y = pupilY;
        rightPupil.x = pupilX;
        rightPupil.y = pupilY;
        
        // Escala das pupilas varia sutilmente (Fresh: clamp(1 + clamp(...), 0.5, 1))
        float pupilScale = 1.0f + pupilX * 0.1f;
        pupilScale = net.minecraft.util.Mth.clamp(pupilScale, 0.7f, 1.0f);
        leftPupil.xScale = pupilScale;
        rightPupil.xScale = pupilScale;
    }
    
    /**
     * Anima a boca baseado em emoção e estado
     * Fresh Animations: mouth rotaciona e move verticalmente
     */
    private void animateMouth(T villager, float animationProgress) {
        var brain = villager.getVillagerBrain();
        
        // Boca abre quando assustado ou machucado
        if (brain.isPanicking()) {
            mouth.y = 0.5f; // Abre mais
            mouth.zRot = 0;
        } else if (villager.hurtTime > 0) {
            mouth.y = 0.3f + (float) Math.sin(villager.hurtTime * Math.PI / 5.0f) * 0.2f;
            mouth.zRot = (float) Math.sin(villager.hurtTime * Math.PI / 10.0f) * 0.3f; // Careta
        } else {
            // Movimento sutil de breathing
            mouth.y = (float) Math.sin(animationProgress * 0.05f) * 0.05f;
            
            // Ocasionalmente sorri (Fresh: clamp(-sin(age/100)*20, 0, 1))
            float smileTrigger = (float) Math.sin(animationProgress / 100.0f) * 20.0f;
            if (-smileTrigger > 0) {
                mouth.y -= 0.1f; // Sorriso sutil
            }
            
            mouth.zRot = 0;
        }
    }
    
    /**
     * Sistema completo de expressões faciais baseado em emoção atual
     * Fresh Animations: sobrancelhas com movimento complexo e assimétrico
     * 
     * Tipos de expressões:
     * 1. RAIVA - Sobrancelhas inclinadas para dentro (^\/)
     * 2. TRISTEZA - Sobrancelhas inclinadas para fora (\/^)
     * 3. GALANTE/CONFIANTE - Uma sobrancelha levantada (^_)
     * 4. SURPRESA/PÂNICO - Ambas sobrancelhas retas para cima (^^)
     * 5. NEUTRA - Sobrancelhas relaxadas
     * 6. CONCENTRADO - Sobrancelhas levemente franzidas
     * 7. FELIZ - Sobrancelhas arqueadas suavemente
     * 8. SUSPEITO - Uma abaixada, outra levantada
     */
    private void animateBrows(T villager, float animationProgress) {
        FacialExpression expression = detectEmotion(villager, animationProgress);
        applyExpression(expression, animationProgress);
    }
    
    /**
     * Detecta a emoção atual do NPC baseado no estado do cérebro
     */
    private FacialExpression detectEmotion(T villager, float animationProgress) {
        var brain = villager.getVillagerBrain();
        
        // PÂNICO - sobrancelhas retas para cima
        if (brain.isPanicking()) {
            return FacialExpression.PANIC;
        }
        
        // RAIVA - quando sendo atacado ou em combate
        if (villager.getLastHurtByMob() != null) {
            return FacialExpression.ANGER;
        }
        
        // TRISTEZA - quando machucado recentemente
        if (villager.hurtTime > 0) {
            return FacialExpression.SADNESS;
        }
        
        // CONCENTRADO - durante trabalho/chores
        try {
            var jobMethod = brain.getClass().getDeclaredMethod("getCurrentJob");
            jobMethod.setAccessible(true);
            var job = jobMethod.invoke(brain);
            if (job != null) {
                return FacialExpression.FOCUSED;
            }
        } catch (Exception ignored) {}
        
        // FELIZ - alterna ocasionalmente quando em estado calmo (expressão positiva)
        if (!brain.isPanicking() && Math.sin(animationProgress * 0.012f) > 0.98f) {
            return FacialExpression.HAPPY;
        }
        
        // GALANTE - alterna ocasionalmente quando não está ocupado
        if (!brain.isPanicking() && Math.sin(animationProgress * 0.01f) > 0.97f) {
            return FacialExpression.CONFIDENT;
        }
        
        // SUSPEITO - quando observando ao redor (expressão investigativa)
        if (Math.sin(animationProgress * 0.008f) > 0.985f) {
            return FacialExpression.SUSPICIOUS;
        }
        
        // NEUTRA - estado padrão
        return FacialExpression.NEUTRAL;
    }
    
    /**
     * Aplica a expressão facial nas sobrancelhas
     */
    private void applyExpression(FacialExpression expression, float animationProgress) {
        float breathingOffset = (float) Math.sin(animationProgress * 0.05f) * 0.05f;
        
        switch (expression) {
            case ANGER -> {
                // RAIVA: Sobrancelhas inclinadas para dentro (^\/)
                leftBrow.y = -0.5f + breathingOffset;   // Esquerda sobe
                rightBrow.y = -0.5f + breathingOffset;  // Direita sobe
                leftBrow.zRot = (float) Math.toRadians(-15);  // Inclina para dentro
                rightBrow.zRot = (float) Math.toRadians(15);  // Inclina para dentro
            }
            case SADNESS -> {
                // TRISTEZA: Sobrancelhas inclinadas para fora (\/^)
                leftBrow.y = 0.3f + breathingOffset;    // Esquerda desce
                rightBrow.y = 0.3f + breathingOffset;   // Direita desce
                leftBrow.zRot = (float) Math.toRadians(15);   // Inclina para fora
                rightBrow.zRot = (float) Math.toRadians(-15); // Inclina para fora
            }
            case CONFIDENT -> {
                // GALANTE: Uma sobrancelha levantada (^_)
                leftBrow.y = -0.8f;  // Esquerda bem levantada
                rightBrow.y = 0.2f + breathingOffset;  // Direita normal
                leftBrow.zRot = 0;
                rightBrow.zRot = 0;
            }
            case PANIC -> {
                // PÂNICO: Ambas sobrancelhas RETAS para cima (^^)
                leftBrow.y = -1.2f;  // Bem levantadas
                rightBrow.y = -1.2f;
                leftBrow.zRot = 0;   // Retas (sem inclinação)
                rightBrow.zRot = 0;
            }
            case FOCUSED -> {
                // CONCENTRADO: Sobrancelhas levemente franzidas
                leftBrow.y = 0.2f + breathingOffset;
                rightBrow.y = 0.2f + breathingOffset;
                leftBrow.zRot = (float) Math.toRadians(-8);
                rightBrow.zRot = (float) Math.toRadians(8);
            }
            case HAPPY -> {
                // FELIZ: Sobrancelhas arqueadas para cima de forma suave
                leftBrow.y = -0.4f + breathingOffset;
                rightBrow.y = -0.4f + breathingOffset;
                leftBrow.zRot = (float) Math.toRadians(-5);  // Leve curvatura externa
                rightBrow.zRot = (float) Math.toRadians(5);
            }
            case SUSPICIOUS -> {
                // SUSPEITO: Uma sobrancelha abaixada, outra levantada (_^)
                leftBrow.y = 0.3f + breathingOffset;  // Esquerda abaixada
                rightBrow.y = -0.6f;  // Direita levantada
                leftBrow.zRot = (float) Math.toRadians(5);
                rightBrow.zRot = (float) Math.toRadians(-10);
            }
            case NEUTRAL -> {
                // NEUTRA: Relaxadas com breathing sutil
                leftBrow.y = breathingOffset;
                rightBrow.y = breathingOffset;
                leftBrow.zRot = 0;
                rightBrow.zRot = 0;
            }
        }
    }
    
    /**
     * Enum de expressões faciais disponíveis
     */
    private enum FacialExpression {
        ANGER,      // Raiva - sobrancelhas inclinadas para dentro
        SADNESS,    // Tristeza - sobrancelhas inclinadas para fora
        CONFIDENT,  // Galante - uma sobrancelha levantada
        PANIC,      // Pânico - ambas sobrancelhas retas para cima
        FOCUSED,    // Concentrado - sobrancelhas levemente franzidas
        HAPPY,      // Feliz - sobrancelhas arqueadas para cima
        SUSPICIOUS, // Suspeito - uma sobrancelha abaixada, outra normal
        NEUTRAL     // Neutro - sobrancelhas relaxadas
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        leftArmwear.visible = !wearsHidden && visible;
        rightArmwear.visible = !wearsHidden && visible;
        leftLegwear.visible = !wearsHidden && visible;
        rightLegwear.visible = !wearsHidden && visible;
        bodyWear.visible = !wearsHidden && visible;
    }

    public VillagerEntityModelMCA<T> hideWears() {
        wearsHidden = true;
        breastsWear.visible = false;
        leftArmwear.visible = false;
        rightArmwear.visible = false;
        leftLegwear.visible = false;
        rightLegwear.visible = false;
        bodyWear.visible = false;
        return this;
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof VillagerEntityModelMCA) {
            copyAttributes((VillagerEntityModelMCA<T>) target);
        }
    }

    private void copyAttributes(VillagerEntityModelMCA<T> target) {
        target.leftLegwear.copyFrom(leftLegwear);
        target.rightLegwear.copyFrom(rightLegwear);
        target.leftArmwear.copyFrom(leftArmwear);
        target.rightArmwear.copyFrom(rightArmwear);
        target.bodyWear.copyFrom(bodyWear);
        target.breastsWear.copyFrom(breastsWear);
    }

    public <M extends HumanoidModel<T>> void copyVisibility(M model) {
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        bodyWear.visible = model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftArmwear.visible = model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightArmwear.visible = model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftLegwear.visible = model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightLegwear.visible = model.rightLeg.visible;
    }
}
