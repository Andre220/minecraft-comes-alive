package net.conczin.mca.api.addon.client;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The item an addon's dialogue answer shows in the radial conversation menu. */
public final class DialogueIcons {
    private static final Map<String, Item> ICONS = new HashMap<>();

    private DialogueIcons() {
    }

    public static void register(String answer, Item icon) {
        ICONS.put(answer, icon);
    }

    public static @Nullable Item get(String answer) {
        return ICONS.get(answer);
    }
}
