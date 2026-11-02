package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;

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
    static final int BLUEPRINT_COLOR = 0xFFB27CFF;
    static final int CAPTURE_COLOR = 0xFF7CFF8A;
    static final int DEMOLISH_COLOR = 0xFFB0102A;
    static final int DRAFT_COLOR = 0xFF5AD7FF;
    static final int HELD_COLOR = 0xFFFFE066;
    static final int TOUCHING_COLOR = 0xFFFFD000;
    static final int HOVER_COLOR_PIECE = 0xFFFFFFFF;
    /** Ghost blocks drawn per tick at most, for everything together; big plans still show their outlines. */
    private static final int PREVIEW_BLOCK_LIMIT = 6000;
    private static int budget = PREVIEW_BLOCK_LIMIT;
    private static final int HOVER_COLOR = 0xFFFFFFFF;
    /** Up to this many blocks the selection shows every single block it would change. */
    private static final int PER_BLOCK_PREVIEW_LIMIT = 512;
    private static final float LABEL_SCALE = 0.6F;

    private WorkOrderOverlay() {
    }

    static int colorOf(WorkOrderType type) {
        return switch (type) {
            case DIG -> DIG_COLOR;
            case BUILD -> BUILD_COLOR;
            case BLUEPRINT -> BLUEPRINT_COLOR;
        };
    }

    static int colorOf(PlanningTool tool) {
        return tool.orderType() == null ? CAPTURE_COLOR : colorOf(tool.orderType());
    }

    /** The box being captured as a blueprint. */
    static void drawCapture(WorkArea area) {
        Gizmos.cuboid(box(area).inflate(0.02), GizmoStyle.strokeAndFill(CAPTURE_COLOR, 3.0F, withAlpha(CAPTURE_COLOR, 0x18)));
    }

    /**
     * A blueprint at the cursor: its outline, its blocks as ghosts, and in red the blocks
     * standing where it would go, which builders will have to remove first.
     */
    static void drawBlueprint(ClientLevel level, Map<BlockPos, BlockState> blocks, WorkArea area) {
        Gizmos.cuboid(box(area).inflate(0.01), GizmoStyle.stroke(BLUEPRINT_COLOR, 3.0F)).setAlwaysOnTop();
        int ghost = withAlpha(BLUEPRINT_COLOR, 0x50);
        int drawn = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (drawn >= budget) {
                break;
            }
            BlockPos pos = entry.getKey();
            BlockState planned = entry.getValue();
            BlockState current = level.getBlockState(pos);
            boolean blocked = !current.isAir() && !current.canBeReplaced() && !current.is(planned.getBlock());
            if (blocked) {
                Gizmos.cuboid(pos, 0.01F, GizmoStyle.strokeAndFill(INVALID_COLOR, 1.0F, withAlpha(INVALID_COLOR, 0x40))).setAlwaysOnTop();
                drawn++;
            } else if (!planned.isAir() && !current.is(planned.getBlock())) {
                Gizmos.cuboid(pos, -0.06F, GizmoStyle.fill(ghost));
                drawn++;
            }
        }
        budget -= drawn;
    }

    /** Called once per tick before drawing, to share the ghost block budget among everything drawn. */
    static void beginFrame() {
        budget = PREVIEW_BLOCK_LIMIT;
    }

    /**
     * A piece of the draft: its outline and its blocks as ghosts. Where the ground is in the way (a basement) the
     * blocks are drawn through the terrain, so a sunk blueprint stays visible.
     */
    static void drawPiece(ClientLevel level, Map<BlockPos, BlockState> blocks, WorkArea area, int color, boolean held) {
        Gizmos.cuboid(box(area).inflate(0.015), GizmoStyle.stroke(color, held ? 3.0F : 1.5F)).setAlwaysOnTop();
        int ghost = withAlpha(color, held ? 0x60 : 0x45);
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (budget <= 0) {
                return;
            }
            BlockPos pos = entry.getKey();
            BlockState planned = entry.getValue();
            BlockState current = level.getBlockState(pos);
            boolean blocked = !current.isAir() && !current.canBeReplaced() && !current.is(planned.getBlock());
            if (blocked) {
                // seen through the ground: this is what has to be dug out first
                Gizmos.cuboid(pos, 0.01F, GizmoStyle.strokeAndFill(INVALID_COLOR, 1.0F, withAlpha(INVALID_COLOR, 0x30))).setAlwaysOnTop();
                budget--;
            } else if (!planned.isAir() && !current.is(planned.getBlock())) {
                Gizmos.cuboid(pos, -0.06F, GizmoStyle.fill(ghost));
                budget--;
            }
        }
    }

    /** A line around the area at {@code y}: where the ground was before a blueprint was sunk into it. */
    static void drawGroundLevel(WorkArea area, int y) {
        Gizmos.cuboid(new AABB(area.minX(), y, area.minZ(), area.maxX() + 1, y + 0.02, area.maxZ() + 1),
                GizmoStyle.strokeAndFill(0xFF60FF60, 2.0F, 0x2060FF60)).setAlwaysOnTop();
    }

    /** A dig area of the draft. */
    static void drawDig(WorkArea area, boolean demolish, boolean highlighted) {
        int color = demolish ? DEMOLISH_COLOR : DIG_COLOR;
        Gizmos.cuboid(box(area).inflate(0.02), GizmoStyle.strokeAndFill(color, highlighted ? 4.0F : 2.0F,
                withAlpha(color, highlighted ? 0x40 : 0x26))).setAlwaysOnTop();
    }

    /**
     * The capture box waiting to be saved; the face under the cursor is lit up, as scrolling pushes or pulls it.
     * Blocks touching the box from outside are marked: they are usually the ones forgotten.
     */
    static void drawCaptureBox(WorkArea area, @Nullable Direction face, Iterable<BlockPos> touching) {
        AABB box = box(area).inflate(0.02);
        Gizmos.cuboid(box, GizmoStyle.strokeAndFill(CAPTURE_COLOR, 3.0F, withAlpha(CAPTURE_COLOR, 0x14))).setAlwaysOnTop();
        if (face != null) {
            AABB slab = switch (face) {
                case DOWN -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 0.05, box.maxZ);
                case UP -> new AABB(box.minX, box.maxY - 0.05, box.minZ, box.maxX, box.maxY, box.maxZ);
                case NORTH -> new AABB(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ + 0.05);
                case SOUTH -> new AABB(box.minX, box.minY, box.maxZ - 0.05, box.maxX, box.maxY, box.maxZ);
                case WEST -> new AABB(box.minX, box.minY, box.minZ, box.minX + 0.05, box.maxY, box.maxZ);
                case EAST -> new AABB(box.maxX - 0.05, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            };
            Gizmos.cuboid(slab, GizmoStyle.strokeAndFill(HOVER_COLOR, 2.0F, withAlpha(CAPTURE_COLOR, 0x60))).setAlwaysOnTop();
        }
        for (BlockPos pos : touching) {
            if (budget-- <= 0) {
                return;
            }
            Gizmos.cuboid(pos, 0.03F, GizmoStyle.strokeAndFill(TOUCHING_COLOR, 2.0F, withAlpha(TOUCHING_COLOR, 0x40))).setAlwaysOnTop();
        }
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
        drawSelection(level, area, type, block, valid, false);
    }

    static void drawSelection(ClientLevel level, WorkArea area, WorkOrderType type, @Nullable BlockState block, boolean valid, boolean demolish) {
        int color = valid ? colorOf(type) : INVALID_COLOR;
        Gizmos.cuboid(box(area).inflate(0.01), GizmoStyle.strokeAndFill(color, 3.0F, withAlpha(color, 0x22)));
        if (!valid || area.volume() > PER_BLOCK_PREVIEW_LIMIT) {
            return;
        }
        int fill = withAlpha(color, 0x55);
        for (int y : area.layerOrder(type.topDown())) {
            for (BlockPos pos : area.layer(y)) {
                boolean changes = type == WorkOrderType.DIG
                        ? WorkOrderBlocks.needsDigging(level, pos, demolish)
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
