package net.conczin.mca.client.model.animation;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.animation.AnimationMath.*;

/**
 * Stores and calculates animation state variables for Fresh Animations-style villager animations.
 * This class mirrors the variables defined in Fresh Animations' .jpm files.
 */
public class VillagerAnimationState<T extends LivingEntity & VillagerLike<T>> {
    
    // Entity state
    private final T villager;
    private final int entityId;
    public final boolean isChild;
    public final boolean isRiding;
    public final boolean isOnGround;
    public final boolean isInWater;
    public final float velocityY;
    private final boolean isHurt;
    private final float hurtTime;
    
    // Animation parameters
    private final float limbSwing;
    private final float limbSpeed;
    public final float ageInTicks;
    private final float headYaw;
    private final float headPitch;
    
    // MCA-specific states
    public final Chore currentChore;
    public final MoveState moveState;
    public final boolean isTrading;
    public final boolean isFollowing;
    
    // Calculated variables (from Fresh Animations)
    public final float r;           // Random offset based on entity ID
    public final float b;           // Age-based breathing animation
    public final float ls;          // Normalized limb swing
    public final float swim;        // Swimming animation phase
    public final float walk;        // Walk intensity (0-1)
    public final float run;         // Run intensity (0-1)
    public final float scared;      // Scared state (when running fast)
    public final boolean isSwimming;
    
    // Additional calculated values
    public final float swimPhase;
    public final float randomFactor;
    
    public VillagerAnimationState(T villager, float limbSwing, float limbSpeed, 
                                   float ageInTicks, float headYaw, float headPitch) {
        // Store entity state
        this.villager = villager;
        this.entityId = villager.getId();
        this.isChild = villager.isBaby();
        this.isRiding = villager.isPassenger();
        this.isOnGround = villager.onGround();
        this.isInWater = villager.isInWater();
        this.velocityY = (float) villager.getDeltaMovement().y;
        this.isHurt = villager.hurtTime > 0;
        this.hurtTime = villager.hurtTime;
        
        // Store animation parameters
        this.limbSwing = limbSwing;
        this.limbSpeed = limbSpeed;
        this.ageInTicks = ageInTicks;
        this.headYaw = clamp(headYaw, -90, 90);
        this.headPitch = headPitch;
        
        // Calculate base variables (from Fresh Animations var.* definitions)
        this.randomFactor = ((entityId * 2654435761L) % 10000) / 10000f;
        this.r = (float) Math.PI * randomFactor * 4.0f;
        this.b = ageInTicks / (11.0f - randomFactor * 2.0f) * (isChild ? 1.5f : 1.0f);
        
        // Normalized limb swing
        this.ls = normalizedLimbSwing(limbSwing, isChild, limbSpeed);
        
        // Swimming animation
        this.swim = limbSwing / (isChild ? 2.0f : 1.0f) / 2.0f + ageInTicks / (isChild ? 2.0f : 3.0f);
        this.swimPhase = swim;
        
        // Movement intensities
        this.walk = walkIntensity(limbSpeed, isChild);
        this.run = runIntensity(limbSpeed, isChild);
        
        // Scared state (activated when moving very fast)
        this.scared = limbSpeed > 0.9f ? 1.0f : 0.0f;
        
        // Swimming check
        this.isSwimming = AnimationMath.isSwimming(isRiding, isOnGround, isInWater);
        
        // MCA-specific states - initialize before conditional
        Chore chore = Chore.NONE;
        MoveState state = MoveState.MOVE;
        boolean trading = false;
        boolean following = false;
        
        try {
            if (villager.getClass().getName().contains("VillagerEntityMCA")) {
                // Use reflection to avoid compile-time dependency
                var brain = villager.getClass().getMethod("getVillagerBrain").invoke(villager);
                chore = (Chore) brain.getClass().getMethod("getCurrentJob").invoke(brain);
                state = (MoveState) brain.getClass().getMethod("getMoveState").invoke(brain);
                
                var tradingPlayer = villager.getClass().getMethod("getTradingPlayer").invoke(villager);
                trading = tradingPlayer != null;
                
                var assigner = brain.getClass().getMethod("getJobAssigner").invoke(brain);
                var optional = (java.util.Optional<?>) assigner;
                following = optional.isPresent();
            }
        } catch (Exception ignored) {
            // Fall back to defaults if reflection fails
        }
        
        this.currentChore = chore;
        this.moveState = state;
        this.isTrading = trading;
        this.isFollowing = following;
    }
    
    /**
     * Checks if villager is panicking (scared state from MCA brain)
     */
    public boolean isPanicking() {
        return villager.getVillagerBrain().isPanicking();
    }
    
    /**
     * Gets hurt animation multiplier based on hurt time
     */
    public float getHurtMultiplier() {
        if (!isHurt) return 0;
        return -Mth.sin(hurtTime * (float) Math.PI / 5.0f) / 6.0f * hurtTime;
    }
    
    /**
     * Calculates idle head looking animation
     * Creates periodic head movements when villager is idle
     */
    public float getIdleLookIntensity() {
        return clamp(Mth.sin(r + ageInTicks / 60.0f) * 6.0f, 0, 1) * 1.2f;
    }
    
    /**
     * Gets a random behavior factor (0-1) for this villager
     * Used to enable/disable certain animations randomly per villager
     */
    public boolean hasRandomBehavior(float min, float max) {
        return between(randomFactor, min, max);
    }
    
    /**
     * Calculates breathing animation offset
     */
    public float getBreathingOffset() {
        return Mth.sin(r + b) / 60.0f;
    }
    
    /**
     * Gets the entity instance
     */
    public T getVillager() {
        return villager;
    }
    
    /**
     * Returns whether the entity is a child
     */
    public boolean isChild() {
        return isChild;
    }
    
    /**
     * Returns whether the entity is riding
     */
    public boolean isRiding() {
        return isRiding;
    }
    
    /**
     * Returns whether the entity is on the ground
     */
    public boolean isOnGround() {
        return isOnGround;
    }
    
    /**
     * Returns whether the entity is in water
     */
    public boolean isInWater() {
        return isInWater;
    }
    
    /**
     * Returns the entity's vertical velocity (Y motion)
     */
    public float getVelocityY() {
        return velocityY;
    }
    
    /**
     * Returns whether the entity is jumping
     * Detected by: not on ground AND moving upward
     */
    public boolean isJumping() {
        return !isOnGround && velocityY > 0.1f;
    }
    
    /**
     * Returns limb swing value
     */
    public float getLimbSwing() {
        return limbSwing;
    }
    
    /**
     * Returns limb speed value
     */
    public float getLimbSpeed() {
        return limbSpeed;
    }
    
    /**
     * Returns age in ticks
     */
    public float getAgeInTicks() {
        return ageInTicks;
    }
    
    /**
     * Returns head yaw
     */
    public float getHeadYaw() {
        return headYaw;
    }
    
    /**
     * Returns head pitch
     */
    public float getHeadPitch() {
        return headPitch;
    }
}
