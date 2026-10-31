package net.conczin.mca.expansion.townhall;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Putting villagers on job sites: by the chief from the town hall's jobs panel, or by the villager leader once a day.
 * A villager on a job site takes its profession; one put there by the chief stays put.
 */
public final class JobAssignments {
    /** How much better a villager has to fit another job for the leader to move it. */
    private static final float BETTER_BY = 0.15F;

    private JobAssignments() {
    }

    /** A job site of the village: where it is, the profession it stands for, and who works there, if anyone. */
    public record Site(BlockPos pos, VillagerProfession profession, Optional<VillagerEntityMCA> worker) {
    }

    /** The profession a job site block stands for. */
    public static Optional<VillagerProfession> professionAt(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().getType(pos)
                .filter(VillagerProfession.ALL_ACQUIRABLE_JOBS)
                .flatMap(JobAssignments::professionFor);
    }

    private static Optional<VillagerProfession> professionFor(Holder<PoiType> type) {
        return BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(profession -> profession.heldJobSite().test(type)).findFirst();
    }

    /** Every job site inside the village, with its worker among the residents. */
    public static List<Site> sites(ServerLevel level, Village village, List<VillagerEntityMCA> residents) {
        BoundingBox box = village.getBox().inflatedBy(Village.BORDER_MARGIN / 2);
        BlockPos center = new BlockPos(village.getCenter());
        int radius = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 8;
        List<Site> sites = new ArrayList<>();
        Stream<BlockPos> positions = level.getPoiManager()
                .findAll(VillagerProfession.ALL_ACQUIRABLE_JOBS, pos -> box.isInside(pos), center, radius, PoiManager.Occupancy.ANY);
        positions.forEach(pos -> professionAt(level, pos).ifPresent(profession -> sites.add(new Site(pos.immutable(), profession,
                residents.stream().filter(v -> worksAt(v, pos)).findFirst()))));
        return sites;
    }

    static boolean worksAt(VillagerEntityMCA villager, BlockPos pos) {
        return villager.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE).filter(site -> site.pos().equals(pos)).isPresent();
    }

    /**
     * Puts a villager on a job site: it leaves its old one and takes the site's profession, keeping its trade level.
     * False when the block is no job site or someone else works there.
     */
    public static boolean assign(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        PoiManager poi = level.getPoiManager();
        Optional<Holder<PoiType>> type = poi.getType(pos).filter(VillagerProfession.ALL_ACQUIRABLE_JOBS);
        Optional<VillagerProfession> profession = type.flatMap(JobAssignments::professionFor);
        if (profession.isEmpty()) {
            return false;
        }
        if (!worksAt(villager, pos)) {
            if (poi.take(t -> t.equals(type.get()), (t, at) -> at.equals(pos), pos, 1).isEmpty()) {
                return false;
            }
            villager.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE)
                    .filter(site -> site.dimension() == level.dimension())
                    .ifPresent(site -> poi.release(site.pos()));
            villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), pos));
        }
        VillagerProfession wanted = profession.get();
        if (villager.getVillagerData().profession().value() != wanted) {
            int trades = villager.getVillagerData().level();
            villager.setVillagerData(villager.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(wanted)).withLevel(1));
            villager.setOffers(null);
            villager.getOffers();
            for (int l = 1; l < trades; l++) {
                villager.customLevelUp();
            }
            villager.refreshBrain(level);
        }
        level.broadcastEntityEvent(villager, (byte) 14);
        return true;
    }

    /**
     * The leader's daily round: the villager (not placed by the chief) that would fit a free job site much better than
     * its own job moves there. Only with attributes on, as they tell who fits where.
     */
    static void reassignOne(ServerLevel level, Village village, TownHallData data) {
        if (!VillagerAttributes.enabled()) {
            return;
        }
        List<VillagerEntityMCA> residents = village.getResidents(level).stream()
                .filter(v -> v.isAlive() && v.getAgeState().isAdult() && VillagerAttributes.isRolled(v)
                             && !data.isPinned(village.getId(), v.getUUID()))
                .toList();
        List<Site> free = sites(level, village, residents).stream().filter(site -> site.worker().isEmpty()).toList();
        VillagerEntityMCA bestVillager = null;
        Site bestSite = null;
        float bestGain = BETTER_BY;
        for (VillagerEntityMCA villager : residents) {
            float now = VillagerAttributes.aptitude(villager, villager.getProfession());
            for (Site site : free) {
                float gain = VillagerAttributes.aptitude(villager, site.profession()) - now;
                if (gain > bestGain) {
                    bestGain = gain;
                    bestVillager = villager;
                    bestSite = site;
                }
            }
        }
        if (bestVillager != null) {
            assign(level, bestVillager, bestSite.pos());
        }
    }
}
