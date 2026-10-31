package net.conczin.mca.expansion.minds;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Spending a villager's attribute points from the interaction screen: only the village chief may. */
public final class AttributeMessages {
    /** The lowest rank in the villager's village that counts as its chief. */
    static final Rank CHIEF = Rank.MAYOR;

    private AttributeMessages() {
    }

    /** Whether the player may spend this villager's points: a chief of its village. */
    public static boolean isChief(ServerPlayer player, VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager))
                .map(village -> Tasks.getRank(village, player).isAtLeast(CHIEF))
                .orElse(player.isCreative());
    }

    /** Client to server: spend one point of a villager on an attribute. */
    public record Spend(int villager, String stat) implements HandleablePayload {
        public static final CustomPacketPayload.Type<Spend> TYPE = new CustomPacketPayload.Type<>(MCA.locate("attribute_spend"));
        public static final StreamCodec<FriendlyByteBuf, Spend> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Spend::villager,
                ByteBufCodecs.STRING_UTF8, Spend::stat,
                Spend::new
        );

        @Override
        public void handleServer(ServerPlayer player) {
            VillagerAttributes.Stat chosen = VillagerAttributes.Stat.byId(stat);
            if (chosen == null || !VillagerAttributes.enabled()
                || !(player.level().getEntity(villager) instanceof VillagerEntityMCA target) || target.distanceToSqr(player) > 64.0) {
                return;
            }
            if (!isChief(player, target)) {
                player.sendSystemMessage(Component.translatable("attribute.mca.not_chief"));
                return;
            }
            VillagerAttributes.spend(target, chosen);
        }

        @Override
        public CustomPacketPayload.Type<Spend> type() {
            return TYPE;
        }
    }
}
