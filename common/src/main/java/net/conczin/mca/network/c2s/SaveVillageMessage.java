package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record SaveVillageMessage(
        int id,
        float taxes,
        float populationThreshold,
        float marriageThreshold
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<SaveVillageMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("save_village"));
    public static final StreamCodec<FriendlyByteBuf, SaveVillageMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SaveVillageMessage::id,
            ByteBufCodecs.FLOAT, SaveVillageMessage::taxes,
            ByteBufCodecs.FLOAT, SaveVillageMessage::populationThreshold,
            ByteBufCodecs.FLOAT, SaveVillageMessage::marriageThreshold,
            SaveVillageMessage::new
    );

    public SaveVillageMessage(Village village) {
        this(village.getId(), village.getTaxes(), village.getPopulationThreshold(), village.getMarriageThreshold());
    }

    /** Each rule changes only for a sender with the rank the blueprint asks for it; the client alone does not decide. */
    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager.get(player.level()).getOrEmpty(id).ifPresent(village -> {
            Rank rank = Tasks.getRank(village, player);
            if (rank.isAtLeast(Rank.MERCHANT)) {
                village.setTaxes(taxes);
            }
            if (rank.isAtLeast(Rank.NOBLE)) {
                village.setPopulationThreshold(populationThreshold);
            }
            if (rank.isAtLeast(Rank.MAYOR)) {
                village.setMarriageThreshold(marriageThreshold);
            }
        });
    }

    @Override
    public Type<SaveVillageMessage> type() {
        return TYPE;
    }
}
