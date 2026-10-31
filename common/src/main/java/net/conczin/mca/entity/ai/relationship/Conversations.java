package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.entity.ai.traits.TraitCompatibility;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

import java.util.Optional;
import java.util.UUID;

/**
 * Two villagers talking. Whether it goes well depends on how their traits get along and on what
 * they already think of each other: friends chat, villagers who clash end up arguing. The outcome
 * changes both opinions, the mood, and may make a jealous partner watching from nearby unhappy.
 */
public final class Conversations {
    /** Chance to argue between two neutral villagers with nothing in common. */
    private static final float BASE_ARGUMENT = 0.12F;
    private static final int CHAT_OPINION = 3;
    private static final int ARGUMENT_OPINION = -7;
    private static final int ARGUMENT_MOOD = -2;
    private static final int JEALOUS_MOOD = -3;
    private static final int JEALOUS_OPINION = -6;
    private static final double JEALOUSY_RANGE = 12.0;

    private Conversations() {
    }

    /** The chance that a talk between the two ends in an argument. */
    public static float argumentChance(VillagerEntityMCA a, VillagerEntityMCA b) {
        float compatibility = TraitCompatibility.between(a.getTraits(), b.getTraits());
        float opinion = (a.getOpinions().get(b.getUUID()) + b.getOpinions().get(a.getUUID())) / (2.0F * Opinions.MAX);
        float chance = BASE_ARGUMENT - compatibility * 0.35F - opinion * 0.2F;
        if (a.getTraits().hasTrait(PersonalityTraits.PEACEFUL) || b.getTraits().hasTrait(PersonalityTraits.PEACEFUL)) {
            chance *= 0.5F;
        }
        return Mth.clamp(chance, 0.02F, 0.8F);
    }

    /** A finished talk: an argument or a good chat, with what follows from it. Returns whether it went well. */
    public static boolean talk(ServerLevel level, VillagerEntityMCA a, VillagerEntityMCA b) {
        boolean argument = level.getRandom().nextFloat() < argumentChance(a, b);
        if (argument) {
            // each takes it as hard as its traits make it, a sensitive villager twice as hard
            a.getOpinions().change(b, a.getTraits().scaleHearts(ARGUMENT_OPINION));
            b.getOpinions().change(a, b.getTraits().scaleHearts(ARGUMENT_OPINION));
            a.getVillagerBrain().modifyMoodValue(ARGUMENT_MOOD);
            b.getVillagerBrain().modifyMoodValue(ARGUMENT_MOOD);
            particles(level, a, ParticleTypes.ANGRY_VILLAGER);
            particles(level, b, ParticleTypes.ANGRY_VILLAGER);
        } else {
            int warmth = CHAT_OPINION + Math.round(TraitCompatibility.between(a.getTraits(), b.getTraits()) * 4.0F);
            a.getOpinions().change(b, a.getTraits().scaleHearts(Math.max(1, warmth)));
            b.getOpinions().change(a, b.getTraits().scaleHearts(Math.max(1, warmth)));
            particles(level, a, ParticleTypes.HAPPY_VILLAGER);
            particles(level, b, ParticleTypes.HAPPY_VILLAGER);
            jealousy(level, a, b);
            jealousy(level, b, a);
        }
        return !argument;
    }

    /** A jealous partner seeing the talker chat with someone else is not pleased with that someone. */
    private static void jealousy(ServerLevel level, VillagerEntityMCA talker, VillagerEntityMCA other) {
        Optional<UUID> partnerId = talker.getRelationships().getPartnerUUID();
        if (partnerId.isEmpty() || partnerId.get().equals(other.getUUID())) {
            return;
        }
        level.getEntitiesOfClass(VillagerEntityMCA.class, talker.getBoundingBox().inflate(JEALOUSY_RANGE),
                        v -> v.getUUID().equals(partnerId.get()) && v.getTraits().hasTrait(PersonalityTraits.JEALOUS))
                .forEach(partner -> {
                    partner.getVillagerBrain().modifyMoodValue(JEALOUS_MOOD);
                    partner.getOpinions().change(other, JEALOUS_OPINION);
                    particles(level, partner, ParticleTypes.ANGRY_VILLAGER);
                });
    }

    private static void particles(ServerLevel level, VillagerEntityMCA villager, ParticleOptions particle) {
        level.sendParticles(particle, villager.getX(), villager.getEyeY() + 0.4, villager.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
    }
}
