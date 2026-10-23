package net.conczin.mca.expansion.economy;

import net.conczin.mca.expansion.tabletop.PlacedItems;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where produced goods go: the village's share (the tax slider) to the chest of the workshop the
 * villager works at, the rest stays with the villager. A workshop without a chest keeps
 * producing, but everything then stays with the villager.
 */
public final class ProductionSink {
    private ProductionSink() {
    }

    public static Optional<Village> villageOf(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager));
    }

    /** The villager's job site, when it is in the villager's dimension. */
    public static Optional<BlockPos> jobSite(ServerLevel level, VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }

    /**
     * How many of {@code count} produced items belong to the village: the tax share, with the
     * fraction rounded at random so small batches still pay their part on average.
     */
    public static int villageShare(Village village, int count, RandomSource random) {
        double share = count * Math.max(0.0, Math.min(1.0, village.getTaxes()));
        int whole = (int) share;
        return whole + (random.nextDouble() < share - whole ? 1 : 0);
    }

    /**
     * The workshop chests of a job site, nearest to it first: the containers in its building, or,
     * for a station outside any building (a farm's composter), the ones right next to it.
     */
    public static List<BlockPos> workshopContainers(ServerLevel level, Village village, BlockPos jobSite) {
        Optional<Building> workshop = workshopOf(village, jobSite);
        if (workshop.filter(building -> NOT_WORKSHOPS.contains(building.getType())).isPresent()) {
            return List.of();
        }
        Stream<BlockPos> candidates = workshop.isPresent()
                ? workshop.get().getBlocks().values().stream().flatMap(Collection::stream)
                : BlockPos.betweenClosedStream(jobSite.offset(-NEXT_TO_STATION, -1, -NEXT_TO_STATION),
                        jobSite.offset(NEXT_TO_STATION, 1, NEXT_TO_STATION)).map(BlockPos::immutable);
        return candidates
                .distinct()
                .filter(pos -> level.isLoaded(pos) && VillageStorage.getContainer(level, pos).isPresent())
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(jobSite)))
                .toList();
    }

    /** Whether a building of this type can be a workshop (not the storage, not a home). */
    public static boolean isWorkshopType(@Nullable String type) {
        return !NOT_WORKSHOPS.contains(type);
    }

    /** The type of the building a job site is in, if any. */
    public static @Nullable String buildingTypeOf(Village village, BlockPos jobSite) {
        return workshopOf(village, jobSite).map(Building::getType).orElse(null);
    }

    /**
     * Hands out what a villager produced at its station: the village's share straight into the
     * workshop chest, the rest (and whatever does not fit) into the villager's inventory.
     */
    public static void deliver(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos jobSite, List<ItemStack> produced) {
        List<BlockPos> chests = workshopContainers(level, village, jobSite);
        for (ItemStack stack : produced) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = stack.copy();
            // expansion: tabletop - now and then one goes on show on a table of the workshop
            PlacedItems.maybeShow(level, workshopOf(village, jobSite).orElse(null), rest, villager.getRandom());
            if (rest.isEmpty()) {
                continue;
            }
            int share = chests.isEmpty() ? 0 : villageShare(village, rest.getCount(), villager.getRandom());
            if (share > 0) {
                ItemStack villageShare = rest.split(share);
                for (BlockPos pos : chests) {
                    Optional<Container> container = VillageStorage.getContainer(level, pos);
                    if (container.isPresent() && !villageShare.isEmpty()) {
                        villageShare = VillageStorage.insert(container.get(), villageShare);
                    }
                }
                rest.grow(villageShare.getCount());
            }
            ItemStack overflow = villager.getInventory().addItem(rest);
            if (!overflow.isEmpty()) {
                villager.spawnAtLocation(level, overflow);
            }
        }
    }

    /**
     * The building a job site belongs to: the one that recorded the station block (a station
     * fills its floor cell, so rooms don't "contain" it), else the room around it.
     */
    public static Optional<Building> workshopOf(Village village, BlockPos jobSite) {
        return village.getBuildings().values().stream()
                .filter(building -> building.getBlocks().values().stream().anyMatch(positions -> positions.contains(jobSite)))
                .findFirst()
                .or(() -> village.findInteractionRoomAt(jobSite))
                .or(() -> village.getBuildingAt(jobSite));
    }

    /** Buildings whose chests are never a workshop's: the village's stock and people's homes. */
    private static final Set<String> NOT_WORKSHOPS = Set.of(VillageStorage.STORAGE_BUILDING, "house", "big_house", "inn");

    /** How far from a station outside any building its chest may stand. */
    private static final int NEXT_TO_STATION = 2;

    /** Whether the village storage already holds enough of an item to stop making more. */
    public static boolean isSaturated(ServerLevel level, Village village, Item item) {
        return VillageStorage.exists(village)
               && VillageStorage.count(level, village, item) >= EconomyFeature.config().economyStorageCap;
    }
}
