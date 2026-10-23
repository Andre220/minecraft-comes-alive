package net.conczin.mca.mixin.client;

import net.conczin.mca.mixin.ItemDisplayAccessor;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Property: a padlocked door, trapdoor or container (it shows a padlock display) is not opened
 * ahead of the server's answer. The use is still sent; the server opens it for its owner, or
 * denies it without the door swinging (and sounding) first.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MixinMultiPlayerGameMode {
    @Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
    private void mca$waitForPadlockedBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.isCreative()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        AABB around = new AABB(pos).inflate(0.6, 1.1, 0.6);
        boolean padlocked = !player.level().getEntitiesOfClass(Display.ItemDisplay.class, around,
                display -> ((ItemDisplayAccessor) display).mca$getItemStack().is(ItemsMCA.PADLOCK)).isEmpty();
        if (padlocked) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
