package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.Config;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who may plan work orders for a village: players of high enough rank there, or anyone
 * holding the Villager Editor (whose orders also need no materials).
 */
public final class WorkOrderPermissions {
    public enum Access {
        DENIED,
        /** Allowed by village rank; builders use the village's materials. */
        RANK,
        /** Allowed by the Villager Editor; builders need no materials. */
        EDITOR;

        public boolean allowed() {
            return this != DENIED;
        }

        public boolean free() {
            return this == EDITOR;
        }
    }

    private WorkOrderPermissions() {
    }

    /** The whole feature is behind the {@code villagerWorkOrders} config option. */
    public static boolean enabled() {
        return Config.getServerConfig().villagerWorkOrders;
    }

    public static Access check(ServerPlayer player, Village village) {
        if (!enabled()) {
            return Access.DENIED;
        }
        if (holdsEditor(player)) {
            return Access.EDITOR;
        }
        return Tasks.getRank(village, player).isAtLeast(minRank()) ? Access.RANK : Access.DENIED;
    }

    public static boolean holdsEditor(Player player) {
        return player.getMainHandItem().is(ItemsMCA.VILLAGER_EDITOR) || player.getOffhandItem().is(ItemsMCA.VILLAGER_EDITOR);
    }

    public static Rank minRank() {
        return Rank.fromName(Config.getInstance().workOrderMinRank);
    }
}
