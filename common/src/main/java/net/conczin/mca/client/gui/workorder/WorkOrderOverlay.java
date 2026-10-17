package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws planned and running work orders into the world. Gizmos only live for one tick, so
 * everything is drawn again every client tick while the planning screen is open.
 */
final class WorkOrderOverlay {
    static final int DIG_COLOR = 0xFFFF5544;
    static final int BUILD_COLOR = 0xFF44AAFF;
    static final int PAUSED_COLOR = 0xFF9A9A9A;
    static final int WAITING_COLOR = 0xFFFFCC33;
    static final int INVALID_COLOR = 0xFFFF2020;
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    /** Up to this many blocks the selection shows every single block it would change. */
    private static final int PER_BLOCK_PREVIEW_LIMIT = 512;
    private static final float LABEL_SCALE = 0.6F;

    private WorkOrderOverlay() {
    }

    static int colorOf(WorkOrderType type) {
        return type == WorkOrderType.DIG ? DIG_COLOR : BUILD_COLOR;
    }

    static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    static AABB box(WorkArea area) {
        return new AABB(area.minX(), area.minY(), area.minZ(), area.maxX() + 1, area.maxY() + 1, area.maxZ() + 1);
    }

    static void drawHover(BlockPos pos, int color) {
        Gizmos.cuboid(pos, 0.002F, GizmoStyle.stroke(color == 0 ? HOVER_COLOR : color, 2.0F));
    }

    /**
     * The selection being dragged: its outline, plus the single blocks that would actually
     * change when it is small enough.
     */
    static void drawSelection(ClientLevel level, WorkArea area, WorkOrderType type, @Nullable BlockState block, boolean valid) {
        int color = valid ? colorOf(type) : INVALID_COLOR;
        Gizmos.cuboid(box(area).inflate(0.01), GizmoStyle.strokeAndFill(color, 3.0F, withAlpha(color, 0x22)));
        if (!valid || area.volume() > PER_BLOCK_PREVIEW_LIMIT) {
            return;
        }
        int fill = withAlpha(color, 0x55);
        for (int y : area.layerOrder(type.topDown())) {
            for (BlockPos pos : area.layer(y)) {
                boolean changes = type == WorkOrderType.DIG
                        ? WorkOrderBlocks.needsDigging(level, pos)
                        : block != null && WorkOrderBlocks.needsBuilding(level, pos, block);
                if (changes) {
                    Gizmos.cuboid(pos, -0.08F, GizmoStyle.fill(fill));
                }
            }
        }
    }

    /** An existing order, with its id and progress floating above it. */
    static void drawOrder(WorkOrdersResponse.OrderInfo order, boolean highlighted) {
        int color = order.paused() ? PAUSED_COLOR : order.waiting() ? WAITING_COLOR : colorOf(order.type());
        float width = highlighted ? 4.0F : 2.0F;
        Gizmos.cuboid(box(order.area()), GizmoStyle.strokeAndFill(color, width, withAlpha(color, highlighted ? 0x30 : 0x14)));

        WorkArea area = order.area();
        Vec3 label = new Vec3((area.minX() + area.maxX() + 1) / 2.0, area.maxY() + 1.6, (area.minZ() + area.maxZ() + 1) / 2.0);
        String text = "#" + order.id() + "  " + Math.round(order.progress() * 100.0F) + "%";
        Gizmos.billboardText(text, label, TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE)).setAlwaysOnTop();
    }
}
