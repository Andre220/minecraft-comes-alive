package net.conczin.mca.client.model.animation;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.animation.AnimationMath.*;

/**
 * Applies Fresh Animations-style animations to villager model parts.
 * This class translates the mathematical expressions from Fresh Animations' .jpm files into Java code.
 * 
 * Based on Fresh Animations by FreshLX
 * Adapted from: villager_animations.jpm, male_villager_animations.jpm, female_villager_animations.jpm
 */
public class FreshVillagerAnimations {
    
    /**
     * Applies all Fresh Animations to the villager model
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyAnimations(
            VillagerAnimationState<T> state,
            ModelPart head, ModelPart hat,
            ModelPart body,
            ModelPart rightArm, ModelPart leftArm,
            ModelPart rightLeg, ModelPart leftLeg
    ) {
        // HIGHEST PRIORITY: Jump animation (overrides everything)
        if (state.isJumping()) {
            animateJump(state, rightArm, leftArm, rightLeg, leftLeg, head, body);
            // Hat copies head transformations
            if (hat != null) {
                hat.copyFrom(head);
             }
            return; // Skip other animations during jump
        }
        
        // Apply animations based on state
        animateHead(state, head);
        animateBody(state, head, body);
        animateArms(state, rightArm, leftArm);
        animateLegs(state, rightLeg, leftLeg);
        
        // MCA-specific animations (applied on top of base animations)
        // Priority: Chores > Trading > Interactions > Movement states
        
        if (state.currentChore != null && state.currentChore != net.conczin.mca.entity.ai.Chore.NONE) {
            applyChoreAnimation(state, head, body, rightArm, leftArm, rightLeg, leftLeg);
        } else if (state.isTrading) {
            applyTradingAnimation(state, head, rightArm, leftArm);
        } else if (state.isFollowing) {
            applyFollowingAnimation(state, head, body);
        } else if (state.moveState != null && state.moveState == net.conczin.mca.entity.ai.MoveState.STAY) {
            applyStayingAnimation(state, head, body, rightArm, leftArm);
        }
        
        // Hat copies head transformations (if hat exists)
        if (hat != null) {
            hat.copyFrom(head);
        }
    }
    
    /**
     * Animates the head with Fresh Animations style
     */
    private static <T extends LivingEntity & VillagerLike<T>> void animateHead(
            VillagerAnimationState<T> state, ModelPart head) {
        
        // Base head rotation from pitch/yaw
        float baseRotX = toRad(state.getHeadPitch()) / 1.5f;
        float baseRotY = toRad(state.getHeadYaw()) / 1.8f;
        float baseRotZ = 0;
        
        // Swimming animation
        if (state.isSwimming) {
            baseRotX += Mth.cos(state.swim * 2.0f) / 30.0f + toRad(-10) 
                      + toRad(10.0f * clamp(state.getLimbSpeed() * 8.0f, 0, 1));
        }
        
        // ENHANCED: Scared/panicking animation (head tilts down more dramatically)
        if (state.isPanicking() || state.scared > 0) {
            float scaredAmount = state.scared > 0 ? state.scared : clamp(Mth.sin(state.getLimbSpeed() * 3.0f), 0, 1);
            baseRotX += -toRad(15) * scaredAmount; // Was -toRad(10), now -15 for more dramatic head down
            
            // ENHANCED: Head shake when scared
            baseRotY += Mth.sin(state.ls * 3.0f) * 0.15f * scaredAmount; // NEW: head wobble when scared
        }
        
        // Walking/running head bob (more intense during running)
        baseRotX += Mth.sin(state.ls * 2.0f) / 20.0f * state.getLimbSpeed();
        baseRotX += Mth.cos(state.ls * 2.0f) / 12.0f * state.run; // NEW: additional bob during running
        
        // Hurt animation
        float hurtMult = state.getHurtMultiplier();
        if (hurtMult != 0) {
            baseRotX += (Mth.sin(state.getLimbSwing() / 2.0f) / 4.0f + toRad(20)) * hurtMult;
            baseRotZ += Mth.cos(state.getLimbSwing() / 3.0f) / 3.0f * hurtMult;
        }
        
        // Breathing animation when idle
        baseRotX += state.getBreathingOffset();
        
        // Idle head sway (var.nov1 - only some villagers do this)
        if (state.getLimbSpeed() < 0.1f && state.hasRandomBehavior(0.0f, 0.4f)) {
            float idleIntensity = clamp(1.0f - state.getLimbSpeed() * 4.0f, 0, 1);
            baseRotY += Mth.sin(state.r + state.ageInTicks / 10.0f + Mth.sin(state.r * 2.0f + state.ageInTicks / 5.0f) / 2.0f) / 6.0f * idleIntensity * 0.4f;
            baseRotZ += Mth.sin(state.r + state.ageInTicks / 10.0f + Mth.sin(state.r * 2.0f + state.ageInTicks / 5.0f) / 2.0f) / 20.0f * idleIntensity * 0.4f;
        }
        
        // Apply rotations
        head.xRot = baseRotX;
        head.yRot = baseRotY;
        head.zRot = baseRotZ;
        
        // Head position adjustments
        head.x = Mth.sin((float) Math.PI / 4.0f + state.ls) / 3.0f * state.getLimbSpeed();
        
        // FIXED: Use age-appropriate scaling instead of just isChild
        // Teen/child have different body proportions that need adjustment
        float headBobIntensity = state.isChild ? 2.4f : 1.2f;
        float walkDivider = state.isChild ? 1.0f : 3.0f;
        
        float headY = 0.3f;
        if (state.isSwimming) {
            headY = -Mth.sin(state.swim * 2.0f) / 2.0f;
        }
        
        // FIXED: Reduced intensity to prevent head from leaving body
        headY += (state.getLimbSpeed() * (state.isChild ? 2.0f : 0.8f) // Was 3.0 / 1.0, now 2.0 / 0.8
                - Mth.cos(-(float) Math.PI / 4.0f + state.ls * 2.0f - Mth.sin(state.ls * 2.0f) / 4.0f)) 
                / walkDivider * state.walk;
        
        // FIXED: Running head bob (reduced for teens/adults)
        headY += -Mth.sin(toRad(state.isChild || state.getLimbSpeed() > 0.75f ? -45 : 45) 
                + state.ls * 2.0f + Mth.cos(state.ls * 2.0f) / 3.0f) 
                * headBobIntensity * state.run * 0.7f; // ADDED: 0.7x multiplier to reduce intensity
        
        headY += Mth.sin(state.r + state.b) / 6.0f;
        
        head.y = headY;
        
        float headZ = 0;
        if (state.isSwimming) {
            headZ = -1.0f * clamp(state.getLimbSpeed() * 8.0f, 0, 1);
        } else {
            headZ = -clamp(state.getLimbSpeed() * 2.0f, 0, 1) * (state.isChild ? 0.5f : 0.0f);
            headZ -= Mth.sin(state.r + state.b) / 6.0f;
            headZ += clamp(toRad(-state.getHeadPitch()) * 3.0f, -2, 0) / 2.0f;
        }
        
        if (hurtMult != 0) {
            headZ += (Mth.sin(state.getLimbSwing() / 2.0f) + 2.0f) * hurtMult;
        }
        
        head.z = headZ;
    }
    
    /**
     * Animates the body with Fresh Animations style
     */
    private static <T extends LivingEntity & VillagerLike<T>> void animateBody(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body) {
        
        float bodyRotX = 0;
        float bodyRotY = 0;
        float bodyRotZ = 0;
        
        // Swimming animation - EXATAMENTE como Fresh Animations original
        if (state.isSwimming) {
            // Fresh formula: torad(20)*clamp(limb_speed*8, 0, 1)
            bodyRotX = toRad(20) * clamp(state.getLimbSpeed() * 8.0f, 0, 1);
            // Fresh formula: sin(var.swim)/8
            bodyRotY = Mth.sin(state.swim) / 8.0f;
            // Fresh formula: sin(var.swim)/30*clamp(limb_speed*8, 0, 1)
            bodyRotZ = Mth.sin(state.swim) / 30.0f * clamp(state.getLimbSpeed() * 8.0f, 0, 1);
        } else {
            // Walking/running body lean
            bodyRotX = Mth.sin(state.r + state.b) / 60.0f;
            
            // ENHANCED: Running body lean (dramatic forward lean when running)
            bodyRotX += Mth.cos(state.ls * 2.0f) / 18.0f * state.getLimbSpeed() * state.run * (state.hasRandomBehavior(0.4f, 0.5f) ? 1.0f : 0.0f);
            
            if (state.hasRandomBehavior(0.4f, 0.5f)) {
                // ENHANCED: Aggressive forward lean during sprint
                bodyRotX += toRad(15) * state.getLimbSpeed() * (1.0f + state.run * 0.5f); // Was toRad(10), now 15
            } else {
                bodyRotX += toRad(-5) * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1) * clamp(-1.0f + state.randomFactor * 2.0f, 0, 1);
            }
            
            bodyRotX += clamp(toRad(state.getHeadPitch()) / 4.0f, 0, (float) Math.PI / 20.0f) / 3.0f;
            
            // ENHANCED: Body rotation during movement (side-to-side sway when running)
            bodyRotY = -Mth.sin(state.ls) / 3.5f * state.getLimbSpeed() * state.run * (state.hasRandomBehavior(0.4f, 0.5f) ? 0.0f : 1.0f); // Was /4.0f, now /3.5f for more sway
            bodyRotY += clamp(toRad(state.getHeadYaw()) / 12.0f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1), -(float) Math.PI / 16.0f, (float) Math.PI / 16.0f);
            
            // ENHANCED: Z-axis tilt during running (twisting motion)
            bodyRotZ = Mth.sin(state.ls) / 15.0f * state.getLimbSpeed() * state.run; // NEW: side tilt when running
            
            // Note: Removed idle body sway (var.nov3) - was causing unwanted lateral movement
            // The original Fresh Animations uses this only for specific random behaviors, not general idle
        }
        
        // Hurt animation
        float hurtMult = state.getHurtMultiplier();
        if (hurtMult != 0) {
            bodyRotX -= (Mth.sin(state.getLimbSwing() / 2.0f) / 2.0f + toRad(20)) / 3.0f * hurtMult;
            bodyRotZ -= Mth.cos(state.getLimbSwing() / 3.0f) / 3.0f * hurtMult;
        }
        
        body.xRot = bodyRotX;
        body.yRot = bodyRotY;
        body.zRot = bodyRotZ;
        
        // Body position - SEGUINDO Fresh Animations com bobbing AUMENTADO 2X
        // Fresh formula: body.ty = head.ty +(0.15*limb_speed) -cos(torad(if(limb_speed>0.5, -45, 45))+var.ls*2 +cos(var.ls*2)/2)/1.5/2*limb_speed
        // AUMENTADO 2X para movimento vertical mais dramático nos passos
        float headY = head.y;
        body.y = headY + (0.3f * state.getLimbSpeed()); // 0.15 -> 0.3 (2x)
        
        // Subtract the complex bobbing formula - AUMENTADO 2X
        float bodyAngle = toRad(state.getLimbSpeed() > 0.5f ? -45 : 45);
        body.y -= Mth.cos(bodyAngle + state.ls * 2.0f + Mth.cos(state.ls * 2.0f) / 2.0f) / 0.75f / 2.0f * state.getLimbSpeed(); // /1.5 -> /0.75 (2x)
        
        // Body Z position - Fresh formula: body.tz = head.tz -clamp(torad(-head_pitch)*3, -2, 0)/4
        float headZ = head.z;
        body.z = headZ - clamp(toRad(-state.getHeadPitch()) * 3.0f, -2, 0) / 4.0f;
        
        // Body X position - Fresh formula: body.tx = head.tx
        body.x = head.x;
    }
    
    /**
     * Animates the arms with Fresh Animations style
     */
    private static <T extends LivingEntity & VillagerLike<T>> void animateArms(
            VillagerAnimationState<T> state, ModelPart rightArm, ModelPart leftArm) {
        
        float rightArmRotX, leftArmRotX;
        float armRotY, armRotZ;
        
        // Swimming animation
        if (state.isSwimming) {
            // Fresh formula: cos(var.swim*2)/8 +torad(-40)
            rightArmRotX = Mth.cos(state.swim * 2.0f) / 8.0f + toRad(-40);
            leftArmRotX = Mth.cos(state.swim * 2.0f) / 8.0f + toRad(-40);
            // Fresh formula: sin(var.swim)/8
            armRotY = Mth.sin(state.swim) / 8.0f;
        } else {
            // Fresh Animations ORIGINAL formula para braços:
            // right_arm.rx: asin( sin(var.ls))/2.8*(0.2+0.8*var.walk) +( sin(var.ls)/(1.2-(0.15*var.walk))/1.8 )*var.run +clamp(-cos(var.ls)/2*clamp(limb_speed*1.5, 0, 1), 0, pi/4)/(1+(9*var.walk)) +torad(15)/(3+(3*var.walk))*clamp(-0.5+limb_speed*2, 0, 1) ) +torad(2)*random(id)
            // left_arm.rx:  asin(-sin(var.ls))/2.8*(0.2+0.8*var.walk) +(-sin(var.ls)/(1.2-(0.15*var.walk))/1.8 )*var.run +clamp( cos(var.ls)/2*clamp(limb_speed*1.5, 0, 1), 0, pi/4)/(1+(9*var.walk)) +torad(15)/(3+(3*var.walk))*clamp(-0.5+limb_speed*2, 0, 1) ) +torad(2)*random(id)
            
            // RIGHT ARM
            rightArmRotX = (float) Math.asin(Mth.sin(state.ls)) / 2.8f * (0.2f + 0.8f * state.walk);  // Walking component
            rightArmRotX += (Mth.sin(state.ls) / (1.2f - (0.15f * state.walk)) / 1.8f) * state.run;   // Running component (MAIS DRAMÁTICO)
            rightArmRotX += clamp(-Mth.cos(state.ls) / 2.0f * clamp(state.getLimbSpeed() * 1.5f, 0, 1), 0, (float) Math.PI / 4.0f) / (1.0f + 9.0f * state.walk);
            rightArmRotX += toRad(15) / (3.0f + 3.0f * state.walk) * clamp(-0.5f + state.getLimbSpeed() * 2.0f, 0, 1);
            rightArmRotX += toRad(2) * state.randomFactor;
            
            // LEFT ARM (inverted)
            leftArmRotX = (float) Math.asin(-Mth.sin(state.ls)) / 2.8f * (0.2f + 0.8f * state.walk);  // Walking component
            leftArmRotX += (-Mth.sin(state.ls) / (1.2f - (0.15f * state.walk)) / 1.8f) * state.run;  // Running component (MAIS DRAMÁTICO)
            leftArmRotX += clamp(Mth.cos(state.ls) / 2.0f * clamp(state.getLimbSpeed() * 1.5f, 0, 1), 0, (float) Math.PI / 4.0f) / (1.0f + 9.0f * state.walk);
            leftArmRotX += toRad(15) / (3.0f + 3.0f * state.walk) * clamp(-0.5f + state.getLimbSpeed() * 2.0f, 0, 1);
            leftArmRotX += toRad(2) * state.randomFactor;
            
            // Y rotation - Fresh formula: clamp(torad(var.hy)/12*clamp(1-limb_speed*2, 0, 1), -pi/16, pi/16)
            armRotY = clamp(toRad(state.getHeadYaw()) / 12.0f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1), -(float) Math.PI / 16.0f, (float) Math.PI / 16.0f);
        }
        
        // Hurt animation
        float hurtMult = state.getHurtMultiplier();
        if (hurtMult != 0) {
            rightArmRotX += (Mth.sin(state.getLimbSwing() / 2.0f) / 4.0f + toRad(20)) * hurtMult;
            leftArmRotX += (-Mth.sin(state.getLimbSwing() / 2.0f) / 4.0f + toRad(20)) * hurtMult;
        }
        
        // Apply arm rotations
        rightArm.xRot = rightArmRotX;
        leftArm.xRot = leftArmRotX;
        rightArm.yRot = armRotY;
        leftArm.yRot = armRotY;
        
        // Body rotation affects arms
        armRotZ = state.getVillager().isCrouching() ? 0 : 0; // Body.rz would be copied here
        rightArm.zRot = armRotZ;
        leftArm.zRot = armRotZ;
        
        // NOVO: Movimento vertical dos ombros durante caminhada/corrida (2x mais dramático)
        // Os ombros sobem e descem alternadamente a cada passo
        float shoulderBob = Mth.sin(state.ls) * 0.5f * state.getLimbSpeed(); // 2x maior que Fresh original
        rightArm.y = -shoulderBob; // Braço direito sobe quando esquerdo desce
        leftArm.y = shoulderBob;   // Braço esquerdo desce quando direito sobe
    }
    
    /**
     * Animates the legs with Fresh Animations style
     */
    private static <T extends LivingEntity & VillagerLike<T>> void animateLegs(
            VillagerAnimationState<T> state, ModelPart rightLeg, ModelPart leftLeg) {
        
        // Don't animate legs while riding
        if (state.isRiding) {
            rightLeg.xRot = 0;
            leftLeg.xRot = 0;
            rightLeg.yRot = 0;
            leftLeg.yRot = 0;
            rightLeg.zRot = 0;
            leftLeg.zRot = 0;
            return;
        }
        
        // Don't animate legs while swimming - Fresh Animations zeroes them out
        if (state.isSwimming) {
            rightLeg.xRot = 0;
            leftLeg.xRot = 0;
            rightLeg.yRot = 0;
            leftLeg.yRot = 0;
            rightLeg.zRot = 0;
            leftLeg.zRot = 0;
            return;
        }
        
        // Fresh Animations ORIGINAL formula para pernas (IGUAL AOS BRAÇOS mas com offset de cabeça):
        // right_leg.rx: (torad(1)+sin(var.r +var.b)/60)*clamp(1-limb_speed*6, 0, 1) +asin( sin(var.ls))/2.8*(0.2+0.8*var.walk) +( sin(var.ls)/(1.2-(0.15*var.walk))/1.8 )*var.run +clamp(-cos(var.ls)/2*clamp(limb_speed*1.5, 0, 1), 0, pi/4)/(1+(9*var.walk)) +torad(15)/(3+(3*var.walk))*clamp(-0.5+limb_speed*2, 0, 1) -torad(var.hy)/30*clamp(1-limb_speed*2, 0, 1) ) +torad(2)*random(id)
        // left_leg.rx:  (torad(1)+sin(var.r +var.b)/60)*clamp(1-limb_speed*6, 0, 1) +asin(-sin(var.ls))/2.8*(0.2+0.8*var.walk) +(-sin(var.ls)/(1.2-(0.15*var.walk))/1.8 )*var.run +clamp( cos(var.ls)/2*clamp(limb_speed*1.5, 0, 1), 0, pi/4)/(1+(9*var.walk)) +torad(15)/(3+(3*var.walk))*clamp(-0.5+limb_speed*2, 0, 1) +torad(var.hy)/30*clamp(1-limb_speed*2, 0, 1) ) +torad(2)*random(id)
        
        // Base idle rotation
        float baseRotX = (toRad(1) + Mth.sin(state.r + state.b) / 60.0f) * clamp(1.0f - state.getLimbSpeed() * 6.0f, 0, 1);
        
        // RIGHT LEG
        float rightLegRotX = baseRotX;
        rightLegRotX += (float) Math.asin(Mth.sin(state.ls)) / 2.8f * (0.2f + 0.8f * state.walk);     // Walking (DRAMÁTICO)
        rightLegRotX += (Mth.sin(state.ls) / (1.2f - (0.15f * state.walk)) / 1.8f) * state.run;      // Running (MUITO DRAMÁTICO)
        rightLegRotX += clamp(-Mth.cos(state.ls) / 2.0f * clamp(state.getLimbSpeed() * 1.5f, 0, 1), 0, (float) Math.PI / 4.0f) / (1.0f + 9.0f * state.walk);
        rightLegRotX += toRad(15) / (3.0f + 3.0f * state.walk) * clamp(-0.5f + state.getLimbSpeed() * 2.0f, 0, 1);
        rightLegRotX -= toRad(state.getHeadYaw()) / 30.0f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1);
        rightLegRotX += toRad(2) * state.randomFactor;
        
        // LEFT LEG (inverted)
        float leftLegRotX = baseRotX;
        leftLegRotX += (float) Math.asin(-Mth.sin(state.ls)) / 2.8f * (0.2f + 0.8f * state.walk);    // Walking (DRAMÁTICO)
        leftLegRotX += (-Mth.sin(state.ls) / (1.2f - (0.15f * state.walk)) / 1.8f) * state.run;     // Running (MUITO DRAMÁTICO)
        leftLegRotX += clamp(Mth.cos(state.ls) / 2.0f * clamp(state.getLimbSpeed() * 1.5f, 0, 1), 0, (float) Math.PI / 4.0f) / (1.0f + 9.0f * state.walk);
        leftLegRotX += toRad(15) / (3.0f + 3.0f * state.walk) * clamp(-0.5f + state.getLimbSpeed() * 2.0f, 0, 1);
        leftLegRotX += toRad(state.getHeadYaw()) / 30.0f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1);
        leftLegRotX += toRad(2) * state.randomFactor;
        
        rightLeg.xRot = rightLegRotX;
        leftLeg.xRot = leftLegRotX;
        
        // Y rotation - Fresh formula: torad(var.hy)/6 /2*clamp(1-limb_speed*2, 0, 1) +torad( 8-5*random(id))*clamp(1-limb_speed*4, 0, 1)
        float legRotY = toRad(state.getHeadYaw()) / 6.0f / 2.0f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1);
        rightLeg.yRot = legRotY + toRad(8.0f - 5.0f * state.randomFactor) * clamp(1.0f - state.getLimbSpeed() * 4.0f, 0, 1);
        leftLeg.yRot = legRotY + toRad(-8.0f + 5.0f * state.randomFactor) * clamp(1.0f - state.getLimbSpeed() * 4.0f, 0, 1);
        
        // Z rotation - Fresh formula: -torad(var.hy)/45/2.5*clamp(1-limb_speed*2, 0, 1) +(head.tx -cos(-pi/4 +var.ls +cos(var.ls*2)/6)*1.5*var.dance)/10
        float legRotZ = -toRad(state.getHeadYaw()) / 45.0f / 2.5f * clamp(1.0f - state.getLimbSpeed() * 2.0f, 0, 1);
        // Note: dance animation part omitted for now
        rightLeg.zRot = legRotZ;
        leftLeg.zRot = legRotZ;
    }
    
    /**
     * Applies jumping animation - MARIO STYLE! 🍄
     * Creates a dramatic X-pose with legs WIDE open like Mario's iconic jump
     */
    public static void animateJump(VillagerAnimationState<?> state, ModelPart rightArm, ModelPart leftArm, 
                                     ModelPart rightLeg, ModelPart leftLeg, ModelPart head, ModelPart body) {
        if (!state.isJumping()) {
            return; // Only apply when actually jumping
        }
        
        // Jump intensity based on velocity - mais dramático no ápice do pulo
        float jumpIntensity = clamp(state.getVelocityY() * 2.5f, 0.0f, 1.0f);
        
        // 🦵 PERNAS BEM ABERTAS EM X - ESTILO MARIO!
        // Perna direita para frente E PARA FORA
        rightLeg.xRot = toRad(85) * jumpIntensity;   // Forward MUITO (45->85)
        rightLeg.yRot = toRad(35) * jumpIntensity;   // Para FORA (abertura lateral)
        rightLeg.zRot = toRad(15) * jumpIntensity;   // Rotação extra para dramaticidade
        
        // Perna esquerda para trás E PARA FORA
        leftLeg.xRot = toRad(-85) * jumpIntensity;   // Back MUITO (45->85)
        leftLeg.yRot = toRad(-35) * jumpIntensity;   // Para FORA (abertura lateral)
        leftLeg.zRot = toRad(-15) * jumpIntensity;   // Rotação extra espelhada
        
        // 💪 BRAÇOS ABERTOS - estilo Super Mario
        // Braço direito levantado e aberto
        rightArm.xRot = toRad(-160) * jumpIntensity;  // Levantado (mantido dramático)
        rightArm.yRot = toRad(-20) * jumpIntensity;   // Aberto para o lado
        rightArm.zRot = toRad(25) * jumpIntensity;    // Mais para fora
        
        // Braço esquerdo aberto para baixo (simetria)
        leftArm.xRot = toRad(40) * jumpIntensity;     // Para trás/baixo
        leftArm.yRot = toRad(20) * jumpIntensity;     // Aberto para o lado
        leftArm.zRot = toRad(-25) * jumpIntensity;    // Mais para fora
        
        // 🫁 Corpo levemente inclinado para frente (pose aerodinâmica)
        body.xRot = toRad(8) * jumpIntensity;
        body.zRot = toRad(3) * Mth.sin(state.ageInTicks * 0.5f) * jumpIntensity; // Leve torção
        
        // 😃 Cabeça olhando para frente/levemente para cima (confiante!)
        head.xRot = toRad(-15) * jumpIntensity;
    }
    
    /**
     * Applies panicking animation (original MCA animation, preserved)
     * This is called separately and will override arm rotations
     */
    public static void applyPanicAnimation(float animationProgress, ModelPart leftArm, ModelPart rightArm) {
        float toRadians = (float) Math.PI / 180;
        
        float armRaise = (((float) Math.sin(animationProgress / 5) * 30 - 180)
                          + ((float) Math.sin(animationProgress / 3) * 3))
                         * toRadians;
        float waveSideways = ((float) Math.sin(animationProgress / 2) * 12 - 17) * toRadians;
        
        leftArm.xRot = armRaise;
        leftArm.zRot = -waveSideways;
        rightArm.xRot = -armRaise;
        rightArm.zRot = waveSideways;
    }
    
    /**
     * Applies MCA-specific chore animations (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyChoreAnimation(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body, 
            ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        
        float t = state.ageInTicks;
        
        switch (state.currentChore) {
            case CHOP:
                // 🪓 Chopping: dramatic overhead swing
                float chopSwing = (float) Math.sin(t * 0.4f) * 1.8f; // ENHANCED: 0.3 -> 0.4, 1.2 -> 1.8
                rightArm.xRot = -2.8f + chopSwing; // ENHANCED: -2.2 -> -2.8
                rightArm.yRot = -0.5f;
                leftArm.xRot = -0.8f + chopSwing * 0.5f;
                leftArm.yRot = 0.5f;
                body.xRot = chopSwing * 0.3f; // ENHANCED: 0.2 -> 0.3
                body.yRot = (float) Math.sin(t * 0.4f) * 0.3f; // ENHANCED: 0.2 -> 0.3
                break;
                
            case PROSPECT:
                // ⛏️ Mining: powerful pickaxe strikes
                float mineSwing = (float) Math.sin(t * 0.35f) * 1.6f; // ENHANCED: 0.25 -> 0.35, 1.2 -> 1.6
                rightArm.xRot = -2.4f + mineSwing; // ENHANCED: -2.0 -> -2.4
                rightArm.zRot = 0.3f;
                leftArm.xRot = -1.8f; // ENHANCED: -1.5 -> -1.8
                leftArm.zRot = -0.3f;
                body.xRot = mineSwing * 0.25f; // ENHANCED: 0.15 -> 0.25
                head.xRot += 0.4f; // ENHANCED: 0.3 -> 0.4
                break;
                
            case HARVEST:
                // 🌾 Harvesting: rhythmic bending and reaching
                float harvestBend = (float) Math.sin(t * 0.25f); // ENHANCED: 0.15 -> 0.25
                body.xRot = harvestBend * 0.6f + 0.6f; // ENHANCED: 0.4 + 0.4 -> 0.6 + 0.6
                rightArm.xRot = harvestBend * 1.0f + 0.8f; // ENHANCED: 0.7 + 0.5 -> 1.0 + 0.8
                leftArm.xRot = -harvestBend * 1.0f + 0.8f; // ENHANCED
                rightArm.yRot = 0.3f;
                leftArm.yRot = -0.3f;
                head.xRot += 0.5f; // ENHANCED: 0.3 -> 0.5
                break;
                
            case HUNT:
                // ⚔️ Hunting: aggressive combat stance
                float huntStance = (float) Math.sin(t * 0.2f) * 0.3f; // ENHANCED: 0.15 -> 0.2, 0.2 -> 0.3
                rightArm.xRot = -1.8f + huntStance; // ENHANCED: -1.5 -> -1.8
                rightArm.yRot = -0.6f; // ENHANCED: -0.4 -> -0.6
                leftArm.xRot = -0.6f;
                leftArm.zRot = -0.3f;
                body.yRot = huntStance;
                rightLeg.xRot = -0.3f;
                leftLeg.xRot = 0.3f;
                break;
                
            case FISH:
                // 🎣 Fishing: patient casting motion
                float fishCast = (float) Math.sin(t * 0.1f); // ENHANCED: 0.08 -> 0.1
                rightArm.xRot = -2.4f + fishCast * 0.6f; // ENHANCED: -2.0, 0.3 -> -2.4, 0.6
                rightArm.yRot = -0.5f;
                leftArm.xRot = -1.2f + fishCast * 0.4f; // ENHANCED: -1.0, 0.2 -> -1.2, 0.4
                leftArm.yRot = 0.3f;
                body.xRot = -0.15f; // ENHANCED: -0.1 -> -0.15
                break;
                
            case NONE:
            default:
                // No chore animation
                break;
        }
    }
    
    /**
     * Trading animation (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyTradingAnimation(
            VillagerAnimationState<T> state, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
        
        if (!state.isTrading) return;
        
        // 🤝 Presenting item with enthusiasm
        float bobbing = (float) Math.sin(state.ageInTicks * 0.15f) * 0.15f; // ENHANCED: 0.1, 0.1 -> 0.15, 0.15
        
        rightArm.xRot = -1.8f + bobbing; // ENHANCED: -1.4 -> -1.8
        rightArm.yRot = -0.6f; // ENHANCED: -0.4 -> -0.6
        rightArm.zRot = 0.0f;
        
        leftArm.xRot = -1.6f + bobbing; // ENHANCED: -1.2 -> -1.6
        leftArm.yRot = 0.6f; // ENHANCED: 0.4 -> 0.6
        leftArm.zRot = 0.0f;
        
        head.xRot = -0.4f + bobbing * 0.5f; // ENHANCED: -0.2 -> -0.4
    }
    
    /**
     * Interaction animations - positive reactions (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyPositiveInteraction(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body, 
            ModelPart rightArm, ModelPart leftArm) {
        
        float t = state.ageInTicks;
        
        // 😊 Happy wave and bounce
        float wave = (float) Math.sin(t * 0.5f) * 0.8f; // ENHANCED: 0.4, 0.5 -> 0.5, 0.8
        float bounce = (float) Math.abs(Math.sin(t * 0.5f)) * 0.3f; // ENHANCED: 0.2 -> 0.3
        
        rightArm.xRot = -2.2f + wave; // ENHANCED: -1.8 -> -2.2
        rightArm.zRot = 0.6f; // ENHANCED: 0.4 -> 0.6
        
        head.yRot = (float) Math.sin(t * 0.3f) * 0.4f; // ENHANCED: 0.2, 0.2 -> 0.3, 0.4
        head.xRot = -0.3f + bounce; // ENHANCED: -0.2, bounce -> -0.3
        
        body.y -= bounce * 3.0f; // ENHANCED: 2.0 -> 3.0
    }
    
    /**
     * Interaction animations - negative reactions (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyNegativeInteraction(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body, 
            ModelPart rightArm, ModelPart leftArm) {
        
        float t = state.ageInTicks;
        
        // 😠 Frustrated gesture and head shake
        float shake = (float) Math.sin(t * 0.8f) * 0.6f; // ENHANCED: 0.6, 0.4 -> 0.8, 0.6
        float armShake = (float) Math.sin(t * 0.6f) * 0.5f; // ENHANCED: 0.4 -> 0.6, 0.3 -> 0.5
        
        head.yRot = shake;
        head.zRot = shake * 0.5f;
        
        rightArm.xRot = -0.8f + armShake; // ENHANCED: -0.5 -> -0.8
        rightArm.zRot = 0.4f + armShake * 0.5f; // ENHANCED: 0.3 -> 0.4
        
        leftArm.xRot = -0.8f - armShake; // ENHANCED: -0.5 -> -0.8
        leftArm.zRot = -0.4f - armShake * 0.5f; // ENHANCED: -0.3 -> -0.4
        
        body.yRot = shake * 0.3f; // ENHANCED: 0.2 -> 0.3
    }
    
    /**
     * Following animation (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyFollowingAnimation(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body) {
        
        if (!state.isFollowing) return;
        
        // 👀 Alert posture and eager looking
        float alertness = (float) Math.sin(state.ageInTicks * 0.2f) * 0.15f; // ENHANCED: 0.15, 0.1 -> 0.2, 0.15
        
        head.xRot = -0.4f + alertness; // ENHANCED: -0.3 -> -0.4
        body.xRot = -0.15f; // ENHANCED: -0.1 -> -0.15
        body.y -= 1.5f; // ENHANCED: 1.0 -> 1.5 (slightly crouched)
    }
    
    /**
     * Staying/waiting animation (ENHANCED for visibility)
     */
    public static <T extends LivingEntity & VillagerLike<T>> void applyStayingAnimation(
            VillagerAnimationState<T> state, ModelPart head, ModelPart body, 
            ModelPart rightArm, ModelPart leftArm) {
        
        if (state.moveState == null || state.moveState != net.conczin.mca.entity.ai.MoveState.STAY) return;
        
        // 🧍 Relaxed standing with fidgeting
        float fidget = (float) Math.sin(state.ageInTicks * 0.08f); // ENHANCED: 0.05 -> 0.08
        
        rightArm.xRot = -0.2f + fidget * 0.2f; // ENHANCED: -0.1, 0.1 -> -0.2, 0.2
        rightArm.zRot = 0.15f; // ENHANCED: 0.1 -> 0.15
        
        leftArm.xRot = -0.2f - fidget * 0.2f; // ENHANCED
        leftArm.zRot = -0.15f; // ENHANCED
        
        body.yRot = fidget * 0.15f; // ENHANCED: 0.1 -> 0.15
        head.yRot = -fidget * 0.3f; // ENHANCED: 0.2 -> 0.3
    }
}
