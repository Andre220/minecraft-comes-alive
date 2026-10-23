package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** A player asks a villager to do something (queued as an order), or cancels one of their orders. */
public record QueueActionMessage(UUID villager, String action, boolean cancel) implements HandleablePayload {
    public static final CustomPacketPayload.Type<QueueActionMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("queue_action"));
    public static final StreamCodec<FriendlyByteBuf, QueueActionMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, QueueActionMessage::villager,
            ByteBufCodecs.STRING_UTF8, QueueActionMessage::action,
            ByteBufCodecs.BOOL, QueueActionMessage::cancel,
            QueueActionMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        ActionType type = ActionType.byId(action);
        if (type == null || !ActionQueue.isEnabled()
            || !(player.level().getEntity(villager) instanceof VillagerEntityMCA entity) || entity.distanceToSqr(player) > 16 * 16) {
            return;
        }
        if (cancel) {
            entity.getActionQueue().cancelOrder(type, player);
        } else {
            entity.getActionQueue().addOrder(type, player);
        }
    }

    @Override
    public CustomPacketPayload.Type<QueueActionMessage> type() {
        return TYPE;
    }
}
