package net.conczin.mca.expansion.talk;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Information behind conversation: with {@code villagerInfoInTalk} on, the interaction screen keeps a villager's
 * feelings, work, traits and friends to itself until the player asks ("How are you?", "Where do you work?"...). The
 * dialogue action {@code "show": "<panel>"} reveals a panel, so new questions are only data (data/mca/dialogues).
 */
public final class TalkPanels {
    /** Mood and needs. */
    public static final String FEELINGS = "feelings";
    /** Skill in the profession and the schedule of the day. */
    public static final String WORK = "work";
    public static final String TRAITS = "traits";
    /** Friends and rivals among the villagers. */
    public static final String FRIENDS = "friends";
    /** Attributes and level (with villagerAttributes on). */
    public static final String SKILLS = "skills";
    public static final List<String> PANELS = List.of(FEELINGS, WORK, TRAITS, FRIENDS, SKILLS);

    private TalkPanels() {
    }

    /** Whether panels wait for the conversation; on clients this follows the server's setting. */
    public static boolean enabled() {
        return Config.getServerConfig().villagerInfoInTalk;
    }

    /** Registers the {@code show} dialogue action. */
    public static void bootstrap() {
        Actions.register("show", GsonHelper::convertToString, panel -> (villager, player) ->
                Network.sendToPlayer(new Reveal(panel), player));
    }

    /** Server to client: the villager in the interaction screen tells about one panel. */
    public record Reveal(String panel) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Reveal> TYPE = new CustomPacketPayload.Type<>(MCA.locate("talk_reveal"));
        public static final StreamCodec<FriendlyByteBuf, Reveal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Reveal::panel,
                Reveal::new
        );

        @Override
        public void handle(Player player) {
            InteractScreen.reveal(panel);
        }

        @Override
        public CustomPacketPayload.Type<Reveal> type() {
            return TYPE;
        }
    }
}
