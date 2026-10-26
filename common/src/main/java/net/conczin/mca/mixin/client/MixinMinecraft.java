package net.conczin.mca.mixin.client;

import net.conczin.mca.expansion.wardrobe.ClosetPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Looks: open wardrobe doors and dresser drawers can be aimed at outside their own block (see ClosetPicker). */
@Mixin(Minecraft.class)
abstract class MixinMinecraft {
    @Shadow
    public @Nullable HitResult hitResult;

    @Shadow
    public @Nullable Entity crosshairPickEntity;

    @Inject(method = "pick", at = @At("TAIL"))
    private void mca$pickOpenFurniture(float partialTicks, CallbackInfo ci) {
        HitResult nearer = ClosetPicker.nearer((Minecraft) (Object) this, partialTicks); // expansion: looks
        if (nearer != null) {
            hitResult = nearer;
            crosshairPickEntity = null;
        }
    }
}
