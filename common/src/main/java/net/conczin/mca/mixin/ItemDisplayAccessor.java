package net.conczin.mca.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets up the item displays that show padlocks (see PadlockDisplays). */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
    @Invoker("setItemStack")
    void mca$setItemStack(ItemStack stack);

    @Invoker("getItemStack")
    ItemStack mca$getItemStack();

    @Invoker("setItemTransform")
    void mca$setItemTransform(ItemDisplayContext context);
}
