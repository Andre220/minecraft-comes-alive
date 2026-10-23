package net.conczin.mca.expansion.requests;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.EntityType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What villagers ask for and what things are worth, from {@code data/<namespace>/requests/*.json}:
 * <pre>{@code
 * {
 *   "values": {"minecraft:diamond": 64, "minecraft:emerald": 16},
 *   "objectives": [{"item": "minecraft:wheat", "count": 16, "worth": 4}]
 * }
 * }</pre>
 * Files are merged; items without a value are worth 1 per stack of 16.
 */
public class RequestPool extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("requests");

    /** Deliver {@code count} of {@code item}, or with a {@code target}, defeat that many of the mob. */
    public record Objective(Item item, @Nullable EntityType<?> target, int count, int worth) {
    }

    private static final Map<Item, Integer> VALUES = new HashMap<>();
    private static final List<Objective> OBJECTIVES = new ArrayList<>();

    public RequestPool() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("requests"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        VALUES.clear();
        OBJECTIVES.clear();
        data.forEach((id, json) -> {
            try {
                JsonObject object = GsonHelper.convertToJsonObject(json, "root");
                GsonHelper.getAsJsonObject(object, "values", new JsonObject()).entrySet().forEach(entry ->
                        VALUES.put(item(entry.getKey()), entry.getValue().getAsInt()));
                GsonHelper.getAsJsonArray(object, "objectives", new com.google.gson.JsonArray()).forEach(element -> {
                    JsonObject objective = GsonHelper.convertToJsonObject(element, "objective");
                    EntityType<?> target = objective.has("entity") ? entity(GsonHelper.getAsString(objective, "entity")) : null;
                    Item item = target == null ? item(GsonHelper.getAsString(objective, "item")) : Items.AIR;
                    OBJECTIVES.add(new Objective(item, target, GsonHelper.getAsInt(objective, "count", 1),
                            GsonHelper.getAsInt(objective, "worth", 1)));
                });
            } catch (JsonParseException | IllegalStateException e) {
                MCA.LOGGER.error("Could not load villager requests {}", id, e);
            }
        });
        OBJECTIVES.sort(Comparator.comparingInt(Objective::worth));
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == Items.AIR) {
            throw new JsonParseException("Unknown item " + id);
        }
        return item;
    }

    private static EntityType<?> entity(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new JsonParseException("Unknown entity " + id));
    }

    /** What a stack is worth as a reward. */
    public static int worth(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer each = VALUES.get(stack.getItem());
        if (each != null) {
            return each * stack.getCount() + (stack.isEnchanted() ? 16 : 0);
        }
        return Math.max(stack.isEnchanted() ? 16 : 0, stack.getCount() / 16);
    }

    public static int worth(Item item) {
        return worth(new ItemStack(item));
    }

    /**
     * An objective about as hard as {@code worth}: one of those closest to it, picked at random so
     * villagers don't always ask the same.
     */
    public static Objective objectiveFor(int worth, RandomSource random) {
        if (OBJECTIVES.isEmpty()) {
            return new Objective(Items.WHEAT, null, 16, 2);
        }
        List<Objective> sorted = new ArrayList<>(OBJECTIVES);
        sorted.sort(Comparator.comparingInt(objective -> Math.abs(objective.worth() - worth)));
        return sorted.get(random.nextInt(Math.min(3, sorted.size())));
    }

    public static List<Objective> objectives() {
        return List.copyOf(OBJECTIVES);
    }
}
