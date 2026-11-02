package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bands are born and die. A band grown big enough splits now and then: its farthest outpost breaks away under an
 * ambitious captain as a new band of the same kind, born its enemy, and players stand with it as they stood with the
 * old one; a band a player leads does not split. A band that lost its last outpost in a war, with no player riding with
 * it and more bands than a world starts with, is gone.
 */
final class FactionSplits {
    /** How many outposts a band needs before it can split. */
    private static final int SPLIT_OUTPOSTS = 3;
    private static final float SPLIT_CHANCE = 0.05F;
    /** How the new band and the old one stand: at war. */
    private static final int BAD_BLOOD = -70;

    private FactionSplits() {
    }

    /** Once a day: maybe a split. */
    static void day(ServerLevel level, FactionData data) {
        if (data.factions().size() >= Config.getServerConfig().factionMax) {
            return;
        }
        RandomSource random = level.getRandom();
        for (FactionData.Faction faction : data.factions()) {
            if (faction.leader() == null && data.outpostCount(faction.id()) >= SPLIT_OUTPOSTS && random.nextFloat() < SPLIT_CHANCE) {
                split(level, data, faction);
                return; // one a day at most
            }
        }
    }

    private static void split(ServerLevel level, FactionData data, FactionData.Faction old) {
        List<FactionData.Outpost> held = data.outposts().stream().filter(o -> o.faction() == old.id()).toList();
        double x = held.stream().mapToInt(o -> o.pos().getX()).average().orElse(0);
        double z = held.stream().mapToInt(o -> o.pos().getZ()).average().orElse(0);
        Optional<FactionData.Outpost> farthest = held.stream()
                .max(Comparator.comparingDouble(o -> sq(o.pos().getX() - x) + sq(o.pos().getZ() - z)));
        if (farthest.isEmpty()) {
            return;
        }
        FactionData.Faction born = Factions.create(level, data, FactionTypes.get(old.type()));
        data.setOwner(farthest.get(), born.id());
        FactionWars.setRelation(level, data, old.id(), born.id(), BAD_BLOOD);
        data.inheritReputation(old.id(), born.id()); // they knew the old band; the new one remembers
        BlockPos at = farthest.get().pos();
        FactionWars.tell(level, Component.translatable("faction.split", Factions.name(old), Factions.name(born), at.getX(), at.getZ()));
    }

    /** After a band lost an outpost: with none left, and more bands than a world starts with, it is gone. */
    static void fallIfEmpty(ServerLevel level, FactionData data, int faction) {
        if (data.outpostCount(faction) > 0 || data.hasMembers(faction) || data.factions().size() <= Config.getServerConfig().factionCount) {
            return;
        }
        data.faction(faction).ifPresent(band -> {
            FactionWars.tell(level, Component.translatable("faction.fallen", Factions.name(band)));
            data.remove(faction);
        });
    }

    private static double sq(double value) {
        return value * value;
    }
}
