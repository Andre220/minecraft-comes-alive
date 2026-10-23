package net.conczin.mca.expansion.progression;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The village screen's progress page: the client asks for a village, the server answers with
 * the page already written.
 */
public final class ProgressMessages {
    private static final int MAX_LINES = 96;

    private ProgressMessages() {
    }

    public record Request(int villageId) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(MCA.locate("village_progress_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::villageId, Request::new);

        @Override
        public void handleServer(ServerPlayer player) {
            Optional<Village> village = VillageManager.get(player.level()).getOrEmpty(villageId);
            Network.sendToPlayer(new Response(village.map(v -> page(player, v)).orElseGet(List::of)), player);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }

    public record Response(List<Component> lines) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(MCA.locate("village_progress_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Response> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), Response::lines, Response::new);

        @Override
        public void handle(Player player) {
            ProgressCache.set(lines);
        }

        @Override
        public CustomPacketPayload.Type<Response> type() {
            return TYPE;
        }
    }

    /** The page: every stage in order, what the next one still needs, and the village's buildings by tier. */
    static List<Component> page(ServerPlayer player, Village village) {
        List<Component> lines = new ArrayList<>();
        if (!VillageGrowth.enabled()) {
            lines.add(Component.translatable("villageStage.disabled").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        VillageProgress.Report report = VillageProgress.evaluate(player.level(), village);
        String current = report.current();
        lines.add(Component.translatable("villageStage.current",
                current == null ? Component.translatable("villageStage.none") : Component.translatable("villageStage." + current))
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.empty());
        for (int i = 0; i < report.stages().size(); i++) {
            VillageProgress.StageStatus stage = report.stages().get(i);
            boolean next = i == report.reached();
            String mark = stage.done() ? "✔ " : next ? "➜ " : "· ";
            ChatFormatting color = stage.done() ? ChatFormatting.GREEN : next ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
            lines.add(Component.literal(mark).append(Component.translatable("villageStage." + stage.id())).withStyle(color));
            if (next) {
                lines.add(Component.literal("   ").append(Component.translatable("villageStage." + stage.id() + ".hint"))
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.WHITE));
            }
            if (!stage.done()) {
                for (VillageProgress.Line line : stage.lines()) {
                    MutableComponent text = Component.literal("   " + (line.done() ? "✔" : "✘") + " " + Math.min(line.have(), line.need()) + "/" + line.need() + " ")
                            .append(line.label());
                    lines.add(text.withStyle(line.done() ? ChatFormatting.DARK_GREEN : next ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                }
            }
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }
}
