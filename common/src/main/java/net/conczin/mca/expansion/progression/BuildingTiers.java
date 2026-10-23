package net.conczin.mca.expansion.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Building tiers: a building of one of MCA's types grows into bigger tiers with more floor area
 * and more furniture, from {@code data/<namespace>/building_tiers/*.json}:
 * <pre>{@code
 * {
 *   "family": "house",
 *   "types": ["house", "big_house"],
 *   "tiers": [{"name": "small_house", "minArea": 0, "blocks": {"#minecraft:beds": 1}}, ...]
 * }
 * }</pre>
 * MCA's own building type stays as it is; the tier is a layer on top. Workshops (rooms with job
 * sites) get a level of their own from their area and their number of work stations.
 */
public class BuildingTiers extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("building_tiers");

    /** The largest building box searched for furniture. */
    private static final int MAX_VOLUME = 24 * 24 * 12;
    public static final int MAX_WORKSHOP_LEVEL = 4;
    /** Floor area a workshop needs per level. */
    public static final int WORKSHOP_AREA_PER_LEVEL = 15;

    public record Tier(String family, String name, int rank, int minArea, Map<String, Integer> blocks) {
    }

    public record Family(String name, Set<String> types, List<Tier> tiers) {
    }

    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();

    public BuildingTiers() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("building_tiers"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        FAMILIES.clear();
        data.forEach((id, json) -> {
            try {
                JsonObject object = GsonHelper.convertToJsonObject(json, "root");
                String family = GsonHelper.getAsString(object, "family");
                List<String> types = new ArrayList<>();
                GsonHelper.getAsJsonArray(object, "types").forEach(type -> types.add(type.getAsString()));
                List<Tier> tiers = new ArrayList<>();
                for (JsonElement element : GsonHelper.getAsJsonArray(object, "tiers")) {
                    JsonObject tier = GsonHelper.convertToJsonObject(element, "tier");
                    Map<String, Integer> blocks = new LinkedHashMap<>();
                    GsonHelper.getAsJsonObject(tier, "blocks", new JsonObject()).entrySet()
                            .forEach(entry -> blocks.put(entry.getKey(), entry.getValue().getAsInt()));
                    tiers.add(new Tier(family, GsonHelper.getAsString(tier, "name"), tiers.size(),
                            GsonHelper.getAsInt(tier, "minArea", 0), blocks));
                }
                FAMILIES.put(family, new Family(family, Set.copyOf(types), List.copyOf(tiers)));
            } catch (JsonParseException | IllegalStateException e) {
                MCA.LOGGER.error("Could not load building tiers {}", id, e);
            }
        });
    }

    public static Optional<Tier> tier(String name) {
        return FAMILIES.values().stream().flatMap(family -> family.tiers().stream())
                .filter(tier -> tier.name().equals(name)).findFirst();
    }

    public static Optional<Family> familyOf(Building building) {
        return FAMILIES.values().stream().filter(family -> family.types().contains(building.getType())).findFirst();
    }

    /** The highest tier the building meets, if it belongs to a family. */
    public static Optional<Tier> tierOf(ServerLevel level, Building building) {
        Optional<Family> family = familyOf(building);
        if (family.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> counts = count(level, building, family.get());
        if (counts == null) {
            return Optional.empty();
        }
        long area = building.getFloorFootprintArea();
        Tier best = null;
        for (Tier tier : family.get().tiers()) {
            boolean met = area >= tier.minArea()
                          && tier.blocks().entrySet().stream().allMatch(entry -> counts.getOrDefault(entry.getKey(), 0) >= entry.getValue());
            if (met) {
                best = tier;
            }
        }
        return Optional.ofNullable(best);
    }

    /** How many blocks of each requirement of the family's tiers the building holds; null when too large. */
    private static @Nullable Map<String, Integer> count(ServerLevel level, Building building, Family family) {
        Map<String, Integer> counts = new HashMap<>();
        List<String> keys = family.tiers().stream().flatMap(tier -> tier.blocks().keySet().stream()).distinct().toList();
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        if (volume(min, max) > MAX_VOLUME) {
            return null;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            for (String key : keys) {
                if (matches(state, key) && countable(state)) {
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /** Beds count once, by their head. */
    private static boolean countable(BlockState state) {
        return !(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)
               || state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
    }

    static boolean matches(BlockState state, String key) {
        if (key.startsWith("#")) {
            return state.is(TagKey.create(Registries.BLOCK, Identifier.parse(key.substring(1))));
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(key));
        return state.is(block);
    }

    private static long volume(BlockPos min, BlockPos max) {
        return (long) (Math.abs(max.getX() - min.getX()) + 1) * (Math.abs(max.getY() - min.getY()) + 1) * (Math.abs(max.getZ() - min.getZ()) + 1);
    }

    /** Job site blocks inside a building: each is a place for one worker. */
    public static int stations(ServerLevel level, Building building) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        if (volume(min, max) > MAX_VOLUME) {
            return 0;
        }
        int stations = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (PoiTypes.forState(level.getBlockState(pos)).filter(poi -> poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()) {
                stations++;
            }
        }
        return stations;
    }

    /**
     * A workshop's level, 0 for no workshop: one level per work station, as far as the floor area
     * allows ({@value #WORKSHOP_AREA_PER_LEVEL} per level), up to {@value #MAX_WORKSHOP_LEVEL}.
     */
    public static int workshopLevel(ServerLevel level, Building building) {
        int stations = stations(level, building);
        if (stations == 0) {
            return 0;
        }
        int byArea = (int) Math.max(1, building.getFloorFootprintArea() / WORKSHOP_AREA_PER_LEVEL);
        return Math.min(MAX_WORKSHOP_LEVEL, Math.min(stations, byArea));
    }

    public static List<Family> families() {
        return List.copyOf(FAMILIES.values());
    }
}
