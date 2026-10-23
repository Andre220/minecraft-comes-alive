package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import org.jspecify.annotations.Nullable;

/**
 * What a click in the planning screen does.
 */
public enum PlanningTool {
    /** Drag an area to dig out; it grows downwards from the clicked block. */
    DIG(WorkOrderType.DIG, true, true),
    /** Drag an area to fill; it grows upwards from the spot above the clicked block. */
    BUILD(WorkOrderType.BUILD, false, false),
    /** Place a saved blueprint at the spot above the clicked block. */
    BLUEPRINT(WorkOrderType.BLUEPRINT, false, false),
    /** Drag a box around something built by hand to save it as a blueprint; grows upwards from the clicked block. */
    CAPTURE(null, false, true);

    private final @Nullable WorkOrderType orderType;
    private final boolean growsDown;
    private final boolean targetsBlock;

    PlanningTool(@Nullable WorkOrderType orderType, boolean growsDown, boolean targetsBlock) {
        this.orderType = orderType;
        this.growsDown = growsDown;
        this.targetsBlock = targetsBlock;
    }

    /** The work order this tool plans, none for capturing. */
    public @Nullable WorkOrderType orderType() {
        return orderType;
    }

    public boolean growsDown() {
        return growsDown;
    }

    /** Whether the cursor picks the block itself rather than the free spot on its face. */
    public boolean targetsBlock() {
        return targetsBlock;
    }

    /** Whether the tool is used by dragging out an area. */
    public boolean drags() {
        return this != BLUEPRINT;
    }
}
