package net.conczin.mca.expansion.economy;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.List;

/**
 * Village level upkeep of the economy: a village with a storage and enough workshops gets its
 * haulers from its jobless adults.
 */
public final class VillageEconomyManager {
    private static final int INTERVAL = 1200;

    private VillageEconomyManager() {
    }

    public static void tick(ServerLevel level, Village village, long time) {
        if (!EconomyFeature.enabled() || time % INTERVAL != 0 || !VillageStorage.exists(village)) {
            return;
        }
        List<VillagerEntityMCA> residents = village.getResidents(level);
        long haulers = residents.stream().filter(v -> v.getProfession() == EconomyRegistry.HAULER).count();
        if (haulers >= wantedHaulers(Workshops.of(level, village).size())) {
            return;
        }
        residents.stream()
                .filter(v -> v.isAlive() && v.getAgeState().isAdult())
                .filter(v -> v.getVillagerData().profession().is(VillagerProfession.NONE))
                .findFirst()
                .ifPresent(v -> v.setProfession(EconomyRegistry.HAULER));
    }

    /** One hauler for every few workshops, starting from two workshops. */
    public static int wantedHaulers(int workshops) {
        if (workshops < 2) {
            return 0;
        }
        int per = Math.max(1, EconomyFeature.config().economyWorkshopsPerHauler);
        return (workshops + per - 1) / per;
    }
}
