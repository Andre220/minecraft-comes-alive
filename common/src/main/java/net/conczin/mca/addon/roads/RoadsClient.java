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
        DialogueIcons.register("bandit_join", Items.OMINOUS_BOTTLE);
        DialogueIcons.register("bandit_leave", Items.BARRIER);
        DialogueIcons.register("bandit_rally", Items.GOAT_HORN);
        DialogueIcons.register("village_fine_ask", Items.WRITABLE_BOOK);
        DialogueIcons.register("village_fine_pay", Items.EMERALD_BLOCK);
        DialogueIcons.register("caravan_order", Items.CHEST);
        DialogueIcons.register("caravan_pickup", Items.CHEST_MINECART);
        DialogueIcons.register("bandit_lodge", Items.BED.red());
        DialogueIcons.register("bandit_raid", Items.IRON_AXE);
        DialogueIcons.register("faction_found", Items.CAMPFIRE);
        DialogueIcons.register("faction_recruit", Items.LEAD);
        DialogueIcons.register("village_jail", Items.IRON_BARS);
        DialogueIcons.register("village_service", Items.WRITABLE_BOOK);
    }
}
