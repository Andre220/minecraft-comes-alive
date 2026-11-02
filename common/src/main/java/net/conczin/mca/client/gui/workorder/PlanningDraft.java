package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.MCA;
import net.conczin.mca.network.c2s.CommitPlanMessage;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything planned in the planning screen but not sent yet: areas to dig and pieces to build (blueprints, filled
 * boxes, brush strokes...). Pieces can be picked up again, moved, turned and dropped; only {@link #toMessage()} turns
 * the draft into work orders.
 */
final class PlanningDraft {
    /** An area to dig; with {@code demolish} chests and the like go too. */
    record Dig(WorkArea area, boolean demolish) {
    }

    /**
     * Something to build: a shape (a saved blueprint, or blocks planned one by one and kept in the same form) put down
     * with its lowest corner at {@code origin}, turned {@code rotation} quarter turns.
     */
    static final class Piece {
        private final Blueprint shape;
        /** Saved blueprints are sent by id, so their order knows which building it raises. */
        private final boolean savedBlueprint;
        private final Component label;
        private BlockPos origin;
        private int rotation;
        private @Nullable Map<BlockPos, BlockState> placed;

        Piece(Blueprint shape, boolean savedBlueprint, Component label, BlockPos origin, int rotation) {
            this.shape = shape;
            this.savedBlueprint = savedBlueprint;
            this.label = label;
            this.origin = origin.immutable();
            this.rotation = rotation;
        }

        Blueprint shape() {
            return shape;
        }

        boolean isSavedBlueprint() {
            return savedBlueprint;
        }

        Component label() {
            return label;
        }

        BlockPos origin() {
            return origin;
        }

        int rotation() {
            return rotation;
        }

        void moveTo(BlockPos origin, int rotation) {
            if (!origin.equals(this.origin) || rotation != this.rotation) {
                this.origin = origin.immutable();
                this.rotation = Math.floorMod(rotation, 4);
                placed = null;
            }
        }

        /** Every block of the piece where it stands now, air (to be cleared) included. */
        Map<BlockPos, BlockState> placed() {
            if (placed == null) {
                placed = shape.place(origin, rotation);
            }
            return placed;
        }

        WorkArea area() {
            return shape.area(origin, rotation);
        }

        long solidBlocks() {
            return placed().values().stream().filter(state -> !state.isAir()).count();
        }
    }

    private final List<Dig> digs = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private int nextShapeId;

    /** Blocks planned one by one become a shape like a blueprint's, relative to their lowest corner. */
    Piece pieceOf(Map<BlockPos, BlockState> blocks, Component label) {
        WorkArea area = WorkArea.around(blocks.keySet());
        BlockPos min = new BlockPos(area.minX(), area.minY(), area.minZ());
        List<Blueprint.Entry> entries = new ArrayList<>(blocks.size());
        blocks.forEach((pos, state) -> entries.add(new Blueprint.Entry(pos.subtract(min), state)));
        Blueprint shape = new Blueprint(MCA.locate("draft/" + nextShapeId++), label.getString(),
                area.sizeX(), area.sizeY(), area.sizeZ(), List.copyOf(entries));
        return new Piece(shape, false, label, min, 0);
    }

    List<Dig> digs() {
        return Collections.unmodifiableList(digs);
    }

    List<Piece> pieces() {
        return Collections.unmodifiableList(pieces);
    }

    void addDig(WorkArea area, boolean demolish) {
        digs.add(new Dig(area, demolish));
    }

    /** Takes back the newest dig area holding {@code pos}; false if there is none. */
    boolean removeDigAt(BlockPos pos) {
        for (int i = digs.size() - 1; i >= 0; i--) {
            if (digs.get(i).area().contains(pos)) {
                digs.remove(i);
                return true;
            }
        }
        return false;
    }

    void clearDigs() {
        digs.clear();
    }

    void addPiece(Piece piece) {
        if (!pieces.contains(piece)) {
            pieces.add(piece);
        }
    }

    void removePiece(Piece piece) {
        pieces.remove(piece);
    }

    /**
     * Takes the blocks at {@code positions} out of every piece holding them; a piece left with nothing goes. A saved
     * blueprint cut into is kept as loose blocks. How many blocks were taken.
     */
    int erase(Set<BlockPos> positions) {
        int taken = 0;
        for (int i = pieces.size() - 1; i >= 0; i--) {
            Piece piece = pieces.get(i);
            Map<BlockPos, BlockState> placed = piece.placed();
            int hits = 0;
            for (BlockPos pos : positions) {
                BlockState state = placed.get(pos);
                if (state != null && !state.isAir()) {
                    hits++;
                }
            }
            if (hits == 0) {
                continue;
            }
            taken += hits;
            Map<BlockPos, BlockState> rest = new LinkedHashMap<>(placed);
            rest.keySet().removeAll(positions);
            if (rest.values().stream().allMatch(BlockState::isAir)) {
                pieces.remove(i);
            } else {
                pieces.set(i, pieceOf(rest, piece.label()));
            }
        }
        return taken;
    }

    /** The newest piece with a block (not air) at {@code pos}. */
    @Nullable Piece pieceAt(BlockPos pos) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            BlockState state = pieces.get(i).placed().get(pos);
            if (state != null && !state.isAir()) {
                return pieces.get(i);
            }
        }
        return null;
    }

    /** The planned block at {@code pos}, newer pieces over older ones; null where nothing is planned. */
    @Nullable BlockState stateAt(BlockPos pos) {
        for (int i = pieces.size() - 1; i >= 0; i--) {
            BlockState state = pieces.get(i).placed().get(pos);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    boolean isEmpty() {
        return digs.isEmpty() && pieces.isEmpty();
    }

    void clear() {
        digs.clear();
        pieces.clear();
    }

    long totalBlocks() {
        long dug = digs.stream().mapToLong(dig -> dig.area().volume()).sum();
        return dug + pieces.stream().mapToLong(Piece::solidBlocks).sum();
    }

    /**
     * The whole draft as one message: dig areas as they are, saved blueprints by id, and everything else as single
     * blocks, newer pieces over older ones where they overlap.
     */
    CommitPlanMessage toMessage() {
        List<CommitPlanMessage.Dig> digList = digs.stream()
                .map(dig -> new CommitPlanMessage.Dig(new BlockPos(dig.area().minX(), dig.area().minY(), dig.area().minZ()),
                        new BlockPos(dig.area().maxX(), dig.area().maxY(), dig.area().maxZ()), dig.demolish()))
                .toList();
        List<CommitPlanMessage.Placement> placements = new ArrayList<>();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (Piece piece : pieces) {
            if (piece.isSavedBlueprint()) {
                placements.add(new CommitPlanMessage.Placement(piece.shape().id().toString(), piece.origin(), piece.rotation()));
            } else {
                piece.placed().forEach((pos, state) -> {
                    if (!state.isAir()) {
                        blocks.put(pos, state);
                    }
                });
            }
        }
        long[] positions = new long[blocks.size()];
        int[] states = new int[blocks.size()];
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            positions[i] = entry.getKey().asLong();
            states[i] = Block.getId(entry.getValue());
            i++;
        }
        return new CommitPlanMessage(digList, placements, positions, states);
    }
}
