package net.conczin.mca.mixin.addon.roads;

import net.conczin.mca.addon.roads.caravan.Caravans;
import net.conczin.mca.addon.roads.faction.Factions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A faction leaves alone the players it respects, and remembers who killed what (expansion: factions). */
@Mixin(LivingEntity.class)
abstract class MixinLivingEntityFactions {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$factionSpares(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (Factions.spares((LivingEntity) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void mca$factionDeath(DamageSource source, CallbackInfo ci) {
        Factions.onDeath((LivingEntity) (Object) this, source);
        Caravans.onDeath((LivingEntity) (Object) this, source); // expansion: caravans
    }
}
