package net.conczin.mca.expansion.townhall;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The City Records: a wooden stand, 12 px high like the enchanting table, with its book floating above. */
public class CityRecordsBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            Block.box(4, 2, 4, 12, 10, 12),
            Block.box(2, 10, 2, 14, 12, 14));

    public CityRecordsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CityRecordsBlockEntity(pos, state);
    }

    /** Only the client animates the book. */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != TownHall.RECORDS) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<CityRecordsBlockEntity>) (l, pos, s, records) -> records.animate(l, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.TOWN_HALL), serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
