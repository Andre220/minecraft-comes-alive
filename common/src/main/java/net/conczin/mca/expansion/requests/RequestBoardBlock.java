package net.conczin.mca.expansion.requests;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The village's request board: using it gets a request from someone in the village who needs
 * help; sneaking lists the requests you have taken there.
 */
public class RequestBoardBlock extends Block {
    public RequestBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!Requests.enabled()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            if (player.isSecondaryUseActive()) {
                Requests.listOnBoard(serverLevel, pos, player);
            } else {
                Requests.takeFromBoard(serverLevel, pos, player);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
