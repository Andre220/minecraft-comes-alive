package net.conczin.mca.client.render;

import net.conczin.mca.client.resources.WorkAnimationClip;
import net.conczin.mca.client.resources.WorkAnimationLoader;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Plays the sounds and particles of work animation events, in step with the animation's loop.
 */
public final class WorkAnimationEffects {
    private static final double RANGE_SQR = 24 * 24;

    private WorkAnimationEffects() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null || client.isPaused()) return;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VillagerEntityMCA villager && villager.distanceToSqr(client.player) < RANGE_SQR) {
                tick(level, villager);
            }
        }
    }

    private static void tick(ClientLevel level, VillagerEntityMCA villager) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        // no effects while blending out
        if (brain.getWorkAnimation() == WorkAnimation.NONE) return;

        WorkAnimationClip clip = WorkAnimationLoader.get(brain.getShownWorkAnimation(), villager.getProfessionId());
        float ticks = brain.getWorkAnimationTicks(0.0F);
        if (clip == null || ticks < 1) return;

        for (String event : clip.eventsBetween(clip.phase(ticks - 1), clip.phase(ticks))) {
            if (event.equals("impact")) {
                impact(level, villager, clip.impact());
            }
        }
    }

    private static void impact(ClientLevel level, VillagerEntityMCA villager, WorkAnimationClip.Impact impact) {
        RandomSource random = villager.getRandom();
        BlockPos worked = workedBlock(level, villager);
        BlockState state = worked == null ? null : level.getBlockState(worked);

        // where the hand meets the work: in front of the villager, about waist high
        Vec3 forward = Vec3.directionFromRotation(0, villager.yBodyRot);
        Vec3 hit = worked != null
                ? Vec3.atCenterOf(worked).add(0, 0.4, 0)
                : villager.position().add(forward.scale(0.7)).add(0, 0.8, 0);

        SoundEvent sound = switch (impact.sound()) {
            case "none" -> null;
            case "block" -> state == null ? null : state.getSoundType().getHitSound();
            default -> BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.tryParse(impact.sound())).orElse(null);
        };
        if (sound != null) {
            float pitch = impact.pitch() * (0.9F + random.nextFloat() * 0.2F);
            level.playLocalSound(hit.x, hit.y, hit.z, sound, SoundSource.NEUTRAL, impact.volume(), pitch, false);
        }

        ParticleOptions particle = switch (impact.particle()) {
            case "none" -> null;
            case "block" -> state == null ? null : new BlockParticleOption(ParticleTypes.BLOCK, state);
            default -> BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.tryParse(impact.particle()))
                    .filter(type -> type instanceof SimpleParticleType)
                    .map(type -> (ParticleOptions) type)
                    .orElse(null);
        };
        if (particle != null) {
            for (int i = 0; i < impact.particleCount(); i++) {
                level.addParticle(particle,
                        hit.x + (random.nextDouble() - 0.5) * 0.4,
                        hit.y + random.nextDouble() * 0.2,
                        hit.z + (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 0.15,
                        random.nextDouble() * 0.15,
                        (random.nextDouble() - 0.5) * 0.15);
            }
        }
    }

    /**
     * The block in front of the villager it works on: the station at its feet, or the crop/ground for crouching work.
     */
    private static @Nullable BlockPos workedBlock(ClientLevel level, VillagerEntityMCA villager) {
        Vec3 forward = Vec3.directionFromRotation(0, villager.yBodyRot);
        BlockPos ahead = BlockPos.containing(villager.position().add(forward.scale(0.8)).add(0, 0.2, 0));
        for (BlockPos pos : new BlockPos[]{ahead, ahead.below(), villager.blockPosition(), villager.blockPosition().below()}) {
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }
}
