package net.conczin.mca.client;

import net.conczin.mca.Config;
import net.minecraft.network.chat.Component;

/**
 * Optional built-in resource pack with the human-like villager model for Fresh Animations. It holds
 * only MCA's own model; the animation comes from the user's Fresh Animations pack, which must be
 * below it. Registered on every launch, enabled by default only with the config switch.
 */
public final class FreshAnimationsIntegration {
    /** Pack folder, under {@code resourcepacks/} in the mod resources. */
    public static final String PACK_NAME = "mca_fresh_animations";
    public static final String PACK_PATH = "resourcepacks/" + PACK_NAME;
    public static final Component DISPLAY_NAME = Component.literal("MCA x Fresh Animations");

    private FreshAnimationsIntegration() {
    }

    public static boolean enabledByDefault() {
        Config config = Config.getInstance();
        return config.useSquidwardModels && config.freshAnimationsIntegration;
    }
}
