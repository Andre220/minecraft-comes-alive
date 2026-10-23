package net.conczin.mca.expansion;

import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.Comparator;
import java.util.Optional;

/**
 * Finds the village a player with a blueprint is in, even when it barely knows itself yet: big villages only
 * know the bell and the beds their villagers found, and a village just reached hasn't been reported at all.
 * Looks further out, registers a bell nearby right away, and queues what is around the player so the building
 * they stand in gets known.
 */
public final class VillageFinder {
    /** How far outside its known buildings a player may still be in a village. */
    private static final int WIDE_MARGIN = 160;
    private static final int BELL_RADIUS = 128;
    private static final int REPORT_RADIUS = 48;

    private VillageFinder() {
    }

    public static Optional<Village> forPlayer(ServerPlayer player) {
        Optional<Village> village = Village.findNearest(player);
        if (village.isPresent()) {
            return village;
        }
        ServerLevel level = player.level();
        VillageManager manager = VillageManager.get(level);
        BlockPos pos = player.blockPosition();
        village = manager.findNearestVillage(pos, WIDE_MARGIN);
        if (village.isEmpty()) {
            // nobody reported this village yet: its bell makes it one
            level.getPoiManager().findAll(type -> type.is(PoiTypes.MEETING), bell -> true, pos, BELL_RADIUS, PoiManager.Occupancy.ANY)
                    .min(Comparator.comparingDouble(bell -> bell.distSqr(pos)))
                    .ifPresent(bell -> {
                        manager.cache.add(bell);
                        manager.processBuilding(bell);
                    });
            village = manager.findNearestVillage(pos, WIDE_MARGIN);
        }
        if (village.isPresent()) {
            level.getPoiManager().findAll(type -> true, poi -> !manager.cache.contains(poi), pos, REPORT_RADIUS, PoiManager.Occupancy.ANY)
                    .forEach(manager::reportBuilding);
        }
        return village;
    }
}
