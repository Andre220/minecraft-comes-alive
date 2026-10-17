package net.conczin.mca.server.world.data.workorder;

import java.util.Locale;
import java.util.Optional;

/**
 * What villagers do with every block inside a work order's box.
 */
public enum WorkOrderType {
    /** Remove every breakable block, top layer first. Drops go to the village storage. */
    DIG,
    /** Place the order's block in every replaceable spot, bottom layer first. Takes blocks from the village storage. */
    BUILD;

    private static final WorkOrderType[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WorkOrderType> byId(String id) {
        for (WorkOrderType type : VALUES) {
            if (type.id().equalsIgnoreCase(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Digging works from the top down so nobody stands on air; building from the bottom up so blocks have support. */
    public boolean topDown() {
        return this == DIG;
    }
}
