package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The box a player drags out in the planning screen: the first click sets a corner, moving
 * the mouse spans the area on that layer and scrolling sets how deep (digging) or how high
 * (building, capturing) it goes.
 */
public final class PlanningSelection {
    public static final int MAX_HEIGHT = 48;

    private PlanningTool tool = PlanningTool.DIG;
    private @Nullable BlockPos anchor;
    private @Nullable BlockPos corner;
    private int height = 1;

    public PlanningTool getTool() {
        return tool;
    }

    /** Switching the tool drops a half finished selection. */
    public void setTool(PlanningTool tool) {
        if (this.tool != tool) {
            this.tool = tool;
            clear();
        }
    }

    public boolean isActive() {
        return anchor != null;
    }

    public @Nullable BlockPos getAnchor() {
        return anchor;
    }

    public void start(BlockPos anchor) {
        this.anchor = anchor.immutable();
        this.corner = this.anchor;
        this.height = 1;
    }

    /** Moves the opposite corner; it always stays on the anchor's layer. */
    public void update(BlockPos corner) {
        if (anchor != null) {
            this.corner = new BlockPos(corner.getX(), anchor.getY(), corner.getZ());
        }
    }

    public int getHeight() {
        return height;
    }

    public void adjustHeight(int delta) {
        height = Math.clamp(height + delta, 1, MAX_HEIGHT);
    }

    public void clear() {
        anchor = null;
        corner = null;
        height = 1;
    }

    /** The selected box: digging goes down from the clicked layer, everything else goes up from it. */
    public Optional<WorkArea> area() {
        if (anchor == null || corner == null) {
            return Optional.empty();
        }
        int y = anchor.getY();
        int bottom = tool.growsDown() ? y - (height - 1) : y;
        int top = tool.growsDown() ? y : y + (height - 1);
        return Optional.of(new WorkArea(
                Math.min(anchor.getX(), corner.getX()), bottom, Math.min(anchor.getZ(), corner.getZ()),
                Math.max(anchor.getX(), corner.getX()), top, Math.max(anchor.getZ(), corner.getZ())
        ));
    }
}
