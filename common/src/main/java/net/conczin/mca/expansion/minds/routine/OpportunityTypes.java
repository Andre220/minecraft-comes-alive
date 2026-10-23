package net.conczin.mca.expansion.minds.routine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What blocks and entities offer villagers with free time, from
 * {@code data/<namespace>/opportunities/*.json}:
 * <pre>{@code
 * {
 *   "kind": "read",
 *   "blocks": ["minecraft:lectern", "#minecraft:some_tag"],
 *   "properties": {"has_record": "true"},          // optional: block state must match
 *   "entities": [{"type": "minecraft:item_display", "tags": ["chair"]}],
 *   "appeal": 1.0,
 *   "rooms": {"library": 1.8},                     // optional: more appealing in these buildings
 *   "professions": {"minecraft:librarian": 1.5},  // optional: more appealing for these jobs
 *   "items": ["#some_mod:instruments"],            // optional: items that offer it wherever the villager is
 *                                                  //   (kind "play": carried, or picked up from a display)
 *   "contents": true,                              // optional: what lies in the block's container is used instead:
 *                                                  //   instruments played, food eaten, anything else looked at
 *   "hold": "minecraft:book",                      // optional: item held while doing it
 *   "particle": "minecraft:note"                   // optional: shown now and then
 * }
 * }</pre>
 * Blocks and entity types that don't exist (a mod not installed) are skipped, so integrations
 * with other mods are plain data files that only do something when the mod is there.
 */
public class OpportunityTypes extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("opportunities");

    public record EntityMatch(EntityType<?> type, Set<String> tags) {
        public boolean matches(Entity entity) {
            return entity.getType() == type && entity.entityTags().containsAll(tags);
        }
    }

    public record Type(Identifier id, Opportunity.Kind kind, Set<Block> blocks, List<TagKey<Block>> tags,
                       Map<String, String> properties, List<EntityMatch> entities, float appeal,
                       Map<String, Float> rooms, Map<Identifier, Float> professions,
                       Set<Item> items, List<TagKey<Item>> itemTags, boolean contents,
                       @Nullable Item hold, @Nullable ParticleOptions particle) {
        public boolean matches(BlockState state) {
            if (!blocks.contains(state.getBlock()) && tags.stream().noneMatch(state::is)) {
                return false;
            }
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null || !entry.getValue().equals(String.valueOf(state.getValue(property)).toLowerCase(java.util.Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        }

        public boolean matches(Entity entity) {
            return entities.stream().anyMatch(match -> match.matches(entity));
        }

        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && (items.contains(stack.getItem()) || itemTags.stream().anyMatch(stack::is));
        }

        public boolean hasItems() {
            return !items.isEmpty() || !itemTags.isEmpty();
        }

        public float appealFor(@Nullable String room, VillagerProfession profession) {
            float value = appeal;
            if (room != null) {
                value *= rooms.getOrDefault(room, 1.0F);
            }
            Identifier job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            return job == null ? value : value * professions.getOrDefault(job, 1.0F);
        }

        public @Nullable ItemStack holdStack() {
            return hold == null ? null : new ItemStack(hold);
        }
    }

    private static final List<Type> TYPES = new ArrayList<>();

    public OpportunityTypes() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("opportunities"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        TYPES.clear();
        data.forEach((id, json) -> {
            try {
                parse(id, GsonHelper.convertToJsonObject(json, "root")).ifPresent(TYPES::add);
            } catch (JsonParseException | IllegalArgumentException | IllegalStateException e) {
                MCA.LOGGER.error("Could not load villager opportunity {}", id, e);
            }
        });
    }

    private static Optional<Type> parse(Identifier id, JsonObject json) {
        Opportunity.Kind kind = Opportunity.Kind.valueOf(GsonHelper.getAsString(json, "kind").toUpperCase(java.util.Locale.ROOT));
        java.util.HashSet<Block> blocks = new java.util.HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "blocks", new com.google.gson.JsonArray())) {
            String name = element.getAsString();
            if (name.startsWith("#")) {
                tags.add(TagKey.create(Registries.BLOCK, Identifier.parse(name.substring(1))));
            } else {
                BuiltInRegistries.BLOCK.getOptional(Identifier.parse(name)).ifPresent(blocks::add);
            }
        }
        Map<String, String> properties = new HashMap<>();
        GsonHelper.getAsJsonObject(json, "properties", new JsonObject()).entrySet()
                .forEach(entry -> properties.put(entry.getKey(), entry.getValue().getAsString().toLowerCase(java.util.Locale.ROOT)));
        List<EntityMatch> entities = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "entities", new com.google.gson.JsonArray())) {
            JsonObject entity = GsonHelper.convertToJsonObject(element, "entity");
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(GsonHelper.getAsString(entity, "type")));
            if (type.isPresent()) {
                Set<String> required = new java.util.HashSet<>();
                GsonHelper.getAsJsonArray(entity, "tags", new com.google.gson.JsonArray()).forEach(tag -> required.add(tag.getAsString()));
                entities.add(new EntityMatch(type.get(), Set.copyOf(required)));
            }
        }
        java.util.HashSet<Item> items = new java.util.HashSet<>();
        List<TagKey<Item>> itemTags = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "items", new com.google.gson.JsonArray())) {
            String name = element.getAsString();
            if (name.startsWith("#")) {
                itemTags.add(TagKey.create(Registries.ITEM, Identifier.parse(name.substring(1))));
            } else {
                BuiltInRegistries.ITEM.getOptional(Identifier.parse(name)).ifPresent(items::add);
            }
        }
        if (blocks.isEmpty() && tags.isEmpty() && entities.isEmpty() && items.isEmpty() && itemTags.isEmpty()) {
            return Optional.empty(); // its mod is not installed
        }
        Map<String, Float> rooms = new HashMap<>();
        GsonHelper.getAsJsonObject(json, "rooms", new JsonObject()).entrySet().forEach(entry -> rooms.put(entry.getKey(), entry.getValue().getAsFloat()));
        Map<Identifier, Float> professions = new HashMap<>();
        GsonHelper.getAsJsonObject(json, "professions", new JsonObject()).entrySet()
                .forEach(entry -> professions.put(Identifier.parse(entry.getKey()), entry.getValue().getAsFloat()));
        Item hold = json.has("hold") ? BuiltInRegistries.ITEM.getOptional(Identifier.parse(GsonHelper.getAsString(json, "hold"))).orElse(null) : null;
        ParticleOptions particle = json.has("particle")
                ? BuiltInRegistries.PARTICLE_TYPE.getOptional(Identifier.parse(GsonHelper.getAsString(json, "particle")))
                        .filter(SimpleParticleType.class::isInstance).map(SimpleParticleType.class::cast).orElse(null)
                : null;
        return Optional.of(new Type(id, kind, Set.copyOf(blocks), List.copyOf(tags), Map.copyOf(properties), List.copyOf(entities),
                GsonHelper.getAsFloat(json, "appeal", 1.0F), Map.copyOf(rooms), Map.copyOf(professions),
                Set.copyOf(items), List.copyOf(itemTags), GsonHelper.getAsBoolean(json, "contents", false), hold, particle));
    }

    public static List<Type> types() {
        return List.copyOf(TYPES);
    }

    /** The first type a block state matches, if any. */
    public static @Nullable Type forBlock(BlockState state) {
        for (Type type : TYPES) {
            if (type.matches(state)) {
                return type;
            }
        }
        return null;
    }

    public static @Nullable Type forEntity(Entity entity) {
        for (Type type : TYPES) {
            if (type.matches(entity)) {
                return type;
            }
        }
        return null;
    }

    /** The first type an item offers (an instrument to play), if any. */
    public static @Nullable Type forItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Type type : TYPES) {
            if (type.hasItems() && type.matches(stack)) {
                return type;
            }
        }
        return null;
    }

    public static List<Type> itemTypes() {
        return TYPES.stream().filter(Type::hasItems).toList();
    }

    public static boolean hasEntityTypes() {
        return TYPES.stream().anyMatch(type -> !type.entities().isEmpty());
    }
}
