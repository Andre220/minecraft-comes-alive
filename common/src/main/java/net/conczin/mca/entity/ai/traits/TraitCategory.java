package net.conczin.mca.entity.ai.traits;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Kind of trait. Genetic traits are inborn conditions and looks; the others are personality
 * traits in the style of The Sims, which take one of the slots a villager has for its age.
 */
public enum TraitCategory {
    GENETIC(0xFF9E9E9E),
    EMOTIONAL(0xFFE57373),
    HOBBY(0xFF64B5F6),
    LIFESTYLE(0xFF81C784),
    SOCIAL(0xFFFFB74D);

    private final int color;

    TraitCategory(int color) {
        this.color = color;
    }

    /** Whether traits of this category count against the slots of an age. */
    public boolean usesSlot() {
        return this != GENETIC;
    }

    /** ARGB color of the category, used for icon borders. */
    public int color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component getName() {
        return Component.translatable("trait.category." + id());
    }
}
