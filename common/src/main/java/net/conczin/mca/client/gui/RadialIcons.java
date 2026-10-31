package net.conczin.mca.client.gui;

import net.conczin.mca.api.addon.client.DialogueIcons;
import net.conczin.mca.api.addon.Addons;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/** The vanilla item standing for each action of the radial interaction menu. */
final class RadialIcons {
    private static final Map<String, Item> ICONS = Map.ofEntries(
            Map.entry("talk", Items.WRITABLE_BOOK),
            Map.entry("interact", Items.CAKE),
            Map.entry("backarrow", Items.ARROW),
            Map.entry("gift", Items.POPPY),
            Map.entry("trade", Items.EMERALD),
            Map.entry("inventory", Items.CHEST),
            Map.entry("work", Items.IRON_PICKAXE),
            Map.entry("familyTree", Items.OAK_SAPLING),
            Map.entry("commands", Items.GOAT_HORN),
            Map.entry("locations", Items.COMPASS),
            Map.entry("pick_up", Items.BED.pink()),
            Map.entry("armor", Items.IRON_CHESTPLATE),
            Map.entry("execute", Items.IRON_SWORD),
            Map.entry("pardon", Items.PAPER),
            Map.entry("request", Items.MAP), // expansion: requests
            Map.entry("negotiate", Items.EMERALD), // expansion: barter
            Map.entry("peek_inventory", Items.SPYGLASS), // expansion: barter
            Map.entry("profession.builder", Items.BRICKS),
            Map.entry("profession.miner", Items.IRON_PICKAXE),
            Map.entry("profession.hauler", Items.CHEST_MINECART),
            Map.entry("professions", Items.LECTERN),
            Map.entry("follow", Items.LEAD),
            Map.entry("stay", Items.CAMPFIRE),
            Map.entry("move", Items.LEATHER_BOOTS),
            Map.entry("ridehorse", Items.SADDLE),
            Map.entry("sethome", Items.BED.red()),
            Map.entry("setworkplace", Items.CRAFTING_TABLE),
            Map.entry("town_hall.overview", Items.BELL), // expansion: town hall
            Map.entry("town_hall.jobs", Items.SMITHING_TABLE),
            Map.entry("town_hall.map", Items.FILLED_MAP),
            Map.entry("town_hall.residents", Items.VILLAGER_SPAWN_EGG),
            Map.entry("town_hall.works", Items.BRICKS),
            Map.entry("town_hall.rules", Items.GOLD_INGOT),
            Map.entry("town_hall.progress", Items.EXPERIENCE_BOTTLE),
            Map.entry("town_hall.catalog", Items.BOOKSHELF),
            Map.entry("town_hall.rank", Items.GOLDEN_HELMET),
            Map.entry("gohome", Items.OAK_DOOR),
            Map.entry("fishing", Items.FISHING_ROD),
            Map.entry("harvesting", Items.WHEAT),
            Map.entry("hunting", Items.BOW),
            Map.entry("chopping", Items.IRON_AXE),
            Map.entry("stopworking", Items.BARRIER),
            Map.entry("profession.none", Items.PAPER),
            Map.entry("profession.guard", Items.IRON_SWORD),
            Map.entry("profession.archer", Items.BOW)
    );

    /** Answers of conversations (data/mca/dialogues), by answer name. */
    private static final Map<String, Item> DIALOGUE_ICONS = Map.ofEntries(
            Map.entry("rumors", Items.SPYGLASS),
            Map.entry("chat", Items.BOOK),
            Map.entry("how_are_you", Items.APPLE), // expansion: talk
            Map.entry("where_work", Items.CLOCK),
            Map.entry("about_you", Items.NAME_TAG),
            Map.entry("friends", Items.CAKE),
            Map.entry("good_at", Items.EXPERIENCE_BOTTLE),
            Map.entry("caravan_from", Items.LEAD), // expansion: caravans
            Map.entry("caravan_news", Items.FILLED_MAP),
            Map.entry("joke", Items.SLIME_BALL),
            Map.entry("story", Items.WRITTEN_BOOK),
            Map.entry("hug", Items.WOOL.pink()),
            Map.entry("flirt", Items.ROSE_BUSH),
            Map.entry("kiss", Items.HONEY_BOTTLE),
            Map.entry("shake_hand", Items.LEATHER),
            Map.entry("rock_paper_scissor", Items.TARGET),
            Map.entry("rock", Items.COBBLESTONE),
            Map.entry("paper", Items.PAPER),
            Map.entry("scissor", Items.SHEARS),
            Map.entry("hire", Items.GOLD_INGOT),
            Map.entry("stay", Items.CAMPFIRE),
            Map.entry("adopt", ItemsMCA.BABY_BOY),
            Map.entry("procreate", ItemsMCA.BABY_GIRL),
            Map.entry("procreate_engaged", ItemsMCA.BABY_GIRL),
            Map.entry("apologize", ItemsMCA.BOUQUET),
            Map.entry("divorceInitiate", ItemsMCA.DIVORCE_PAPERS),
            Map.entry("divorcePapers", ItemsMCA.DIVORCE_PAPERS),
            Map.entry("confirm", Items.EMERALD),
            Map.entry("cancel", Items.BARRIER),
            Map.entry("spent_night", Items.BED.red()),
            Map.entry("animal", Items.EGG),
            Map.entry("monster", Items.ROTTEN_FLESH),
            Map.entry("nether", Items.NETHERRACK),
            Map.entry("enderdragon", Items.DRAGON_HEAD),
            Map.entry("wither", Items.WITHER_SKELETON_SKULL),
            Map.entry("exploring", Items.MAP),
            Map.entry("settling", Items.CRAFTING_TABLE),
            Map.entry("creative", Items.PAINTING),
            Map.entry("generic", Items.BOOK),
            Map.entry("kind", Items.GOLDEN_APPLE),
            Map.entry("long", Items.CLOCK),
            Map.entry("short", Items.FEATHER)
    );

    private RadialIcons() {
    }

    static ItemStack dialogueIcon(String answer) {
        Addons.clientBootstrap();
        Item addonIcon = DialogueIcons.get(answer);
        return new ItemStack(addonIcon != null ? addonIcon : DIALOGUE_ICONS.getOrDefault(answer, Items.PAPER));
    }

    static ItemStack iconFor(String identifier) {
        String key = identifier.startsWith("gui.button.") ? identifier.substring("gui.button.".length()) : identifier;
        if (key.equals("orders")) {
            return new ItemStack(Items.CLOCK);
        }
        if (key.startsWith("order.")) {
            ActionType type = ActionType.byId(key.substring("order.".length()));
            if (type != null) {
                return type.icon();
            }
        }
        return new ItemStack(ICONS.getOrDefault(key, Items.PAPER));
    }
}
