package net.conczin.mca.expansion.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The path a village grows along, stage by stage, from {@code data/<namespace>/village_stages/*.json}:
 * <pre>{@code
 * {
 *   "order": 2,
 *   "requirements": [
 *     {"kind": "building", "building": "storage"},
 *     {"kind": "tier", "tier": "common_house", "count": 3},
 *     {"kind": "population", "count": 10},
 *     {"kind": "workshops", "count": 2, "level": 1},
 *     {"kind": "any", "of": [{"kind": "building", "building": "library"}, {"kind": "building", "building": "school"}]}
 *   ]
 * }
 * }</pre>
 * The file name is the stage's id; its name and hint are {@code villageStage.<id>} and
 * {@code villageStage.<id>.hint}. Stages only guide: buildings work whatever the stage.
 */
public class VillageStages extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("village_stages");

    public enum Kind {BUILDING, TIER, POPULATION, WORKSHOPS, ANY}

    public record Requirement(Kind kind, String target, int count, int level, List<Requirement> of) {
    }

    public record Stage(String id, int order, List<Requirement> requirements) {
    }

    private static final List<Stage> STAGES = new ArrayList<>();

    public VillageStages() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("village_stages"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        STAGES.clear();
        data.forEach((id, json) -> {
            try {
                JsonObject object = GsonHelper.convertToJsonObject(json, "root");
                List<Requirement> requirements = new ArrayList<>();
                GsonHelper.getAsJsonArray(object, "requirements").forEach(element -> requirements.add(requirement(element)));
                STAGES.add(new Stage(id.getPath(), GsonHelper.getAsInt(object, "order"), List.copyOf(requirements)));
            } catch (JsonParseException | IllegalStateException | IllegalArgumentException e) {
                MCA.LOGGER.error("Could not load village stage {}", id, e);
            }
        });
        STAGES.sort(Comparator.comparingInt(Stage::order));
    }

    private static Requirement requirement(JsonElement element) {
        JsonObject object = GsonHelper.convertToJsonObject(element, "requirement");
        Kind kind = Kind.valueOf(GsonHelper.getAsString(object, "kind").toUpperCase(java.util.Locale.ROOT));
        String target = switch (kind) {
            case BUILDING -> GsonHelper.getAsString(object, "building");
            case TIER -> GsonHelper.getAsString(object, "tier");
            default -> "";
        };
        List<Requirement> of = new ArrayList<>();
        if (kind == Kind.ANY) {
            GsonHelper.getAsJsonArray(object, "of").forEach(child -> of.add(requirement(child)));
        }
        return new Requirement(kind, target, GsonHelper.getAsInt(object, "count", 1), GsonHelper.getAsInt(object, "level", 1), List.copyOf(of));
    }

    public static List<Stage> stages() {
        return List.copyOf(STAGES);
    }
}
