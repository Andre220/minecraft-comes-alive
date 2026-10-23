package net.conczin.mca.expansion.economy;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The village's workshops: every job site with a workshop chest, and what each one consumes.
 */
public final class Workshops {
    /** Margin around the outermost building in which job sites still count as the village's. */
    private static final int MARGIN = 16;

    public record Workshop(BlockPos station, VillagerProfession profession, @Nullable String building,
                           List<BlockPos> chests, Set<Item> inputs) {
    }

    private Workshops() {
    }

    public static List<Workshop> of(ServerLevel level, Village village) {
        BlockPos center = new BlockPos(village.getCenter());
        int radius = village.getBuildings().values().stream()
                .mapToInt(building -> (int) Math.sqrt(building.getCenter().distSqr(center)))
                .max().orElse(0) + MARGIN;
        return level.getPoiManager()
                .findAllWithType(holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), pos -> true, center, radius, PoiManager.Occupancy.ANY)
                .map(pair -> workshop(level, village, pair.getFirst(), pair.getSecond()))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(workshop -> workshop.station().distSqr(center)))
                .toList();
    }

    private static Optional<Workshop> workshop(ServerLevel level, Village village, Holder<PoiType> poi, BlockPos station) {
        Optional<VillagerProfession> profession = professionFor(poi);
        if (profession.isEmpty()) {
            return Optional.empty();
        }
        List<BlockPos> chests = ProductionSink.workshopContainers(level, village, station);
        if (chests.isEmpty()) {
            return Optional.empty();
        }
        String building = ProductionSink.workshopOf(village, station).map(Building::getType).orElse(null);
        return Optional.of(new Workshop(station, profession.get(), building, chests,
                ProductionRecipes.inputsFor(profession.get(), building)));
    }

    /** The profession that works at a job site. */
    public static Optional<VillagerProfession> professionFor(Holder<PoiType> poi) {
        return BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(profession -> profession.heldJobSite().test(poi))
                .findFirst();
    }
}
