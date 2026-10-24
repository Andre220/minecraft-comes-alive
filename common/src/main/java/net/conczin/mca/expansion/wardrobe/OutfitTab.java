package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The "MCA - Clothes" creative tab (client): an outfit for every clothing texture, and an empty one. */
public final class OutfitTab {
    private OutfitTab() {
    }

    public static List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>();
        items.add(OutfitItem.of(Wardrobes.OUTFIT, ""));
        List<String> ids = new ArrayList<>();
        for (String id : ClientSkinCatalog.clothing().keySet()) {
            // the clothing textures of the resource packs, not the online library's
            if (id.endsWith(".png") && id.contains(":skins/clothing/")) {
                ids.add(id);
            }
        }
        ids.sort(null);
        for (String id : ids) {
            items.add(OutfitItem.of(Wardrobes.OUTFIT, id));
        }
        return items;
    }
}
