package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Choosing an outfit in a wardrobe or dresser: the server asks the player, the player answers by wearing it. */
public final class OutfitMessages {
    private OutfitMessages() {
    }

    /** Server to client: ask whether to wear the outfit in that closet's place. */
    public record Choose(BlockPos pos, int slot) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Choose> TYPE = new CustomPacketPayload.Type<>(MCA.locate("outfit_choose"));
        public static final StreamCodec<FriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choose::pos, ByteBufCodecs.VAR_INT, Choose::slot, Choose::new);

        @Override
        public void handle(Player player) {
            OutfitChoiceScreen.open(pos, slot);
        }

        @Override
        public CustomPacketPayload.Type<Choose> type() {
            return TYPE;
        }
    }

    /** Client to server: yes, wear it. */
    public record Wear(BlockPos pos, int slot) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Wear> TYPE = new CustomPacketPayload.Type<>(MCA.locate("outfit_wear"));
        public static final StreamCodec<FriendlyByteBuf, Wear> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Wear::pos, ByteBufCodecs.VAR_INT, Wear::slot, Wear::new);

        @Override
        public void handleServer(ServerPlayer player) {
            Wardrobes.wearFrom(player, pos, slot);
        }

        @Override
        public CustomPacketPayload.Type<Wear> type() {
            return TYPE;
        }
    }
}
