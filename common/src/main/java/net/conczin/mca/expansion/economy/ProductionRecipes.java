package net.conczin.mca.expansion.economy;

import com.google.gson.JsonArray;
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
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What workshops make, from {@code data/<namespace>/production/*.json}:
 * <pre>{@code
 * {
 *   "profession": "minecraft:butcher",
 *   "building": "bakery",              // optional: only in this building type
 *   "ticks": 600,                      // working time per batch
 *   "inputs": [{"item": "minecraft:wheat", "count": 3}],
 *   "outputs": [{"item": "minecraft:bread", "count": 1}],
 *   "rare": [{"item": "minecraft:cake", "count": 1, "chance": 0.02}],
 *   "optional": true                   // optional: skipped quietly when an item is missing (another mod's)
 * }
 * }</pre>
 */
public class ProductionRecipes extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("production");

    public record Stack(Item item, int count) {
        public ItemStack create() {
            return new ItemStack(item, count);
        }
    }

    public record Rare(Stack stack, double chance) {
    }

    public record Recipe(Identifier id, Identifier profession, @Nullable String building, int ticks,
                         List<Stack> inputs, List<Stack> outputs, List<Rare> rare) {
        public boolean appliesTo(VillagerProfession worker, @Nullable String buildingType) {
            return profession.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker))
                   && (building == null || building.equals(buildingType));
        }
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    public ProductionRecipes() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("production"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        RECIPES.clear();
        data.forEach((id, json) -> {
            JsonObject object = GsonHelper.convertToJsonObject(json, "root");
            try {
                RECIPES.add(parse(id, object));
            } catch (JsonParseException e) {
                if (GsonHelper.getAsBoolean(object, "optional", false)) {
                    MCA.LOGGER.debug("Skipping production recipe {}: {}", id, e.getMessage());
                } else {
                    MCA.LOGGER.error("Could not load production recipe {}", id, e);
                }
            }
        });
        // building specific recipes first, so a bakery bakes instead of doing the butcher's generic work
        RECIPES.sort((a, b) -> Boolean.compare(a.building() == null, b.building() == null));
    }

    private static Recipe parse(Identifier id, JsonObject json) {
        return new Recipe(id,
                Identifier.parse(GsonHelper.getAsString(json, "profession")),
                json.has("building") ? GsonHelper.getAsString(json, "building") : null,
                Math.max(20, GsonHelper.getAsInt(json, "ticks", 600)),
                stacks(GsonHelper.getAsJsonArray(json, "inputs", new JsonArray())),
                stacks(GsonHelper.getAsJsonArray(json, "outputs", new JsonArray())),
                GsonHelper.getAsJsonArray(json, "rare", new JsonArray()).asList().stream()
                        .map(element -> GsonHelper.convertToJsonObject(element, "rare"))
                        .map(object -> new Rare(stack(object), GsonHelper.getAsDouble(object, "chance")))
                        .toList());
    }

    private static List<Stack> stacks(JsonArray array) {
        return array.asList().stream().map(element -> stack(GsonHelper.convertToJsonObject(element, "stack"))).toList();
    }

    private static Stack stack(JsonObject json) {
        Identifier itemId = Identifier.parse(GsonHelper.getAsString(json, "item"));
        Item item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == Items.AIR) {
            throw new JsonParseException("Unknown item " + itemId);
        }
        return new Stack(item, GsonHelper.getAsInt(json, "count", 1));
    }

    /** The recipes a worker runs in a building of the given type, most specific first. */
    public static List<Recipe> forWorker(VillagerProfession profession, @Nullable String buildingType) {
        return RECIPES.stream().filter(recipe -> recipe.appliesTo(profession, buildingType)).toList();
    }

    /** What a workshop consumes: haulers bring these and leave them in its chest. */
    public static Set<Item> inputsFor(VillagerProfession profession, @Nullable String buildingType) {
        Set<Item> inputs = new HashSet<>();
        forWorker(profession, buildingType).forEach(recipe -> recipe.inputs().forEach(stack -> inputs.add(stack.item())));
        return inputs;
    }
}
