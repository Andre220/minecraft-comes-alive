package net.conczin.mca.mixin.addon.roads;

import net.conczin.mca.addon.roads.caravan.Caravans;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With caravans on, the wandering trader still comes, only rarer (expansion: caravans). */
@Mixin(WanderingTraderSpawner.class)
abstract class MixinWanderingTraderSpawner {
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void mca$rarerWithCaravans(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        if (Caravans.skipWanderingTrader(level.getRandom())) {
            cir.setReturnValue(false);
        }
    }
}
