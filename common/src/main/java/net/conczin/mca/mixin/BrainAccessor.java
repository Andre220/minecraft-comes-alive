package net.conczin.mca.mixin;

import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets memories age on the ticks a villager's brain is skipped (see BrainThrottle). */
@Mixin(Brain.class)
public interface BrainAccessor {
    @Invoker("forgetOutdatedMemories")
    void mca$forgetOutdatedMemories();
}
