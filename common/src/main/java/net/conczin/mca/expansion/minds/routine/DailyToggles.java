package net.conczin.mca.expansion.minds.routine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Things villagers switch with the time of day as they pass by, from
 * {@code data/<namespace>/daily_toggles/*.json}: shutters and blinds open in the morning and closed
 * in the evening, lamps lit at dusk.
 * <pre>{@code
 * {
 *   "blocks": ["some_mod:oak_blinds", "#some_mod:lamps"],
 *   "property": "open",
 *   "active": "true",      // the value between "from" and "to"
 *   "inactive": "false",   // the value the rest of the day
 *   "from": 0,             // time of day in ticks (0 = 6 am, 12000 = 6 pm)
 *   "to": 12500            // may wrap around midnight (from 12500 to 23500 is the night)
 * }
 * }</pre>
 * Blocks that don't exist (a mod not installed) are skipped.
 */
public class DailyToggles extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("daily_toggles");

    public record Toggle(Identifier id, Set<Block> blocks, List<TagKey<Block>> tags, String property,
                         String active, String inactive, int from, int to) {
        public boolean matches(BlockState state) {
            return blocks.contains(state.getBlock()) || tags.stream().anyMatch(state::is);
        }

        public boolean isActive(long dayTime) {
            long time = Math.floorMod(dayTime, 24000L);
            return from <= to ? time >= from && time < to : time >= from || time < to;
        }

        /** The state the block should be in now, or null if it already is (or can't be). */
        public @Nullable BlockState wanted(BlockState state, long dayTime) {
            Property<?> found = state.getBlock().getStateDefinition().getProperty(property);
            return found == null ? null : with(state, found, isActive(dayTime) ? active : inactive);
        }

        private static <T extends Comparable<T>> @Nullable BlockState with(BlockState state, Property<T> property, String value) {
            Optional<T> parsed = property.getValue(value);
            if (parsed.isEmpty() || state.getValue(property).equals(parsed.get())) {
                return null;
            }
            return state.setValue(property, parsed.get());
        }
    }

    private static final List<Toggle> TOGGLES = new ArrayList<>();

    public DailyToggles() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("daily_toggles"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        TOGGLES.clear();
        data.forEach((id, json) -> {
            try {
                parse(id, GsonHelper.convertToJsonObject(json, "root")).ifPresent(TOGGLES::add);
            } catch (JsonParseException | IllegalArgumentException e) {
                MCA.LOGGER.error("Could not load daily toggle {}", id, e);
            }
        });
    }

    private static Optional<Toggle> parse(Identifier id, JsonObject json) {
        Set<Block> blocks = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "blocks")) {
            String name = element.getAsString();
            if (name.startsWith("#")) {
                tags.add(TagKey.create(Registries.BLOCK, Identifier.parse(name.substring(1))));
            } else {
                BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name)).ifPresent(blocks::add);
            }
        }
        if (blocks.isEmpty() && tags.isEmpty()) {
            return Optional.empty(); // its mod is not installed
        }
        return Optional.of(new Toggle(id, Set.copyOf(blocks), List.copyOf(tags),
                GsonHelper.getAsString(json, "property"),
                GsonHelper.getAsString(json, "active").toLowerCase(Locale.ROOT),
                GsonHelper.getAsString(json, "inactive").toLowerCase(Locale.ROOT),
                GsonHelper.getAsInt(json, "from"), GsonHelper.getAsInt(json, "to")));
    }

    public static boolean isEmpty() {
        return TOGGLES.isEmpty();
    }

    public static @Nullable Toggle forBlock(BlockState state) {
        for (Toggle toggle : TOGGLES) {
            if (toggle.matches(state)) {
                return toggle;
            }
        }
        return null;
    }
}
