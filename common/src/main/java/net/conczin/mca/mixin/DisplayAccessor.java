package net.conczin.mca.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets up item displays used as icons (see NeedIndicators). */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setTransformation")
    void mca$setTransformation(Transformation transformation);

    @Invoker("setBillboardConstraints")
    void mca$setBillboardConstraints(Display.BillboardConstraints constraints);
}
