package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Plan a blueprint with its lowest corner at {@code origin}, turned {@code rotation} quarter turns clockwise.
 */
public record PlaceBlueprintMessage(String id, BlockPos origin, int rotation) implements HandleablePayload {
    public static final CustomPacketPayload.Type<PlaceBlueprintMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("place_blueprint"));
    public static final StreamCodec<FriendlyByteBuf, PlaceBlueprintMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlaceBlueprintMessage::id,
            BlockPos.STREAM_CODEC, PlaceBlueprintMessage::origin,
            ByteBufCodecs.VAR_INT, PlaceBlueprintMessage::rotation,
            PlaceBlueprintMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return;
        }
        if (!WorkOrdersRequest.isNearPlayer(player, origin)) {
            player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
            return;
        }
        player.sendSystemMessage(WorkOrderService.placeBlueprint(player, parsed, origin, rotation).message());
        WorkOrdersRequest.sendResponse(player, origin);
    }

    @Override
    public CustomPacketPayload.Type<PlaceBlueprintMessage> type() {
        return TYPE;
    }
}
