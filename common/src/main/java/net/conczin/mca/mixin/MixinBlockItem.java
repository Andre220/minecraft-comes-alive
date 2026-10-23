package net.conczin.mca.mixin;

import net.conczin.mca.server.property.PropertyRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Property: what a player places belongs to them. */
@Mixin(BlockItem.class)
abstract class MixinBlockItem {
    @Inject(method = "place", at = @At("RETURN"))
    private void mca$ownPlacedBlock(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer player
            && context.getLevel() instanceof ServerLevel level) {
            PropertyRules.onPlaced(player, level, context.getClickedPos());
        }
    }
}
