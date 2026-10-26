package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The box a player drags out in the planning screen: the first click sets a corner, moving
 * the mouse spans the area on that layer, and scrolling stretches it up or down from that layer
 * (scrolling up raises its other side, scrolling down lowers it).
 */
public final class PlanningSelection {
    public static final int MAX_HEIGHT = 48;

    private PlanningTool tool = PlanningTool.DIG;
    private @Nullable BlockPos anchor;
    private @Nullable BlockPos corner;
    /** Layers above (positive) or below (negative) the clicked one the box reaches to. */
    private int extent;

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
        this.extent = 0;
    }

    /** Moves the opposite corner; it always stays on the anchor's layer. */
    public void update(BlockPos corner) {
        if (anchor != null) {
            this.corner = new BlockPos(corner.getX(), anchor.getY(), corner.getZ());
        }
    }

    /** Stretches the box a layer up (positive) or down (negative); it is at most {@link #MAX_HEIGHT} tall. */
    public void adjustExtent(int delta) {
        extent = Math.clamp(extent + delta, -(MAX_HEIGHT - 1), MAX_HEIGHT - 1);
    }

    public void clear() {
        anchor = null;
        corner = null;
        extent = 0;
    }

    /** The selected box: from the clicked layer up or down as far as it was stretched. */
    public Optional<WorkArea> area() {
        if (anchor == null || corner == null) {
            return Optional.empty();
        }
        int y = anchor.getY();
        int bottom = Math.min(y, y + extent);
        int top = Math.max(y, y + extent);
        return Optional.of(new WorkArea(
                Math.min(anchor.getX(), corner.getX()), bottom, Math.min(anchor.getZ(), corner.getZ()),
                Math.max(anchor.getX(), corner.getX()), top, Math.max(anchor.getZ(), corner.getZ())
        ));
    }
}
