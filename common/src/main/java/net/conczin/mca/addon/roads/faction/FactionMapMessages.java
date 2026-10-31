package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.addon.roads.client.FactionMapOverlay;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** The outposts a player has seen, with whose they are and how that band sees the player, for the village map. */
public final class FactionMapMessages {
    private FactionMapMessages() {
    }

    /** An outpost on the map: where, the band's name parts and banner color, and the player's standing with it. */
    public record Outpost(BlockPos pos, String place, int template, String color, int rgb, int standing) {
        static final StreamCodec<FriendlyByteBuf, Outpost> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Outpost::pos,
                ByteBufCodecs.STRING_UTF8, Outpost::place,
                ByteBufCodecs.VAR_INT, Outpost::template,
                ByteBufCodecs.STRING_UTF8, Outpost::color,
                ByteBufCodecs.INT, Outpost::rgb,
                ByteBufCodecs.VAR_INT, Outpost::standing,
                Outpost::new
        );

        public Factions.Standing standingValue() {
            return Factions.Standing.values()[Math.clamp(standing, 0, Factions.Standing.values().length - 1)];
        }
    }

    /** Client to server: the map opened. */
    public record Request() implements HandleablePayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(MCA.locate("faction_map_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public void handleServer(ServerPlayer player) {
            if (!Factions.enabled()) {
                return;
            }
            FactionData data = FactionData.get(player.level());
            List<Outpost> outposts = data.outposts().stream()
                    .filter(o -> data.knows(player.getUUID(), o.pos()))
                    .flatMap(o -> data.faction(o.faction()).stream().map(f -> new Outpost(o.pos(), f.place, f.template,
                            f.base().getSerializedName(), f.base().getTextureDiffuseColor(),
                            Factions.standing(player.level(), player.getUUID(), f.id()).ordinal())))
                    .toList();
            Network.sendToPlayer(new Response(outposts, Config.getServerConfig().factionTerritoryRadius), player);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }

    /** Server to client: the known outposts and how far each band's land reaches around them. */
    public record Response(List<Outpost> outposts, int radius) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(MCA.locate("faction_map_response"));
        public static final StreamCodec<FriendlyByteBuf, Response> STREAM_CODEC = StreamCodec.composite(
                Outpost.CODEC.apply(ByteBufCodecs.list()), Response::outposts,
                ByteBufCodecs.VAR_INT, Response::radius,
                Response::new
        );

        @Override
        public void handle(Player player) {
            FactionMapOverlay.receive(this);
        }

        @Override
        public CustomPacketPayload.Type<Response> type() {
            return TYPE;
        }
    }
}
