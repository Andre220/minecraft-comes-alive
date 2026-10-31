package net.conczin.mca.mixin.addon.roads;

import net.conczin.mca.addon.roads.caravan.Caravans;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A caravan's pack animal left over from an older trip goes away when its chunk loads (expansion: caravans). */
@Mixin(AbstractHorse.class)
abstract class MixinAbstractHorse {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mca$caravanAnimal(CallbackInfo ci) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (self.tickCount % 100 == 0 && !self.level().isClientSide()) {
            Caravans.tickAnimal(self);
        }
    }
}
