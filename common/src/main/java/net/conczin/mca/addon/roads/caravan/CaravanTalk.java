package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.addon.roads.faction.FactionData;
import net.conczin.mca.addon.roads.faction.Factions;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What caravan people tell in a conversation: where they come from and go to, and news. News is about villages
 * nobody has found yet (a name and which way it lies), or a faction's land along the road; a caravan only travels
 * between villages players know, but has heard of the others. Dialogue actions {@code caravan_from} and
 * {@code caravan_news}, for the answers of data/mca/dialogues/main.json with the constraint {@code caravan}.
 */
final class CaravanTalk {
    private static final double HEARD_OF = 3000.0;

    private CaravanTalk() {
    }

    static void bootstrap() {
        Actions.register("caravan_from", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, from(player.level(), villager)));
        Actions.register("caravan_news", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, news(player.level(), villager)));
    }

    private static void say(ServerPlayer player, Component line) {
        Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
    }

    private static Component from(ServerLevel level, VillagerEntityMCA villager) {
        Optional<CaravanData.Caravan> caravan = Caravans.caravanOf(level, villager);
        if (caravan.isEmpty()) {
            return Component.translatable("dialogue.caravan.from.none");
        }
        String from = Caravans.villageById(level, caravan.get().from).map(Village::getName).orElse(null);
        String to = Caravans.villageById(level, caravan.get().to).map(Village::getName).orElse(null);
        if (from != null && to != null) {
            return Component.translatable("dialogue.caravan.from.both", from, to);
        }
        if (to != null) {
            return Component.translatable("dialogue.caravan.from.to", to);
        }
        if (from != null) {
            return Component.translatable("dialogue.caravan.from.from", from);
        }
        return Component.translatable("dialogue.caravan.from.road");
    }

    /** A village not found yet, or a faction's land, or nothing new. */
    private static Component news(ServerLevel level, VillagerEntityMCA villager) {
        CaravanData data = CaravanData.get(level);
        BlockPos here = villager.blockPosition();
        List<Village> unknown = new ArrayList<>();
        for (Village village : VillageManager.get(level)) {
            BlockPos center = new BlockPos(village.getCenter());
            if (!data.isKnown(village.getId()) && center.distSqr(here) < HEARD_OF * HEARD_OF) {
                unknown.add(village);
            }
        }
        unknown.sort(Comparator.comparingDouble(v -> new BlockPos(v.getCenter()).distSqr(here)));
        if (!unknown.isEmpty() && level.getRandom().nextFloat() < 0.7F) {
            Village rumor = unknown.get(level.getRandom().nextInt(Math.min(3, unknown.size())));
            BlockPos center = new BlockPos(rumor.getCenter());
            return Component.translatable("dialogue.caravan.news.village", rumor.getName(), direction(here, center), distance(here, center));
        }
        if (Factions.enabled()) {
            FactionData factions = FactionData.get(level);
            Optional<FactionData.Outpost> outpost = factions.outposts().stream()
                    .min(Comparator.comparingDouble(o -> o.pos().distSqr(here)));
            if (outpost.isPresent()) {
                Optional<FactionData.Faction> band = factions.faction(outpost.get().faction());
                if (band.isPresent()) {
                    return Component.translatable("dialogue.caravan.news.faction", Factions.name(band.get()),
                            direction(here, outpost.get().pos()), distance(here, outpost.get().pos()));
                }
            }
        }
        return Component.translatable("dialogue.caravan.news.none");
    }

    /** Which way a place lies, as one of eight compass points. */
    private static Component direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), from.getZ() - to.getZ()));
        String[] points = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        return Component.translatable("dialogue.caravan.direction." + points[index]);
    }

    /** How far, roughly: a few days' walk, a day's walk, close by. */
    private static Component distance(BlockPos from, BlockPos to) {
        double blocks = Math.sqrt(from.distSqr(to));
        String key = blocks < 400 ? "near" : blocks < 1200 ? "day" : "far";
        return Component.translatable("dialogue.caravan.distance." + key);
    }
}
