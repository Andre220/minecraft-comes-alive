package net.conczin.mca.entity.ai.traits;

import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.Traits.Trait;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.entity.ai.traits.PersonalityTraits.*;

/**
 * How well two villagers get along by their traits, from -1 to 1. Some traits are pleasant (or
 * unpleasant) with everyone, some pairs clash, and villagers with the same personality traits
 * tend to like each other, except for the ones that clash with themselves, like two hot heads.
 */
public final class TraitCompatibility {
    /** How pleasant a trait is with anyone. */
    private static final Map<Trait, Float> AFFINITY = new HashMap<>();
    /** Pairs that get along especially well or badly, in both directions. */
    private static final Map<Trait, Map<Trait, Float>> PAIRS = new HashMap<>();
    /** Sharing a personality trait. */
    private static final float SHARED = 0.08F;

    static {
        affinity(FRIENDLY, 0.12F);
        affinity(GOOD, 0.08F);
        affinity(PEACEFUL, 0.06F);
        affinity(CHEERFUL, 0.05F);
        affinity(GENEROUS, 0.05F);
        affinity(MEAN, -0.15F);
        affinity(HOT_HEADED, -0.1F);
        affinity(SOCIALLY_AWKWARD, -0.05F);

        pair(GOOD, MEAN, -0.4F);
        pair(FRIENDLY, MEAN, -0.2F);
        pair(CHEERFUL, GLOOMY, -0.2F);
        pair(OUTGOING, LONER, -0.15F);
        pair(PARTY_ANIMAL, LONER, -0.15F);
        pair(NOSY, LONER, -0.15F);
        pair(NEAT, SLOB, -0.2F);
        pair(ACTIVE, LAZY, -0.15F);
        pair(FAMILY_ORIENTED, HATES_CHILDREN, -0.2F);
        pair(GENEROUS, MATERIALISTIC, -0.2F);
        pair(ANXIOUS, HOT_HEADED, -0.2F);
        pair(HOT_HEADED, HOT_HEADED, -0.3F);
        pair(MEAN, MEAN, -0.2F);
        pair(BOOKWORM, GENIUS, 0.12F);
        pair(CREATIVE, ART_LOVER, 0.12F);
        pair(GOOFBALL, CHEERFUL, 0.1F);
        pair(PARTY_ANIMAL, OUTGOING, 0.1F);
        pair(STORYTELLER, FAMILY_ORIENTED, 0.1F);
        pair(WISE, ANXIOUS, 0.1F);
    }

    private TraitCompatibility() {
    }

    private static void affinity(Trait trait, float value) {
        AFFINITY.put(trait, value);
    }

    private static void pair(Trait a, Trait b, float value) {
        PAIRS.computeIfAbsent(a, t -> new HashMap<>()).put(b, value);
        PAIRS.computeIfAbsent(b, t -> new HashMap<>()).put(a, value);
    }

    /** How well the two get along, from -1 (they clash) to 1 (made for each other). */
    public static float between(Traits a, Traits b) {
        return between(a.getPersonalityTraits(), b.getPersonalityTraits());
    }

    public static float between(List<Trait> a, List<Trait> b) {
        float score = 0.0F;
        for (Trait trait : a) {
            score += AFFINITY.getOrDefault(trait, 0.0F) / 2.0F;
        }
        for (Trait trait : b) {
            score += AFFINITY.getOrDefault(trait, 0.0F) / 2.0F;
        }
        // how much each likes the other at all: beauty, an annoying voice (traits from RimWorld)
        for (Trait trait : a) {
            score += charm(trait);
        }
        for (Trait trait : b) {
            score += charm(trait);
        }
        Set<Trait> others = Set.copyOf(b);
        for (Trait trait : a) {
            Map<Trait, Float> pairs = PAIRS.getOrDefault(trait, Map.of());
            for (Trait other : b) {
                score += pairs.getOrDefault(other, 0.0F);
            }
            if (others.contains(trait) && !pairs.containsKey(trait)) {
                score += SHARED;
            }
        }
        return Mth.clamp(score, -1.0F, 1.0F);
    }

    private static float charm(Trait trait) {
        float charm = 0.0F;
        for (TraitEffect effect : trait.getEffects()) {
            if (effect instanceof TraitEffect.Charm c) {
                charm += c.amount();
            }
        }
        return charm;
    }
}
