package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The shapes the build tool can plan, inspired by building wand mods: every one of them turns a few clicks into a map
 * of positions and block states, which becomes a piece of the draft.
 */
final class PlanningWands {
    /** Copies a repeat makes at most, to keep the draft sane. */
    static final int MAX_COPIES = 64;

    private PlanningWands() {
    }

    enum Shape {
        /** Drag a box and fill it. */
        BOX(Items.BRICKS),
        /** Drag a box, only its walls, floor and ceiling. */
        HOLLOW(Items.GLASS),
        /** Click the start, click the end; the lines so far raise with the scroll wheel, like a brush stroke. */
        LINE(Items.LEAD),
        /** Paint blocks while dragging, then raise the stroke with the scroll wheel. */
        BRUSH(Items.BRUSH),
        /** Pick blocks with a box, then drag to repeat them side by side. */
        REPEAT(Items.REPEATER),
        /** Pick blocks with a box, then put a mirrored copy on the side the cursor is. */
        MIRROR(Items.GLASS_PANE),
        /** Click or drag over planned blocks to take them out of the draft, one or a cube of them at a time. */
        ERASE(Items.BARRIER);

        private final Item icon;

        Shape(Item icon) {
            this.icon = icon;
        }

        Item icon() {
            return icon;
        }

        /** Whether the shape starts by dragging out a box. */
        boolean dragsBox() {
            return this != LINE && this != BRUSH && this != ERASE;
        }

        /** Whether the box picks blocks to copy rather than an area to fill. */
        boolean copies() {
            return this == REPEAT || this == MIRROR;
        }
    }

    static Map<BlockPos, BlockState> box(WorkArea area, BlockState state) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            blocks.put(pos.immutable(), state);
        }
        return blocks;
    }

    static Map<BlockPos, BlockState> hollow(WorkArea area, BlockState state) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (pos.getX() == area.minX() || pos.getX() == area.maxX() || pos.getY() == area.minY() || pos.getY() == area.maxY()
                || pos.getZ() == area.minZ() || pos.getZ() == area.maxZ()) {
                blocks.put(pos.immutable(), state);
            }
        }
        return blocks;
    }

    /** A straight line of blocks between two points, diagonals included. */
    static Map<BlockPos, BlockState> line(BlockPos from, BlockPos to, BlockState state) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            blocks.put(new BlockPos(
                    (int) Math.round(from.getX() + dx * t),
                    (int) Math.round(from.getY() + dy * t),
                    (int) Math.round(from.getZ() + dz * t)), state);
        }
        return blocks;
    }

    /**
     * A brush stroke raised into a wall. The stroke follows the ground, so every column starts where the ground is under
     * it. With {@code levelTop} the columns all reach the same top ({@code height} layers above the highest ground of the
     * stroke), a wall straight on top whatever the ground does; without, each column rises {@code height} layers from
     * its own ground. A negative height goes down instead (a trench or foundation), levelled at the bottom. The last
     * layer can take another block, for battlements or a cap.
     */
    static Map<BlockPos, BlockState> extrude(Map<BlockPos, BlockState> stroke, int height, @Nullable BlockState top, boolean levelTop) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        if (stroke.isEmpty()) {
            return blocks;
        }
        int layers = Math.max(1, Math.abs(height));
        boolean down = height < 0;
        int highest = stroke.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
        int lowest = stroke.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        stroke.forEach((base, state) -> {
            // from and to are inclusive, from the ground outwards
            int from = base.getY();
            int to;
            if (down) {
                to = levelTop ? lowest - layers + 1 : from - layers + 1;
            } else {
                to = levelTop ? highest + layers - 1 : from + layers - 1;
            }
            int step = to >= from ? 1 : -1;
            for (int y = from; ; y += step) {
                boolean last = y == to;
                BlockState placed = top != null && layers > 1 && last && !down ? top : state;
                blocks.put(new BlockPos(base.getX(), y, base.getZ()), placed);
                if (last) {
                    break;
                }
            }
        });
        return blocks;
    }

    /** The blocks inside a box, as the plan would leave them: planned blocks over the world's. Air is left out. */
    static Map<BlockPos, BlockState> copy(BlockGetter level, WorkArea area, Function<BlockPos, @Nullable BlockState> planned) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            BlockState state = planned.apply(pos);
            if (state == null) {
                state = level.getBlockState(pos);
            }
            if (!state.isAir()) {
                blocks.put(pos.immutable(), state);
            }
        }
        return blocks;
    }

    /**
     * Copies side by side towards {@code direction}, each one box further and {@code rise} blocks higher than the one
     * before (a rise of one turns a row of stairs into a roof).
     */
    static Map<BlockPos, BlockState> repeat(Map<BlockPos, BlockState> source, WorkArea area, Direction direction, int copies, int rise) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        int length = switch (direction.getAxis()) {
            case X -> area.sizeX();
            case Y -> area.sizeY();
            case Z -> area.sizeZ();
        };
        for (int copy = 1; copy <= Math.min(copies, MAX_COPIES); copy++) {
            int dx = direction.getStepX() * length * copy;
            int dy = direction.getStepY() * length * copy + rise * copy;
            int dz = direction.getStepZ() * length * copy;
            source.forEach((pos, state) -> blocks.put(pos.offset(dx, dy, dz), state));
        }
        return blocks;
    }

    /** A mirrored copy right next to the box, on the {@code side} given; stairs, doors and the like are mirrored too. */
    static Map<BlockPos, BlockState> mirror(Map<BlockPos, BlockState> source, WorkArea area, Direction side) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        // FRONT_BACK swaps east and west, LEFT_RIGHT north and south
        Mirror mirror = side.getAxis() == Direction.Axis.X ? Mirror.FRONT_BACK : Mirror.LEFT_RIGHT;
        source.forEach((pos, state) -> {
            BlockPos target = switch (side) {
                case EAST -> new BlockPos(2 * area.maxX() + 1 - pos.getX(), pos.getY(), pos.getZ());
                case WEST -> new BlockPos(2 * area.minX() - 1 - pos.getX(), pos.getY(), pos.getZ());
                case SOUTH -> new BlockPos(pos.getX(), pos.getY(), 2 * area.maxZ() + 1 - pos.getZ());
                case NORTH -> new BlockPos(pos.getX(), pos.getY(), 2 * area.minZ() - 1 - pos.getZ());
                case UP -> new BlockPos(pos.getX(), 2 * area.maxY() + 1 - pos.getY(), pos.getZ());
                case DOWN -> new BlockPos(pos.getX(), 2 * area.minY() - 1 - pos.getY(), pos.getZ());
            };
            blocks.put(target, side.getAxis() == Direction.Axis.Y ? PlanningPlacement.flip(state) : state.mirror(mirror));
        });
        return blocks;
    }

    /** The planned blocks with the second halves of doors and beds added where they are missing. */
    static Map<BlockPos, BlockState> completeHalves(Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, BlockState> complete = new LinkedHashMap<>(blocks);
        blocks.forEach((pos, state) -> PlanningPlacement.withSecondHalf(pos, state).forEach(complete::putIfAbsent));
        return complete;
    }
}
