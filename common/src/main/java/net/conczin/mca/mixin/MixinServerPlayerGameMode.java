package net.conczin.mca.mixin;

import net.conczin.mca.server.property.PropertyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Property: denies using or breaking blocks that belong to someone else. */
@Mixin(ServerPlayerGameMode.class)
abstract class MixinServerPlayerGameMode {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void mca$useOwnedBlock(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (level instanceof ServerLevel serverLevel && PropertyRules.onUse(player, serverLevel, hit.getBlockPos(), stack, hand)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void mca$breakOwnedBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (PropertyRules.onBreak(player, level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
