package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks of one blueprint, for the planning screen's preview and material list.
 * Positions are packed into one int each (8 bits per axis), states are sent as ids.
 */
public record BlueprintResponse(String id, String name, int sizeX, int sizeY, int sizeZ, int[] positions, int[] states) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BlueprintResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("blueprint_response"));
    public static final StreamCodec<FriendlyByteBuf, BlueprintResponse> STREAM_CODEC = StreamCodec.of(
            (buf, message) -> {
                buf.writeUtf(message.id);
                buf.writeUtf(message.name);
                buf.writeVarInt(message.sizeX);
                buf.writeVarInt(message.sizeY);
                buf.writeVarInt(message.sizeZ);
                buf.writeVarIntArray(message.positions);
                buf.writeVarIntArray(message.states);
            },
            buf -> new BlueprintResponse(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(), buf.readVarIntArray())
    );
    /** Beyond this many blocks only the solid ones are sent; the preview then treats the rest as air. */
    private static final int MAX_ENTRIES_WITH_AIR = 65536;

    public static BlueprintResponse of(Blueprint blueprint) {
        List<Blueprint.Entry> entries = blueprint.blocks().size() > MAX_ENTRIES_WITH_AIR
                ? blueprint.blocks().stream().filter(entry -> !entry.state().isAir()).toList()
                : blueprint.blocks();
        int[] positions = new int[entries.size()];
        int[] states = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            BlockPos pos = entries.get(i).pos();
            positions[i] = (pos.getX() & 0xFF) | (pos.getY() & 0xFF) << 8 | (pos.getZ() & 0xFF) << 16;
            states[i] = Block.getId(entries.get(i).state());
        }
        return new BlueprintResponse(blueprint.id().toString(), blueprint.name(),
                blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), positions, states);
    }

    public Blueprint toBlueprint() {
        List<Blueprint.Entry> entries = new ArrayList<>(positions.length);
        for (int i = 0; i < positions.length && i < states.length; i++) {
            int packed = positions[i];
            entries.add(new Blueprint.Entry(new BlockPos(packed & 0xFF, packed >> 8 & 0xFF, packed >> 16 & 0xFF), Block.stateById(states[i])));
        }
        return new Blueprint(Identifier.parse(id), name, sizeX, sizeY, sizeZ, List.copyOf(entries));
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleBlueprint(this);
    }

    @Override
    public CustomPacketPayload.Type<BlueprintResponse> type() {
        return TYPE;
    }
}
