package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The planning screen asks for the work orders of the village around {@code pos}, usually
 * where its camera looks at.
 */
public record WorkOrdersRequest(BlockPos pos) implements HandleablePayload {
    public static final CustomPacketPayload.Type<WorkOrdersRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("work_orders_request"));
    public static final StreamCodec<FriendlyByteBuf, WorkOrdersRequest> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WorkOrdersRequest::pos,
            WorkOrdersRequest::new
    );
    /** How far from the player the planned spot may be, matching the planning camera's range. */
    static final double MAX_DISTANCE = 160.0;

    @Override
    public void handleServer(ServerPlayer player) {
        sendResponse(player, pos);
    }

    static void sendResponse(ServerPlayer player, BlockPos pos) {
        BlockPos near = isNearPlayer(player, pos) ? pos : player.blockPosition();
        Optional<Village> village = VillageManager.get(player.level()).findNearestVillage(near, Village.BORDER_MARGIN);
        Network.sendToPlayer(village.map(v -> WorkOrdersResponse.of(player, v)).orElseGet(WorkOrdersResponse::none), player);
    }

    static boolean isNearPlayer(ServerPlayer player, BlockPos pos) {
        return player.blockPosition().distSqr(pos) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    @Override
    public CustomPacketPayload.Type<WorkOrdersRequest> type() {
        return TYPE;
    }
}
