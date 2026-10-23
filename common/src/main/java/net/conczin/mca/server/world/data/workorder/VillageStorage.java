package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The village's shared stock: every container inside a room registered as "storage".
 * Builders take their blocks from here and diggers bring their haul back.
 */
public final class VillageStorage {
    public static final String STORAGE_BUILDING = "storage";

    private VillageStorage() {
    }

    public static boolean exists(Village village) {
        return village.getBuildingsOfType(STORAGE_BUILDING).findAny().isPresent();
    }

    /** Positions of all usable containers, nearest to {@code near} first. */
    public static List<BlockPos> containers(ServerLevel level, Village village, BlockPos near) {
        return village.getBuildingsOfType(STORAGE_BUILDING)
                .map(Building::getBlocks)
                .flatMap(blocks -> blocks.values().stream())
                .flatMap(Collection::stream)
                .distinct()
                .filter(pos -> level.isLoaded(pos) && getContainer(level, pos).isPresent())
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(near)))
                .toList();
    }

    public static Optional<Container> getContainer(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // furnaces and brewing stands are workstations, not storage
        if (blockEntity instanceof Container container
            && !(blockEntity instanceof AbstractFurnaceBlockEntity)
            && !(blockEntity instanceof BrewingStandBlockEntity)) {
            return Optional.of(container);
        }
        return Optional.empty();
    }

    public static int count(ServerLevel level, Village village, Item item) {
        int total = 0;
        for (BlockPos pos : containers(level, village, new BlockPos(village.getCenter()))) {
            total += getContainer(level, pos).map(container -> count(container, item)).orElse(0);
        }
        return total;
    }

    /** Everything the storage holds, in one pass over its containers. */
    public static Map<Item, Integer> contents(ServerLevel level, Village village) {
        Map<Item, Integer> contents = new HashMap<>();
        for (BlockPos pos : containers(level, village, new BlockPos(village.getCenter()))) {
            getContainer(level, pos).ifPresent(container -> {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
                }
            });
        }
        return contents;
    }

    public static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The nearest container holding at least one {@code item}. */
    public static Optional<BlockPos> findContaining(ServerLevel level, Village village, Item item, BlockPos near) {
        return containers(level, village, near).stream()
                .filter(pos -> getContainer(level, pos).map(container -> count(container, item) > 0).orElse(false))
                .findFirst();
    }

    /** The nearest container with room for (part of) {@code stack}. */
    public static Optional<BlockPos> findSpaceFor(ServerLevel level, Village village, ItemStack stack, BlockPos near) {
        return containers(level, village, near).stream()
                .filter(pos -> getContainer(level, pos).map(container -> hasSpaceFor(container, stack)).orElse(false))
                .findFirst();
    }

    public static boolean hasSpaceFor(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                return container.canPlaceItem(slot, stack);
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves up to {@code max} of {@code item} from the container into {@code into}.
     *
     * @return how many were moved
     */
    public static int withdraw(Container container, Item item, int max, Container into) {
        int moved = 0;
        for (int slot = 0; slot < container.getContainerSize() && moved < max; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            ItemStack taken = stack.copyWithCount(Math.min(stack.getCount(), max - moved));
            ItemStack rest = insert(into, taken);
            int accepted = taken.getCount() - rest.getCount();
            if (accepted <= 0) {
                break;
            }
            stack.shrink(accepted);
            moved += accepted;
        }
        if (moved > 0) {
            container.setChanged();
            into.setChanged();
        }
        return moved;
    }

    /**
     * Puts as much of {@code stack} as fits into the container.
     *
     * @return what did not fit
     */
    public static ItemStack insert(Container container, ItemStack stack) {
        ItemStack rest = stack.copy();
        // top up matching stacks first, then use empty slots
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, rest)) {
                int space = Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing)) - existing.getCount();
                int move = Math.min(space, rest.getCount());
                if (move > 0) {
                    existing.grow(move);
                    rest.shrink(move);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, rest)) {
                int move = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), container.getMaxStackSize(rest)));
                container.setItem(slot, rest.split(move));
            }
        }
        if (rest.getCount() != stack.getCount()) {
            container.setChanged();
        }
        return rest;
    }
}
