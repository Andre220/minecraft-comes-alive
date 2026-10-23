package net.conczin.mca.expansion.tabletop;

import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Items put down on blocks with the Tabletop mod, seen as a plain container: no code dependency, so all of this
 * does nothing when the mod is not installed. Slots 0 to 3 are the quarters of the block, 4 the middle.
 */
public final class PlacedItems {
    public static final Identifier BLOCK_ID = Identifier.fromNamespaceAndPath("tabletop", "placed_items");
    /** Blocks villagers set things down on: slabs, barrels and what data packs add. */
    public static final TagKey<Block> TABLE_TOPS = TagKey.create(Registries.BLOCK, MCA.locate("table_tops"));
    private static final int QUARTERS = 4;
    /** Items a workshop keeps on show at most. */
    private static final int MAX_ON_SHOW = 4;
    private static final float SHOW_CHANCE = 0.35F;

    private PlacedItems() {
    }

    public static Optional<Block> block() {
        return BuiltInRegistries.BLOCK.getOptional(BLOCK_ID);
    }

    public static boolean is(BlockState state) {
        return block().filter(state::is).isPresent();
    }

    public static Optional<Container> at(Level level, BlockPos pos) {
        return is(level.getBlockState(pos)) && level.getBlockEntity(pos) instanceof Container container
                ? Optional.of(container) : Optional.empty();
    }

    /**
     * Puts one of {@code stack} down at {@code pos} in {@code slot}, setting the placed items up if the spot is
     * empty. Takes it from the stack. False if there is no room (or no Tabletop).
     */
    public static boolean put(Level level, BlockPos pos, int slot, ItemStack stack) {
        Optional<Block> block = block();
        if (stack.isEmpty() || block.isEmpty()) {
            return false;
        }
        if (at(level, pos).isEmpty()) {
            BlockState here = level.getBlockState(pos);
            if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                return false;
            }
            level.setBlock(pos, block.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        Optional<Container> container = at(level, pos);
        if (container.isEmpty() || !container.get().getItem(slot).isEmpty() || !container.get().canPlaceItem(slot, stack)) {
            container.filter(Container::isEmpty).ifPresent(empty -> level.removeBlock(pos, false));
            return false;
        }
        container.get().setItem(slot, stack.split(1));
        return true;
    }

    /** Puts one of {@code stack} in any free quarter at {@code pos}, picked at random. */
    public static boolean putAnywhere(Level level, BlockPos pos, ItemStack stack, RandomSource random) {
        int first = random.nextInt(QUARTERS);
        for (int i = 0; i < QUARTERS; i++) {
            if (put(level, pos, (first + i) % QUARTERS, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Now and then a worker puts one of what it made on show on a table of its workshop (under a roof, a few at
     * most): a cook sets the inn's tables, a smith shows its tools. Takes it from the stack.
     */
    public static void maybeShow(ServerLevel level, @Nullable Building workshop, ItemStack produced, RandomSource random) {
        if (workshop == null || produced.isEmpty() || block().isEmpty() || random.nextFloat() >= SHOW_CHANCE) {
            return;
        }
        List<BlockPos> free = new ArrayList<>();
        int onShow = 0;
        for (BlockPos pos : BlockPos.betweenClosed(workshop.getPos0(), workshop.getPos1())) {
            BlockState state = level.getBlockState(pos);
            Optional<Container> placed = at(level, pos);
            if (placed.isPresent()) {
                for (int slot = 0; slot < placed.get().getContainerSize(); slot++) {
                    onShow += placed.get().getItem(slot).isEmpty() ? 0 : 1;
                }
            }
            if (state.is(TABLE_TOPS) && !level.canSeeSky(pos.above())) {
                BlockState above = level.getBlockState(pos.above());
                if (above.isAir() || is(above)) {
                    free.add(pos.above().immutable());
                }
            }
        }
        if (onShow >= MAX_ON_SHOW || free.isEmpty()) {
            return;
        }
        putAnywhere(level, free.get(random.nextInt(free.size())), produced, random);
    }
}
