package net.conczin.mca.mixin.addon.roads;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The inventory of a pack animal, to fill a caravan's chests (see Caravans). */
@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
    @Accessor("inventory")
    SimpleContainer mca$getInventory();
}
