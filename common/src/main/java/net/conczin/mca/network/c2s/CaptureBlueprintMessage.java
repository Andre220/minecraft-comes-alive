package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Save the blocks inside a box, built by hand, as a blueprint called {@code name}.
 */
public record CaptureBlueprintMessage(BlockPos from, BlockPos to, String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CaptureBlueprintMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("capture_blueprint"));
    public static final StreamCodec<FriendlyByteBuf, CaptureBlueprintMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CaptureBlueprintMessage::from,
            BlockPos.STREAM_CODEC, CaptureBlueprintMessage::to,
            ByteBufCodecs.stringUtf8(64), CaptureBlueprintMessage::name,
            CaptureBlueprintMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        WorkArea area = WorkArea.of(from, to);
        if (!WorkOrdersRequest.isNearPlayer(player, area.center())) {
            player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
            return;
        }
        player.sendSystemMessage(WorkOrderService.capture(player, area, name).message());
        WorkOrdersRequest.sendResponse(player, area.center());
    }

    @Override
    public CustomPacketPayload.Type<CaptureBlueprintMessage> type() {
        return TYPE;
    }
}
