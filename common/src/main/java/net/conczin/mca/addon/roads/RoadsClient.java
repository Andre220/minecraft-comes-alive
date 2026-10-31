package net.conczin.mca.addon.roads;

import net.conczin.mca.addon.roads.client.FactionMapOverlay;
import net.conczin.mca.api.addon.client.DialogueIcons;
import net.conczin.mca.api.addon.client.MapOverlay;
import net.minecraft.world.item.Items;

/** The client side of Bands and Roads: the bands' lands on the village map, and the icons of its questions. */
final class RoadsClient {
    private RoadsClient() {
    }

    static void init() {
        MapOverlay.register(FactionMapOverlay.INSTANCE);
        DialogueIcons.register("caravan_from", Items.COMPASS);
        DialogueIcons.register("caravan_news", Items.FILLED_MAP);
        DialogueIcons.register("bandit_band", Items.CROSSBOW);
        DialogueIcons.register("bandit_hire", Items.IRON_SWORD);
        DialogueIcons.register("bandit_truce", Items.EMERALD);
    }
}
