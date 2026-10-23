package net.conczin.mca.expansion.progression;

import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where a village stands on its path: which stages it completed and what the next one still needs.
 */
public final class VillageProgress {
    /** Rooms with job sites that are not workshops. */
    private static final Set<String> NOT_WORKSHOPS = Set.of("library", "school", "town_center", "inn");

    /** One requirement with how far the village is. */
    public record Line(Component label, int have, int need) {
        public boolean done() {
            return have >= need;
        }
    }

    public record StageStatus(String id, boolean done, List<Line> lines) {
    }

    /**
     * @param reached how many stages are done in a row from the start; the next one is the goal
     */
    public record Report(int reached, List<StageStatus> stages) {
        public @Nullable String current() {
            return reached > 0 ? stages.get(reached - 1).id() : null;
        }

        public @Nullable StageStatus next() {
            return reached < stages.size() ? stages.get(reached) : null;
        }
    }

    /** What a village has, gathered once per evaluation. */
    record Snapshot(Village village, Map<String, Integer> tierRanks, Map<Integer, Integer> workshopsByLevel, int population) {
        int buildings(String type) {
            return (int) village.getBuildingsOfType(type).count();
        }

        /** Buildings of the tier's family at that tier or above. */
        int tiers(String tierName) {
            Optional<BuildingTiers.Tier> tier = BuildingTiers.tier(tierName);
            if (tier.isEmpty()) {
                return 0;
            }
            return tierRanks.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(tier.get().family() + ":"))
                    .filter(entry -> Integer.parseInt(entry.getKey().substring(entry.getKey().indexOf(':') + 1)) >= tier.get().rank())
                    .mapToInt(Map.Entry::getValue).sum();
        }

        int workshops(int level) {
            return workshopsByLevel.entrySet().stream().filter(entry -> entry.getKey() >= level).mapToInt(Map.Entry::getValue).sum();
        }
    }

    private VillageProgress() {
    }

    static Snapshot snapshot(ServerLevel level, Village village) {
        Map<String, Integer> tierRanks = new HashMap<>();
        Map<Integer, Integer> workshops = new HashMap<>();
        for (Building building : village.getBuildings().values()) {
            BuildingTiers.tierOf(level, building).ifPresent(tier -> tierRanks.merge(tier.family() + ":" + tier.rank(), 1, Integer::sum));
            if (ProductionSink.isWorkshopType(building.getType()) && !NOT_WORKSHOPS.contains(building.getType())) {
                int workshopLevel = BuildingTiers.workshopLevel(level, building);
                if (workshopLevel > 0) {
                    workshops.merge(workshopLevel, 1, Integer::sum);
                }
            }
        }
        return new Snapshot(village, tierRanks, workshops, village.getPopulation());
    }

    public static Report evaluate(ServerLevel level, Village village) {
        Snapshot snapshot = snapshot(level, village);
        List<StageStatus> stages = new ArrayList<>();
        int reached = 0;
        boolean inRow = true;
        for (VillageStages.Stage stage : VillageStages.stages()) {
            List<Line> lines = stage.requirements().stream().map(requirement -> line(snapshot, requirement)).toList();
            boolean done = lines.stream().allMatch(Line::done);
            stages.add(new StageStatus(stage.id(), done, lines));
            if (inRow && done) {
                reached++;
            } else {
                inRow = false;
            }
        }
        return new Report(reached, stages);
    }

    static Line line(Snapshot snapshot, VillageStages.Requirement requirement) {
        return switch (requirement.kind()) {
            case BUILDING -> new Line(Component.translatable("buildingType." + requirement.target()),
                    snapshot.buildings(requirement.target()), requirement.count());
            case TIER -> new Line(Component.translatable("buildingTier." + requirement.target()),
                    snapshot.tiers(requirement.target()), requirement.count());
            case POPULATION -> new Line(Component.translatable("villageStage.req.population"),
                    snapshot.population(), requirement.count());
            case WORKSHOPS -> new Line(Component.translatable("villageStage.req.workshops", roman(requirement.level())),
                    snapshot.workshops(requirement.level()), requirement.count());
            case ANY -> {
                MutableComponent label = Component.empty();
                boolean done = false;
                for (int i = 0; i < requirement.of().size(); i++) {
                    Line child = line(snapshot, requirement.of().get(i));
                    if (i > 0) {
                        label.append(Component.translatable("villageStage.req.or"));
                    }
                    label.append(child.label());
                    done |= child.done();
                }
                yield new Line(label, done ? 1 : 0, 1);
            }
        };
    }

    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }
}
