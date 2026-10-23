package net.conczin.mca.client.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads work animation clips from {@code assets/<namespace>/animations/work/}. A clip named
 * {@code <clip>_<profession>} (e.g. {@code hammer_mason}) replaces {@code <clip>} for that profession.
 */
public class WorkAnimationLoader extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("work_animations");

    private static Map<String, WorkAnimationClip> clips = Map.of();

    public WorkAnimationLoader() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("animations/work"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, WorkAnimationClip> loaded = new HashMap<>();
        data.forEach((id, json) -> {
            try {
                loaded.put(id.getPath(), WorkAnimationClip.parse(json.getAsJsonObject()));
            } catch (Exception e) {
                MCA.LOGGER.error("Failed to load work animation `{}`", id, e);
            }
        });
        clips = loaded;
    }

    public static @Nullable WorkAnimationClip get(WorkAnimation animation, @Nullable Identifier profession) {
        if (animation == WorkAnimation.NONE) return null;
        if (profession != null) {
            WorkAnimationClip variant = clips.get(animation.clipName() + "_" + profession.getPath());
            if (variant != null) return variant;
        }
        return clips.get(animation.clipName());
    }
}
