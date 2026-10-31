package net.conczin.mca.expansion;

import net.conczin.mca.api.addon.Addons;
import net.conczin.mca.expansion.townhall.TownHall;
import net.conczin.mca.expansion.progression.VillageGrowth;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.VillageEconomyManager;
import net.conczin.mca.expansion.requests.Requests;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;

/**
 * The few calls MCA's own classes make into the expansions, gathered so the core needs one hook
 * per spot instead of one per expansion.
 */
public final class ExpansionHooks {
    private static final int REQUEST_CHECK_INTERVAL = 1200;

    private ExpansionHooks() {
    }

    /** Every village tick. */
    public static void villageTick(ServerLevel level, Village village, long time) {
        VillageEconomyManager.tick(level, village, time);
        VillageGrowth.tick(level, village, time);
        TownHall.villageTick(level, village, time);
        Addons.villageTick(level, village, time);
        if (Requests.enabled() && time % REQUEST_CHECK_INTERVAL == 0) {
            for (VillagerEntityMCA resident : village.getResidents(level)) {
                Requests.expireIfDue(level, resident);
            }
        }
    }
}
