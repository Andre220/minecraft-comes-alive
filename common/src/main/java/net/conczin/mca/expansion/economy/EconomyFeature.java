package net.conczin.mca.expansion.economy;

import net.conczin.mca.CommonConfig;
import net.conczin.mca.Config;

/**
 * Entry point of the village economy expansion: gatherer professions, real workshop production
 * and haulers. Everything of the expansion lives in this package; MCA's own classes only call in
 * through short hooks marked {@code // expansion: economy}.
 */
public final class EconomyFeature {
    private EconomyFeature() {
    }

    /** The whole expansion is behind the {@code villagerEconomy} config option. */
    public static boolean enabled() {
        return config().villagerEconomy;
    }

    public static CommonConfig config() {
        return Config.getServerConfig();
    }
}
