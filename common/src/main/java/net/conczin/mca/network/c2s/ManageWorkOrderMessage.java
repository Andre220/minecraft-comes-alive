package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.VillageWorkOrders;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Cancel, pause or resume a work order from the planning screen.
 */
public record ManageWorkOrderMessage(int villageId, int orderId, int action) implements HandleablePayload {
    public static final int CANCEL = 0;
    public static final int PAUSE = 1;
    public static final int RESUME = 2;

    public static final CustomPacketPayload.Type<ManageWorkOrderMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("manage_work_order"));
    public static final StreamCodec<FriendlyByteBuf, ManageWorkOrderMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ManageWorkOrderMessage::villageId,
            ByteBufCodecs.VAR_INT, ManageWorkOrderMessage::orderId,
            ByteBufCodecs.VAR_INT, ManageWorkOrderMessage::action,
            ManageWorkOrderMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Optional<Village> found = VillageManager.get(player.level()).getOrEmpty(villageId);
        if (found.isEmpty()) {
            return;
        }
        Village village = found.get();
        if (!WorkOrderPermissions.check(player, village).allowed()) {
            player.sendSystemMessage(WorkOrderService.noPermission(village));
            return;
        }
        VillageWorkOrders orders = village.getWorkOrders();
        boolean done = switch (action) {
            case CANCEL -> orders.cancel(orderId);
            case PAUSE -> orders.setPaused(orderId, true);
            case RESUME -> orders.setPaused(orderId, false);
            default -> false;
        };
        if (done) {
            String key = switch (action) {
                case CANCEL -> "workorder.cancelled";
                case PAUSE -> "workorder.paused";
                default -> "workorder.resumed";
            };
            player.sendSystemMessage(Component.translatable(key, orderId));
        }
        WorkOrdersRequest.sendResponse(player, new BlockPos(village.getCenter()));
    }

    @Override
    public CustomPacketPayload.Type<ManageWorkOrderMessage> type() {
        return TYPE;
    }
}
