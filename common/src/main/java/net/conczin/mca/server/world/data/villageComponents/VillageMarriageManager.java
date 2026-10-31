package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.Opinions;
import net.conczin.mca.entity.ai.traits.TraitCompatibility;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VillageMarriageManager {
    private final Village village;

    public VillageMarriageManager(Village village) {
        this.village = village;
    }

    // if the amount of couples is low, let them marry
    public void marry(ServerLevel world) {
        if (world.getRandom().nextFloat() >= Config.getInstance().marriageChancePerMinute) {
            return;
        }

        //list all lonely villagers
        List<VillagerEntityMCA> allVillagers = village.getResidents(world);
        List<VillagerEntityMCA> availableVillagers = allVillagers.stream()
                .filter(v -> !v.isBaby())
                .filter(v -> !v.getRelationships().isMarried())
                .filter(v -> !v.getRelationships().isEngaged())
                .filter(v -> !v.getRelationships().isPromised())
                .collect(Collectors.toList());

        if (availableVillagers.size() <= 1 || availableVillagers.size() <= allVillagers.size() * (1.0 - village.getMarriageThreshold())) {
            return; // The village is too small.
        }

        //use the one with the least max hearts
        //this feels random yet respects relationships
        availableVillagers.sort(Comparator.comparingInt(a -> a.getVillagerBrain().getMemories().values().stream().map(Memories::getHearts).max(Integer::compare).orElse(0)));

        VillagerEntityMCA suitor = availableVillagers.removeFirst();

        // Find a potential mate: no one who dislikes the other, the one they get along with best
        availableVillagers.stream()
                .filter(suitor::canBeAttractedTo)
                .filter(i -> !suitor.getRelationships().getFamilyEntry().isRelative(i.getUUID()))
                .filter(i -> suitor.getOpinions().get(i.getUUID()) > Opinions.DISLIKED && i.getOpinions().get(suitor.getUUID()) > Opinions.DISLIKED)
                .map(i -> Map.entry(i, fondness(suitor, i)))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .ifPresent(mate -> {
                    suitor.getRelationships().marry(mate);
                    mate.getRelationships().marry(suitor);

                    // tell everyone about it
                    if (Config.getInstance().villagerMarriageNotification) {
                        village.broadCastMessage(world, "events.marry", suitor, mate);
                    }

                    // civil entry
                    village.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("events.marry", suitor.getName(), mate.getName())));
                });
    }

    /** How fond the two are of each other: their opinions, how their traits get along, and a little chance. */
    private static double fondness(VillagerEntityMCA a, VillagerEntityMCA b) {
        double opinions = (a.getOpinions().get(b.getUUID()) + b.getOpinions().get(a.getUUID())) / (2.0 * Opinions.MAX);
        return opinions + TraitCompatibility.between(a.getTraits(), b.getTraits()) * 0.5 + a.getRandom().nextDouble() * 0.3;
    }
}
