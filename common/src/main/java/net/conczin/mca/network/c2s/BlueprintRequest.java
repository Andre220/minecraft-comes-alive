package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BlueprintResponse;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The planning screen wants the blocks of a blueprint to preview it.
 */
public record BlueprintRequest(String id) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BlueprintRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("blueprint_request"));
    public static final StreamCodec<FriendlyByteBuf, BlueprintRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BlueprintRequest::id,
            BlueprintRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Identifier parsed = Identifier.tryParse(id);
        if (!WorkOrderPermissions.enabled() || parsed == null) {
            return;
        }
        BlueprintLibrary.get(player.level().getServer(), parsed)
                .ifPresent(blueprint -> Network.sendToPlayer(BlueprintResponse.of(blueprint), player));
    }

    @Override
    public CustomPacketPayload.Type<BlueprintRequest> type() {
        return TYPE;
    }
}
