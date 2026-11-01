package net.conczin.mca.server.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.expansion.wardrobe.Wardrobes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Where the blocks villagers keep looking for are, so they do not scan thousands of blocks each time: a list per
 * chunk section and kind, made the first time a search reaches the section (sections without the block are skipped
 * by their palette) and kept up to date as blocks change. Sections are looked at again after {@link #REFRESH} ticks,
 * for blocks changed in ways that bypass {@code setBlock} (some world editors), and found positions are checked
 * again before they are returned. Server thread only.
 *
 * <p>Off with {@link Config#villagerBlockIndex}: the searches then scan blocks as before.
 */
public final class BlockIndex {
    /** Ticks a section's lists are trusted before it is looked at again. */
    private static final long REFRESH = 6000;
    private static final TagKey<Block> WELL_RIMS = TagKey.create(Registries.BLOCK, MCA.locate("well_rims"));

    /** What is indexed. The test is on the block alone; anything that also depends on neighbours is checked when searching. */
    public enum Kind {
        /** Cauldrons and sinks with water in them. */
        DRINKING_WATER(Needs::isDrinkingWater),
        /** Fences and walls, of which wells are made; the mouth beside them is checked when searching. */
        WELL_RIM(state -> state.is(WELL_RIMS)),
        CRAFTING_TABLE(state -> state.is(Blocks.CRAFTING_TABLE)),
        WARDROBE(state -> state.is(Wardrobes.WARDROBES)),
        ARCHERY_TARGET(state -> state.is(Blocks.TARGET));

        private final Predicate<BlockState> test;

        Kind(Predicate<BlockState> test) {
            this.test = test;
        }

        public boolean matches(BlockState state) {
            return test.test(state);
        }
    }

    private static final Kind[] KINDS = Kind.values();
    private static final Map<ServerLevel, LevelIndex> LEVELS = new WeakHashMap<>();

    private BlockIndex() {
    }

    public static boolean isEnabled() {
        return Config.getInstance().villagerBlockIndex;
    }

    private static final class Section {
        final long madeAt;
        /** By {@link Kind#ordinal()}; null for none of that kind. */
        final @Nullable LongList[] positions = new LongList[KINDS.length];

        Section(long madeAt) {
            this.madeAt = madeAt;
        }

        void add(Kind kind, long pos) {
            LongList list = positions[kind.ordinal()];
            if (list == null) {
                positions[kind.ordinal()] = list = new LongArrayList(4);
            }
            if (!list.contains(pos)) {
                list.add(pos);
            }
        }

        void remove(Kind kind, long pos) {
            LongList list = positions[kind.ordinal()];
            if (list != null) {
                list.rem(pos);
            }
        }
    }

    private static final class LevelIndex {
        final Long2ObjectMap<Section> sections = new Long2ObjectOpenHashMap<>();
        long lastCleanup;
    }

    /** Keeps the lists right as a block changes (from {@code ServerLevel.updatePOIOnBlockStateChange}). */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState || !level.getServer().isSameThread()) {
            return;
        }
        LevelIndex index = LEVELS.get(level);
        if (index == null) {
            return;
        }
        Section section = index.sections.get(SectionPos.asLong(pos));
        if (section == null) {
            return;
        }
        long at = pos.asLong();
        for (Kind kind : KINDS) {
            boolean was = kind.matches(oldState);
            boolean is = kind.matches(newState);
            if (was && !is) {
                section.remove(kind, at);
            } else if (is && !was) {
                section.add(kind, at);
            }
        }
    }

    /**
     * The nearest block of a kind within {@code radius} blocks across and {@code height} up and down of the center
     * that also passes {@code accept} (checked on the current world), if any. Only loaded chunks are searched.
     */
    public static Optional<BlockPos> findClosest(ServerLevel level, BlockPos center, int radius, int height, Kind kind, Predicate<BlockPos> accept) {
        LevelIndex index = LEVELS.computeIfAbsent(level, l -> new LevelIndex());
        long now = level.getGameTime();
        cleanup(index, now);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minY = center.getY() - height, maxY = center.getY() + height;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sx = SectionPos.blockToSectionCoord(minX); sx <= SectionPos.blockToSectionCoord(maxX); sx++) {
            for (int sz = SectionPos.blockToSectionCoord(minZ); sz <= SectionPos.blockToSectionCoord(maxZ); sz++) {
                if (!level.hasChunk(sx, sz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(sx, sz);
                for (int sy = SectionPos.blockToSectionCoord(minY); sy <= SectionPos.blockToSectionCoord(maxY); sy++) {
                    Section section = section(index, chunk, sx, sy, sz, now);
                    LongList list = section == null ? null : section.positions[kind.ordinal()];
                    if (list == null) {
                        continue;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        pos.set(list.getLong(i));
                        if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ) {
                            continue;
                        }
                        double distance = pos.distSqr(center);
                        if (distance < bestDistance && kind.matches(level.getBlockState(pos)) && accept.test(pos)) {
                            best = pos.immutable();
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether there is a block of the kind passing {@code accept} within the box. */
    public static boolean any(ServerLevel level, BlockPos center, int radius, int height, Kind kind, Predicate<BlockPos> accept) {
        return findClosest(level, center, radius, height, kind, accept).isPresent();
    }

    private static @Nullable Section section(LevelIndex index, LevelChunk chunk, int sx, int sy, int sz, long now) {
        int sectionIndex = chunk.getSectionIndexFromSectionY(sy);
        if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
            return null;
        }
        long key = SectionPos.asLong(sx, sy, sz);
        Section section = index.sections.get(key);
        if (section != null && now - section.madeAt < REFRESH && now >= section.madeAt) {
            return section;
        }
        section = scan(chunk.getSection(sectionIndex), sx, sy, sz, now);
        index.sections.put(key, section);
        return section;
    }

    private static Section scan(LevelChunkSection blocks, int sx, int sy, int sz, long now) {
        Section section = new Section(now);
        if (blocks.hasOnlyAir()) {
            return section;
        }
        for (Kind kind : KINDS) {
            if (!blocks.getStates().maybeHas(kind::matches)) {
                continue;
            }
            int x0 = SectionPos.sectionToBlockCoord(sx), y0 = SectionPos.sectionToBlockCoord(sy), z0 = SectionPos.sectionToBlockCoord(sz);
            for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                    for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                        if (kind.matches(blocks.getBlockState(x, y, z))) {
                            section.add(kind, BlockPos.asLong(x0 + x, y0 + y, z0 + z));
                        }
                    }
                }
            }
        }
        return section;
    }

    /** Forgets sections not looked at again in a while, so the index does not keep every place ever visited. */
    private static void cleanup(LevelIndex index, long now) {
        if (now - index.lastCleanup < REFRESH && now >= index.lastCleanup) {
            return;
        }
        index.lastCleanup = now;
        index.sections.values().removeIf(section -> now - section.madeAt >= REFRESH || now < section.madeAt);
    }

    /** Forgets everything, as when the server stops or tags reload. */
    public static void clear() {
        LEVELS.clear();
    }
}
