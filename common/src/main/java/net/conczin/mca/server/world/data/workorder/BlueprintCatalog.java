package net.conczin.mca.server.world.data.workorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the blueprints of a world are sorted: sections with a name and an icon (houses, windows, furniture...), which
 * section each blueprint belongs to, and which ones the players hid. Kept next to the captured blueprints, in
 * {@code generated/mca/blueprint_sections.json}.
 */
public final class BlueprintCatalog {
    /** Blueprints nobody sorted yet, like the ones shipped with the mod, land here. */
    public static final String DEFAULT_SECTION = "buildings";
    public static final int MAX_SECTIONS = 24;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static @Nullable MinecraftServer loadedFor;
    private static @Nullable BlueprintCatalog loaded;

    /** {@code name} is either typed by a player or a translation key, for the default sections. */
    public record Section(String id, String name, String icon) {
    }

    private final Path file;
    private final List<Section> sections = new ArrayList<>();
    private final Map<String, String> assignments = new HashMap<>();
    private final Set<String> hidden = new LinkedHashSet<>();

    private BlueprintCatalog(Path file) {
        this.file = file;
    }

    public static synchronized BlueprintCatalog get(MinecraftServer server) {
        if (loaded == null || loadedFor != server) {
            loaded = load(server.getWorldPath(LevelResource.GENERATED_DIR).resolve(MCA.MOD_ID).resolve("blueprint_sections.json"));
            loadedFor = server;
        }
        return loaded;
    }

    private static BlueprintCatalog load(Path file) {
        BlueprintCatalog catalog = new BlueprintCatalog(file);
        if (!Files.exists(file)) {
            catalog.sections.add(new Section(DEFAULT_SECTION, "blueprint.section.buildings", "minecraft:bricks"));
            catalog.sections.add(new Section("pieces", "blueprint.section.pieces", "minecraft:flower_pot"));
            return catalog;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : json.getAsJsonArray("sections")) {
                JsonObject section = element.getAsJsonObject();
                catalog.sections.add(new Section(section.get("id").getAsString(), section.get("name").getAsString(),
                        section.get("icon").getAsString()));
            }
            json.getAsJsonObject("assignments").entrySet()
                    .forEach(entry -> catalog.assignments.put(entry.getKey(), entry.getValue().getAsString()));
            json.getAsJsonArray("hidden").forEach(element -> catalog.hidden.add(element.getAsString()));
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("Could not read the blueprint sections from {}", file, e);
        }
        return catalog;
    }

    private void save() {
        JsonObject json = new JsonObject();
        JsonArray sectionList = new JsonArray();
        for (Section section : sections) {
            JsonObject object = new JsonObject();
            object.addProperty("id", section.id());
            object.addProperty("name", section.name());
            object.addProperty("icon", section.icon());
            sectionList.add(object);
        }
        json.add("sections", sectionList);
        JsonObject assigned = new JsonObject();
        assignments.forEach(assigned::addProperty);
        json.add("assignments", assigned);
        JsonArray hiddenList = new JsonArray();
        hidden.forEach(hiddenList::add);
        json.add("hidden", hiddenList);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MCA.LOGGER.warn("Could not save the blueprint sections to {}", file, e);
        }
    }

    public List<Section> getSections() {
        return Collections.unmodifiableList(sections);
    }

    /** The section a blueprint is sorted into; empty when it belongs to none. */
    public String sectionOf(Identifier blueprint) {
        return assignments.getOrDefault(blueprint.toString(), DEFAULT_SECTION);
    }

    public boolean isHidden(Identifier blueprint) {
        return hidden.contains(blueprint.toString());
    }

    public boolean hasSection(String id) {
        return sections.stream().anyMatch(section -> section.id().equals(id));
    }

    /** Adds a section; returns its id, or null when there are too many already or the name is unusable. */
    public @Nullable String addSection(String name, String icon) {
        String slug = BlueprintLibrary.slug(name);
        if (slug.isEmpty() || sections.size() >= MAX_SECTIONS) {
            return null;
        }
        String id = slug;
        for (int i = 2; hasSection(id); i++) {
            id = slug + "_" + i;
        }
        sections.add(new Section(id, name.trim(), icon));
        save();
        return id;
    }

    /** Removes a section; what was in it belongs to no section afterwards. */
    public boolean removeSection(String id) {
        if (!sections.removeIf(section -> section.id().equals(id))) {
            return false;
        }
        // unsorted blueprints may still point at the default section; the screen shows them under "all" only
        assignments.replaceAll((blueprint, section) -> section.equals(id) ? "" : section);
        save();
        return true;
    }

    /** Sorts a blueprint into a section; an empty section takes it out of all. */
    public void assign(Identifier blueprint, String section) {
        if (!section.isEmpty() && !hasSection(section)) {
            return;
        }
        assignments.put(blueprint.toString(), section);
        save();
    }

    public void setHidden(Identifier blueprint, boolean hide) {
        boolean changed = hide ? hidden.add(blueprint.toString()) : hidden.remove(blueprint.toString());
        if (changed) {
            save();
        }
    }

    /** A deleted blueprint leaves no traces behind. */
    public void forget(Identifier blueprint) {
        boolean changed = assignments.remove(blueprint.toString()) != null;
        changed |= hidden.remove(blueprint.toString());
        if (changed) {
            save();
        }
    }
}
