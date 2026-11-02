package net.conczin.mca.addon.roads.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kinds of faction, from data/&lt;ns&gt;/faction_types/*.json: how often a new band is of the kind, the names it takes,
 * what its people carry and wear, and how hard it hits in a war fought out of sight. The bands of a world made before
 * kinds existed are "raiders".
 * <pre>{@code
 * {
 *   "weight": 3,               // how often a new band is of this kind
 *   "names": 2,                // name templates: faction.name.<kind>.0 .. 1 (raiders use faction.name.0 .. 5)
 *   "strength": 1.2,           // war strength out of sight
 *   "mainhand": [{"item": "minecraft:bow", "weight": 3}],
 *   "offhand":  [{"item": "minecraft:shield", "chance": 0.4}],
 *   "armor":    [{"item": "minecraft:leather_chestplate", "chance": 0.6}]
 * }
 * }</pre>
 */
public final class FactionTypes extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("faction_types");
    public static final String RAIDERS = "raiders";

    private record Weighted(Item item, int weight) {
    }

    private record Chance(Item item, float chance) {
    }

    public static final class Type {
        final String id;
        final int weight;
        final int names;
        final float strength;
        private final List<Weighted> mainhand;
        private final List<Chance> offhand;
        private final List<Chance> armor;

        Type(String id, int weight, int names, float strength, List<Weighted> mainhand, List<Chance> offhand, List<Chance> armor) {
            this.id = id;
            this.weight = weight;
            this.names = names;
            this.strength = strength;
            this.mainhand = mainhand;
            this.offhand = offhand;
            this.armor = armor;
        }

        public String id() {
            return id;
        }

        /** A weapon for one of its people. */
        @Nullable Item weapon(RandomSource random) {
            int total = mainhand.stream().mapToInt(Weighted::weight).sum();
            if (total <= 0) {
                return null;
            }
            int roll = random.nextInt(total);
            for (Weighted entry : mainhand) {
                roll -= entry.weight;
                if (roll < 0) {
                    return entry.item;
                }
            }
            return null;
        }

        /** What else one of its people carries in the off hand and wears, by slot. */
        Map<EquipmentSlot, Item> gear(RandomSource random) {
            Map<EquipmentSlot, Item> gear = new EnumMap<>(EquipmentSlot.class);
            for (Chance entry : offhand) {
                if (!gear.containsKey(EquipmentSlot.OFFHAND) && random.nextFloat() < entry.chance) {
                    gear.put(EquipmentSlot.OFFHAND, entry.item);
                }
            }
            for (Chance entry : armor) {
                EquipmentSlot slot = slotOf(entry.item);
                if (slot != null && !gear.containsKey(slot) && random.nextFloat() < entry.chance) {
                    gear.put(slot, entry.item);
                }
            }
            return gear;
        }

        private static @Nullable EquipmentSlot slotOf(Item item) {
            Equippable equippable = item.components().get(DataComponents.EQUIPPABLE);
            return equippable == null ? null : equippable.slot();
        }
    }

    /** What bands made before kinds existed fall back to. */
    private static final Type FALLBACK = new Type(RAIDERS, 0, 6, 1.0F,
            List.of(new Weighted(Items.CROSSBOW, 7), new Weighted(Items.IRON_AXE, 3)), List.of(), List.of());

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    public FactionTypes() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("faction_types"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        TYPES.clear();
        data.forEach((id, json) -> {
            try {
                Type type = parse(id.getPath(), GsonHelper.convertToJsonObject(json, "root"));
                TYPES.put(type.id, type);
            } catch (JsonParseException | IllegalArgumentException | IllegalStateException e) {
                MCA.LOGGER.error("Could not load faction type {}", id, e);
            }
        });
    }

    private static Type parse(String id, JsonObject json) {
        List<Weighted> mainhand = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "mainhand", new JsonArray())) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "mainhand");
            item(entry).ifPresent(item -> mainhand.add(new Weighted(item, GsonHelper.getAsInt(entry, "weight", 1))));
        }
        return new Type(id, GsonHelper.getAsInt(json, "weight", 1), Math.max(1, GsonHelper.getAsInt(json, "names", 1)),
                GsonHelper.getAsFloat(json, "strength", 1.0F), mainhand, chances(json, "offhand"), chances(json, "armor"));
    }

    private static List<Chance> chances(JsonObject json, String key) {
        List<Chance> list = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key, new JsonArray())) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, key);
            item(entry).ifPresent(item -> list.add(new Chance(item, GsonHelper.getAsFloat(entry, "chance", 1.0F))));
        }
        return list;
    }

    /** The item an entry names; entries of mods that are not installed are left out. */
    private static Optional<Item> item(JsonObject entry) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(GsonHelper.getAsString(entry, "item")))
                .filter(item -> item != Items.AIR);
    }

    public static Type get(String id) {
        Type type = TYPES.get(id);
        return type != null ? type : TYPES.getOrDefault(RAIDERS, FALLBACK);
    }

    /** The kind of a new band, by weight. */
    static Type pick(RandomSource random) {
        int total = TYPES.values().stream().mapToInt(t -> Math.max(0, t.weight)).sum();
        if (total <= 0) {
            return get(RAIDERS);
        }
        int roll = random.nextInt(total);
        for (Type type : TYPES.values()) {
            roll -= Math.max(0, type.weight);
            if (roll < 0) {
                return type;
            }
        }
        return get(RAIDERS);
    }
}
