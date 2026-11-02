package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The planning screen's draft was confirmed: areas to dig, blueprints to raise, and blocks placed one by one
 * ({@code positions} packed with {@link BlockPos#asLong()}, {@code states} as block state ids).
 */
public record CommitPlanMessage(List<Dig> digs, List<Placement> blueprints, long[] positions, int[] states) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CommitPlanMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("commit_plan"));
    public static final StreamCodec<FriendlyByteBuf, CommitPlanMessage> STREAM_CODEC = StreamCodec.of(
            (buf, message) -> message.write(buf),
            CommitPlanMessage::read
    );
    /** More parts than this in one draft is not something a player plans by hand. */
    private static final int MAX_PARTS = 64;

    public record Dig(BlockPos from, BlockPos to, boolean demolish) {
    }

    public record Placement(String id, BlockPos origin, int rotation) {
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeCollection(digs, (b, dig) -> {
            b.writeBlockPos(dig.from());
            b.writeBlockPos(dig.to());
            b.writeBoolean(dig.demolish());
        });
        buf.writeCollection(blueprints, (b, placement) -> {
            b.writeUtf(placement.id());
            b.writeBlockPos(placement.origin());
            b.writeVarInt(placement.rotation());
        });
        buf.writeLongArray(positions);
        buf.writeVarIntArray(states);
    }

    private static CommitPlanMessage read(FriendlyByteBuf buf) {
        List<Dig> digs = buf.readList(b -> new Dig(b.readBlockPos(), b.readBlockPos(), b.readBoolean()));
        List<Placement> blueprints = buf.readList(b -> new Placement(b.readUtf(), b.readBlockPos(), b.readVarInt()));
        return new CommitPlanMessage(digs, blueprints, buf.readLongArray(), buf.readVarIntArray());
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (digs.size() + blueprints.size() > MAX_PARTS || positions.length != states.length
            || positions.length > WorkOrderService.maxInlineBlocks()) {
            player.sendSystemMessage(Component.translatable("workorder.error.draft_too_big"));
            return;
        }
        List<WorkOrderService.DigArea> digAreas = new ArrayList<>();
        for (Dig dig : digs) {
            WorkArea area = WorkArea.of(dig.from(), dig.to());
            if (!WorkOrdersRequest.isNearPlayer(player, area.center())) {
                player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
                return;
            }
            digAreas.add(new WorkOrderService.DigArea(area, dig.demolish()));
        }
        List<WorkOrderService.BlueprintPlacement> placements = new ArrayList<>();
        for (Placement placement : blueprints) {
            Identifier id = Identifier.tryParse(placement.id());
            if (id == null) {
                continue;
            }
            if (!WorkOrdersRequest.isNearPlayer(player, placement.origin())) {
                player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
                return;
            }
            placements.add(new WorkOrderService.BlueprintPlacement(id, placement.origin(), placement.rotation()));
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = BlockPos.of(positions[i]);
            BlockState state = Block.stateById(states[i]);
            if (state.isAir()) {
                continue;
            }
            if (!WorkOrdersRequest.isNearPlayer(player, pos)) {
                player.sendSystemMessage(Component.translatable("workorder.error.too_far"));
                return;
            }
            blocks.put(pos, state);
        }

        for (Component message : WorkOrderService.commit(player, digAreas, placements, blocks)) {
            player.sendSystemMessage(message);
        }
        BlockPos focus = !blocks.isEmpty() ? blocks.keySet().iterator().next()
                : !placements.isEmpty() ? placements.getFirst().origin()
                : !digAreas.isEmpty() ? digAreas.getFirst().area().center()
                : player.blockPosition();
        WorkOrdersRequest.sendResponse(player, focus);
    }

    @Override
    public CustomPacketPayload.Type<CommitPlanMessage> type() {
        return TYPE;
    }
}
