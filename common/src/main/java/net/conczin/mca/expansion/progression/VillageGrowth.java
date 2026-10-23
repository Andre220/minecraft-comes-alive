package net.conczin.mca.expansion.progression;

import net.conczin.mca.Config;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Watches villages grow: tells the players in a village when it reaches its next stage (and what
 * comes after) and when one of its buildings grows a tier.
 */
public final class VillageGrowth {
    private static final int INTERVAL = 2400;

    private VillageGrowth() {
    }

    public static boolean enabled() {
        return Config.getServerConfig().villageProgression;
    }

    public static void tick(ServerLevel level, Village village, long time) {
        if (!enabled() || time % INTERVAL != 0 || VillageStages.stages().isEmpty()) {
            return;
        }
        check(level, village);
    }

    /** Looks at the village now; announces what grew since the last look. */
    public static void check(ServerLevel level, Village village) {
        ProgressData data = ProgressData.get(level);
        List<ServerPlayer> players = level.players().stream().filter(village::isWithinBorder).toList();

        for (Building building : village.getBuildings().values()) {
            Optional<BuildingTiers.Tier> tier = BuildingTiers.tierOf(level, building);
            if (tier.isEmpty()) {
                continue;
            }
            int before = data.tier(village.getId(), building.getId());
            if (before >= 0 && tier.get().rank() > before) {
                List<String> residents = village.getResidents(building.getId());
                Component message = residents.isEmpty()
                        ? Component.translatable("villageStage.tier_up", Component.translatable("buildingTier." + tier.get().name()))
                        : Component.translatable("villageStage.tier_up.of", residents.getFirst(), Component.translatable("buildingTier." + tier.get().name()));
                players.forEach(player -> player.sendSystemMessage(message.copy().withStyle(ChatFormatting.GREEN)));
            }
            if (tier.get().rank() != before) {
                data.setTier(village.getId(), building.getId(), tier.get().rank());
            }
        }

        VillageProgress.Report report = VillageProgress.evaluate(level, village);
        int before = data.stage(village.getId());
        if (before >= 0 && report.reached() > before && report.current() != null) {
            Component reached = Component.translatable("villageStage.reached", village.getName(),
                    Component.translatable("villageStage." + report.current())).withStyle(ChatFormatting.GOLD);
            players.forEach(player -> player.sendSystemMessage(reached));
            VillageProgress.StageStatus next = report.next();
            if (next != null) {
                Component hint = Component.translatable("villageStage.next", Component.translatable("villageStage." + next.id() + ".hint"))
                        .withStyle(ChatFormatting.YELLOW);
                players.forEach(player -> player.sendSystemMessage(hint));
            }
        }
        if (report.reached() != before) {
            data.setStage(village.getId(), report.reached());
        }
    }
}
