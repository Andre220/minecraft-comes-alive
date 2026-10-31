package net.conczin.mca.expansion.townhall;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every village has a villager leading it: the wisest resident, elders first. When the leader dies or moves away, the
 * next one is chosen the same way. A player with the rank of mayor or above is the village's chief too; the villager
 * leader acts when no player does: once a day it moves one villager to a free job site it fits much better (never one
 * the chief put there by hand), and villagers spend piled up attribute points as it would (VillagerAttributes).
 */
public final class VillageLeaders {
    /** How often the leader is checked, in ticks. */
    static final long CHECK_INTERVAL = 1200;
    /** Score for being old, against wisdom 1 to 10 counted twice: an elder almost always leads. */
    private static final float ELDER_BONUS = 12.0F;

    private VillageLeaders() {
    }

    public static void tick(ServerLevel level, Village village, long time) {
        if (time % CHECK_INTERVAL != 0 || village.getPopulation() == 0) {
            return;
        }
        TownHallData data = TownHallData.get(level);
        Optional<UUID> leader = data.leader(village.getId());
        boolean stillHere = leader.filter(id -> village.getResidentsUUIDs().anyMatch(id::equals)).isPresent();
        if (!stillHere) {
            choose(level, village, data, leader.isPresent());
        }
        if (time % 24000 == 0) {
            leader.flatMap(id -> current(level, village)).ifPresent(chief -> JobAssignments.reassignOne(level, village, data));
        }
    }

    /** The leader, if it is around and loaded. */
    public static Optional<VillagerEntityMCA> current(ServerLevel level, Village village) {
        return TownHallData.get(level).leader(village.getId())
                .map(level::getEntity)
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast)
                .filter(VillagerEntityMCA::isAlive);
    }

    /** Wisdom (or intelligence without attributes) counted twice, being old a lot more, a little social on top. */
    static float score(VillagerEntityMCA villager) {
        float wisdom = VillagerAttributes.enabled() && VillagerAttributes.isRolled(villager)
                ? VillagerAttributes.get(villager, VillagerAttributes.Stat.WISDOM)
                : 1.0F + 9.0F * Skills.intelligence(villager);
        float social = VillagerAttributes.enabled() && VillagerAttributes.isRolled(villager)
                ? VillagerAttributes.get(villager, VillagerAttributes.Stat.SOCIAL) : VillagerAttributes.AVERAGE;
        return 2.0F * wisdom + (villager.getAgeState() == AgeState.ELDER ? ELDER_BONUS : 0.0F) + 0.5F * social;
    }

    private static void choose(ServerLevel level, Village village, TownHallData data, boolean succession) {
        List<VillagerEntityMCA> candidates = village.getResidents(level).stream()
                .filter(v -> v.isAlive() && v.getAgeState().isAdult())
                .toList();
        Optional<VillagerEntityMCA> best = candidates.stream().max(Comparator.comparingDouble(VillageLeaders::score));
        if (best.isEmpty()) {
            if (succession) {
                data.setLeader(village.getId(), null, "");
            }
            return;
        }
        VillagerEntityMCA leader = best.get();
        data.setLeader(village.getId(), leader.getUUID(), leader.getName().getString());
        Component message = Component.translatable(succession ? "village.leader.succession" : "village.leader.new",
                leader.getName(), village.getName());
        for (ServerPlayer player : level.players()) {
            if (village.isWithinBorder(player)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
