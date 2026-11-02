package net.conczin.mca.mixin.client;

import net.conczin.mca.client.gui.MayorClockHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the mayor's clock dial on top of the rest of the HUD.
 */
@Mixin(Hud.class)
public abstract class MixinHud {
    @Shadow
    public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mca$extractMayorClock(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!isHidden()) {
            MayorClockHud.extract(graphics);
        }
    }
}
