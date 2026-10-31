package net.conczin.mca.addon;

import net.conczin.mca.addon.roads.RoadsAddon;
import net.conczin.mca.api.addon.Addons;

/**
 * The addons that still ship inside MCA's jar. Each moves out to a jar of its own once its code depends on MCA only
 * through the addon API (see docs/planning/addons.md).
 */
public final class BuiltInAddons {
    private BuiltInAddons() {
    }

    public static void register() {
        Addons.register(new RoadsAddon());
    }
}
