package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A saved building: every block of its box, air included, relative to its lowest corner.
 * Shared by the server (which reads it from a structure file) and the planning screen (which
 * gets it over the network for the preview).
 */
public record Blueprint(Identifier id, String name, int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
    public record Entry(BlockPos pos, BlockState state) {
    }

    /** What building it takes: items per kind, and blocks nobody can carry (fluids, portals...). */
    public record Materials(Map<Item, Integer> items, Set<Block> unobtainable) {
        public int total() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public long solidBlocks() {
        return blocks.stream().filter(entry -> !entry.state().isAir()).count();
    }

    /** Every planned position with its block, turned and moved so the lowest corner is {@code origin}. */
    public Map<BlockPos, BlockState> place(BlockPos origin, int rotation) {
        Rotation turn = Rotation.values()[BlueprintTransform.normalize(rotation)];
        Map<BlockPos, BlockState> placed = new LinkedHashMap<>(blocks.size() * 2);
        for (Entry entry : blocks) {
            BlockPos pos = entry.pos();
            BlockPos turned = BlueprintTransform.rotate(pos.getX(), pos.getY(), pos.getZ(), sizeX, sizeZ, rotation);
            placed.put(origin.offset(turned), entry.state().rotate(turn));
        }
        return placed;
    }

    public WorkArea area(BlockPos origin, int rotation) {
        return BlueprintTransform.placedArea(origin, sizeX, sizeY, sizeZ, rotation);
    }

    public Materials materials() {
        return materials(blocks.stream().map(Entry::state).toList());
    }

    /**
     * Items needed for these blocks. Two-block things (doors, beds, tall flowers) count once,
     * and wall variants map to their item (a wall torch needs a torch) through {@link Block#asItem()}.
     */
    public static Materials materials(Collection<BlockState> states) {
        Map<Item, Integer> items = new HashMap<>();
        Set<Block> unobtainable = new LinkedHashSet<>();
        for (BlockState state : states) {
            if (state.isAir() || WorkOrderBlocks.isSecondHalf(state)) {
                continue;
            }
            Item item = state.getBlock().asItem();
            if (!WorkOrderBlocks.isObtainable(state)) {
                unobtainable.add(state.getBlock());
            } else {
                items.merge(item, 1, Integer::sum);
            }
        }
        return new Materials(items, unobtainable);
    }
}
