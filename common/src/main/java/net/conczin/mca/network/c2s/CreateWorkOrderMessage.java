package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The planning screen confirmed a selection. {@code blockState} is a block state id, or -1 for digging.
 */
public record CreateWorkOrderMessage(int orderType, BlockPos from, BlockPos to, int blockState) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CreateWorkOrderMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("create_work_order"));
    public static final StreamCodec<FriendlyByteBuf, CreateWorkOrderMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreateWorkOrderMessage::orderType,
            BlockPos.STREAM_CODEC, CreateWorkOrderMessage::from,
            BlockPos.STREAM_CODEC, CreateWorkOrderMessage::to,
            ByteBufCodecs.VAR_INT, CreateWorkOrderMessage::blockState,
            CreateWorkOrderMessage::new
    );

    public static CreateWorkOrderMessage of(WorkOrderType type, BlockPos from, BlockPos to, @Nullable BlockState block) {
        return new CreateWorkOrderMessage(type.ordinal(), from, to, block == null ? -1 : Block.getId(block));
    }

    @Override
    public void handleServer(ServerPlayer player) {
        WorkOrderType[] types = WorkOrderType.values();
        if (orderType < 0 || orderType >= types.length) {
            return;
        }
        WorkArea area = WorkArea.of(from, to);
        if (!WorkOrdersRequest.isNearPlayer(player, area.center())) {
            player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
            return;
        }
        WorkOrderType type = types[orderType];
        // only plain blocks are planned, the client never picks rotations or other block state details
        BlockState block = type == WorkOrderType.BUILD && blockState >= 0
                ? Block.stateById(blockState).getBlock().defaultBlockState()
                : null;
        WorkOrderService.Result result = WorkOrderService.create(player, type, area, block);
        player.sendSystemMessage(result.message());
        WorkOrdersRequest.sendResponse(player, area.center());
    }

    @Override
    public CustomPacketPayload.Type<CreateWorkOrderMessage> type() {
        return TYPE;
    }
}
