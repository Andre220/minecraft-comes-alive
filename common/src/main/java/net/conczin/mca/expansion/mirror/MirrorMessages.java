package net.conczin.mca.expansion.mirror;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Looking into a mirror: the server lets the player, the client opens the hair editor. */
public final class MirrorMessages {
    private MirrorMessages() {
    }

    /** Server to client: the player looks into a mirror. */
    public record Look() implements HandleablePayload {
        public static final CustomPacketPayload.Type<Look> TYPE = new CustomPacketPayload.Type<>(MCA.locate("mirror_look"));
        public static final StreamCodec<FriendlyByteBuf, Look> STREAM_CODEC = StreamCodec.unit(new Look());

        @Override
        public void handle(Player player) {
            MirrorScreen.open();
        }

        @Override
        public CustomPacketPayload.Type<Look> type() {
            return TYPE;
        }
    }
}
