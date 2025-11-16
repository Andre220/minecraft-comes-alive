package net.conczin.mca.client.model.animation;

import net.minecraft.util.Mth;

/**
 * Mathematical utility functions for Fresh Animations-style villager animations.
 * Based on OptiFine's CEM (Custom Entity Models) math functions.
 */
public class AnimationMath {
    
    /**
     * Converts degrees to radians
     */
    public static float toRad(float degrees) {
        return degrees * ((float) Math.PI / 180f);
    }
    
    /**
     * Converts radians to degrees
     */
    public static float toDeg(float radians) {
        return radians * (180f / (float) Math.PI);
    }
    
    /**
     * Clamps a value between min and max
     */
    public static float clamp(float value, float min, float max) {
        return Mth.clamp(value, min, max);
    }
    
    /**
     * Checks if value is between min and max (inclusive)
     */
    public static boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }
    
    /**
     * Smooth interpolation function (ease-in-out cosine)
     * Returns a value that smoothly transitions from 0 to 1 as t goes from 0 to 1
     */
    public static float smoothstep(float t) {
        t = clamp(t, 0, 1);
        return (0.5f - 0.5f * Mth.cos(t * (float) Math.PI));
    }
    
    /**
     * Creates a periodic pulse (0->1->0) based on a normalized value
     * Used for animations like nodding, blinking, etc.
     */
    public static float pulse(float value, float min, float max) {
        float normalized = clamp((value - min) / (max - min), 0, 1);
        return smoothstep(normalized);
    }
    
    /**
     * Applies a sine wave to create oscillating motion
     */
    public static float wave(float time, float frequency, float amplitude) {
        return Mth.sin(time * frequency) * amplitude;
    }
    
    /**
     * Applies a cosine wave to create oscillating motion
     */
    public static float cosWave(float time, float frequency, float amplitude) {
        return Mth.cos(time * frequency) * amplitude;
    }
    
    /**
     * Linear interpolation between two values
     */
    public static float lerp(float start, float end, float t) {
        return Mth.lerp(clamp(t, 0, 1), start, end);
    }
    
    /**
     * Calculates a smooth "breathing" animation using sine
     */
    public static float breathe(float time, float speed) {
        return (1.0f + Mth.sin(time * speed)) * 0.5f;
    }
    
    /**
     * Creates a randomized offset based on entity ID
     * This ensures each villager has unique animation timing
     */
    public static float randomOffset(int entityId, float multiplier) {
        // Use entity ID as seed for consistent randomization
        return ((entityId * 2654435761L) % 10000) / 10000f * multiplier;
    }
    
    /**
     * Normalized limb swing value (divides by appropriate scale)
     */
    public static float normalizedLimbSwing(float limbSwing, boolean isChild, float limbSpeed) {
        float divisor = isChild ? 2.0f : 1.0f;
        
        // Adjust divisor based on speed (running vs walking)
        if (limbSpeed > 0.75f) {
            divisor *= (1.4f + 0.2f * 0.5f); // Approximate random factor
        } else {
            divisor *= (1.1f + 0.2f * 0.5f);
        }
        
        return limbSwing / divisor;
    }
    
    /**
     * Calculates walk intensity (0-1) based on limb speed
     * Smoothly transitions from idle to walking
     */
    public static float walkIntensity(float limbSpeed, boolean isChild) {
        float base = clamp(limbSpeed * 6.0f, 0, 1);
        
        if (isChild) {
            return base * clamp(2.5f - limbSpeed * 4.0f, 0, 1);
        } else {
            return base * clamp(9.0f - limbSpeed * 10.0f, 0, 1);
        }
    }
    
    /**
     * Calculates run intensity (0-1) based on limb speed
     * Activates when moving faster than walking
     */
    public static float runIntensity(float limbSpeed, boolean isChild) {
        if (isChild) {
            return clamp(-1.5f + limbSpeed * 4.0f, 0, 1);
        } else {
            return clamp(-4.0f + limbSpeed * 5.0f, 0, 1);
        }
    }
    
    /**
     * Checks if entity is swimming (not riding, not on ground, in water)
     */
    public static boolean isSwimming(boolean isRiding, boolean isOnGround, boolean isInWater) {
        return !isRiding && !isOnGround && isInWater;
    }
    
    /**
     * Applies arc sine with limb swing for smooth leg movement
     */
    public static float legSwing(float limbSwing, boolean isRightLeg, float walkIntensity, float runIntensity) {
        float sinValue = Mth.sin(limbSwing);
        if (!isRightLeg) {
            sinValue = -sinValue;
        }
        
        // Walking component
        float walkComponent = (float) Math.asin(Mth.clamp(sinValue, -1, 1)) / 2.8f * (0.2f + 0.8f * walkIntensity);
        
        // Running component
        float runComponent = (sinValue / (1.2f - 0.15f * walkIntensity) / 1.8f) * runIntensity;
        
        return walkComponent + runComponent;
    }
    
    /**
     * Applies arc sine with limb swing for smooth arm movement
     */
    public static float armSwing(float limbSwing, boolean isRightArm, float walkIntensity, float runIntensity) {
        // Arms swing opposite to legs
        return legSwing(limbSwing, !isRightArm, walkIntensity, runIntensity);
    }
}
