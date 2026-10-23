package net.conczin.mca.expansion;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.property.PropertyRules;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The containers in a villager's home: their private chests, where they keep their share of the
 * work and what they offer as rewards.
 */
public final class HomeChests {
    /** A home box larger than this is not searched for chests. */
    private static final int MAX_HOME_VOLUME = 16 * 16 * 8;

    private HomeChests() {
    }

    /** The containers in the villager's home that it may use, nearest to its bed first. */
    public static List<BlockPos> of(ServerLevel level, VillagerEntityMCA villager) {
        Optional<GlobalPos> bed = villager.getResidency().getHome().filter(home -> home.dimension() == level.dimension());
        Optional<Village> village = villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager));
        if (bed.isEmpty() || village.isEmpty()) {
            return List.of();
        }
        Optional<VillageBuilding> home = PropertyRules.homeOf(village.get(), bed.get().pos());
        if (home.isEmpty()) {
            return List.of();
        }
        BlockPos min = home.get().getPos0();
        BlockPos max = home.get().getPos1();
        long volume = (long) (Math.abs(max.getX() - min.getX()) + 1) * (Math.abs(max.getY() - min.getY()) + 1) * (Math.abs(max.getZ() - min.getZ()) + 1);
        if (volume > MAX_HOME_VOLUME) {
            return List.of();
        }
        List<BlockPos> containers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (VillageStorage.getContainer(level, pos).isPresent() && PropertyRules.canVillagerUse(villager, level, pos)) {
                containers.add(pos.immutable());
            }
        }
        containers.sort((a, b) -> Double.compare(a.distSqr(bed.get().pos()), b.distSqr(bed.get().pos())));
        return containers;
    }
}
