package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.conczin.mca.ducks.IVillagerEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.BrainThrottle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
abstract class MixinVillager implements IVillagerEntity {
    @Unique
    @Nullable
    private transient EntitySpawnReason mca$reason;

    @Override
    public EntitySpawnReason mca$getSpawnReason() {
        return mca$reason == null ? EntitySpawnReason.NATURAL : mca$reason;
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mca$injectFinalizeSpawn(
            ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, SpawnGroupData spawnGroupData, CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        mca$reason = spawnType;
    }

    /** Villagers far from players think less often (see {@link BrainThrottle}). */
    @WrapWithCondition(method = "customServerAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
    private boolean mca$throttleBrain(Brain<?> brain, ServerLevel level, LivingEntity body) {
        if (!(body instanceof VillagerEntityMCA villager) || BrainThrottle.shouldTick(level, villager)) {
            return true;
        }
        // memories still run out on time
        ((BrainAccessor) brain).mca$forgetOutdatedMemories();
        return false;
    }
}
