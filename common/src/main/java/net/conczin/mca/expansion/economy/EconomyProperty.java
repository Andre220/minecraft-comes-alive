package net.conczin.mca.expansion.economy;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workshop chests belong to the villagers working there and to the village's haulers, so taking
 * from them is theft like taking from a home.
 */
public final class EconomyProperty {
    private static final double WORKER_RANGE = 64.0;
    private static final double HAULER_RANGE = 128.0;

    private EconomyProperty() {
    }

    /** The owners of a container in a workshop; empty when it is in none (or nobody works there). */
    public static List<UUID> workshopOwners(ServerLevel level, Village village, BlockPos container) {
        if (!EconomyFeature.enabled()) {
            return List.of();
        }
        Optional<Building> workshop = village.getBuildings().values().stream()
                .filter(building -> ProductionSink.isWorkshopType(building.getType()))
                .filter(building -> building.getBlocks().values().stream().anyMatch(positions -> positions.contains(container)))
                .findFirst();
        if (workshop.isEmpty()) {
            return List.of();
        }
        int id = workshop.get().getId();
        List<UUID> owners = new ArrayList<>();
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(container).inflate(WORKER_RANGE), VillagerEntityMCA::isAlive)) {
            boolean worksHere = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .filter(site -> site.dimension() == level.dimension())
                    .flatMap(site -> ProductionSink.workshopOf(village, site.pos()))
                    .filter(building -> building.getId() == id)
                    .isPresent();
            if (worksHere) {
                owners.add(villager.getUUID());
            }
        }
        if (owners.isEmpty()) {
            return owners; // an abandoned workshop is nobody's
        }
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(container).inflate(HAULER_RANGE), VillagerEntityMCA::isAlive)) {
            if (villager.getProfession() == EconomyRegistry.HAULER
                && villager.getResidency().getHomeVillage().filter(home -> home.getId() == village.getId()).isPresent()) {
                owners.add(villager.getUUID());
            }
        }
        return owners;
    }
}
