package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA farmers crouch over a crop for one crouch cycle before harvesting or planting it, instead of doing it on arrival.
 */
@Mixin(HarvestFarmland.class)
abstract class MixinHarvestFarmland {
    /** Length of the crouch clip, see assets/mca/animations/work/crouch.json. */
    @Unique
    private static final int MCA_CROUCH_TICKS = 40;

    @Shadow
    private @Nullable BlockPos aboveFarmlandPos;
    @Shadow
    private long nextOkStartTime;
    @Shadow
    private int timeWorkedSoFar;

    @Unique
    private @Nullable BlockPos mca$crouchPos;
    @Unique
    private int mca$crouchTicks;

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V", at = @At("HEAD"), cancellable = true)
    private void mca$crouchBeforeWork(ServerLevel level, Villager body, long timestamp, CallbackInfo ci) {
        if (!(body instanceof VillagerEntityMCA villager) || aboveFarmlandPos == null
            || timestamp <= nextOkStartTime || !aboveFarmlandPos.closerToCenterThan(body.position(), 1.0)) {
            return;
        }

        BlockState state = level.getBlockState(aboveFarmlandPos);
        boolean hasWork = state.getBlock() instanceof CropBlock crop ? crop.isMaxAge(state) : state.isAir() && body.hasFarmSeeds();
        if (!hasWork) return;

        if (!aboveFarmlandPos.equals(mca$crouchPos)) {
            mca$crouchPos = aboveFarmlandPos;
            mca$crouchTicks = 0;
            villager.getVillagerBrain().restartWorkAnimation();
        }
        villager.getVillagerBrain().setWorkAnimation(WorkAnimation.CROUCH, 5);

        if (mca$crouchTicks++ < MCA_CROUCH_TICKS) {
            timeWorkedSoFar++;
            ci.cancel();
        }
    }

    @Inject(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V", at = @At("HEAD"))
    private void mca$stopCrouching(ServerLevel level, Villager body, long timestamp, CallbackInfo ci) {
        mca$crouchPos = null;
        if (body instanceof VillagerEntityMCA villager) {
            villager.getVillagerBrain().stopWorkAnimation();
        }
    }
}
