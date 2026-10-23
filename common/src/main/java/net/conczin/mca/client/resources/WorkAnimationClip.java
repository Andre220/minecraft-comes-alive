package net.conczin.mca.client.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One looping work animation, read from a subset of Blockbench's Bedrock animation JSON.
 * <p>
 * Values are in Java model space: degrees and model pixels with the same signs as {@code ModelPart}
 * (negative x rotation lifts an arm forward, positive y moves down). Bone offsets are relative to the
 * rest pose, except {@code head} rotation, which is added to where the villager is looking.
 * <pre>
 * {
 *   "animation_length": 1.0,
 *   "mca:tool": "minecraft:mace",
 *   "mca:impact": {"sound": "minecraft:block.anvil.use", "volume": 0.2, "pitch": 1.4, "particle": "block"},
 *   "mca:events": {"0.45": "impact"},
 *   "bones": {
 *     "right_arm": {
 *       "rotation": {"0.0": [-60, 0, 0], "0.45": {"post": [-10, 0, 0], "lerp_mode": "ease_in"}}
 *     }
 *   }
 * }
 * </pre>
 * {@code lerp_mode} belongs to the key it eases into: linear, catmullrom, step, ease_in, ease_out, ease_in_out, ease_out_back.
 */
public final class WorkAnimationClip {
    public static final List<String> BONES = List.of("root", "head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final Map<String, String> BONE_ALIASES = Map.of(
            "rightArm", "right_arm",
            "leftArm", "left_arm",
            "rightLeg", "right_leg",
            "leftLeg", "left_leg"
    );

    private final float length;
    private final Map<String, Channel> rotations;
    private final Map<String, Channel> positions;
    private final float[] eventTimes;
    private final String[] eventNames;
    private final @Nullable Identifier tool;
    private final Impact impact;
    private final @Nullable Carry carry;

    private WorkAnimationClip(float length, Map<String, Channel> rotations, Map<String, Channel> positions,
                              float[] eventTimes, String[] eventNames, @Nullable Identifier tool, Impact impact, @Nullable Carry carry) {
        this.length = length;
        this.rotations = rotations;
        this.positions = positions;
        this.eventTimes = eventTimes;
        this.eventNames = eventNames;
        this.tool = tool;
        this.impact = impact;
        this.carry = carry;
    }

    public float length() {
        return length;
    }

    /** Seconds into the loop for the given ticks since it started. */
    public float phase(float ticks) {
        float seconds = ticks / 20.0F;
        return length > 0 ? seconds % length : 0;
    }

    public @Nullable Identifier tool() {
        return tool;
    }

    public Impact impact() {
        return impact;
    }

    public @Nullable Carry carry() {
        return carry;
    }

    /** Writes the rotation offset (degrees) of a bone into {@code out}; false when the clip doesn't move it. */
    public boolean sampleRotation(String bone, float phase, float[] out) {
        Channel channel = rotations.get(bone);
        if (channel == null) return false;
        channel.sample(phase, out);
        return true;
    }

    /** Writes the position offset (pixels) of a bone into {@code out}; false when the clip doesn't move it. */
    public boolean samplePosition(String bone, float phase, float[] out) {
        Channel channel = positions.get(bone);
        if (channel == null) return false;
        channel.sample(phase, out);
        return true;
    }

    /** Names of the events between two phases, handling the wrap at the end of the loop. */
    public List<String> eventsBetween(float fromPhase, float toPhase) {
        List<String> fired = List.of();
        for (int i = 0; i < eventTimes.length; i++) {
            float t = eventTimes[i];
            boolean crossed = fromPhase <= toPhase
                    ? t > fromPhase && t <= toPhase
                    : t > fromPhase || t <= toPhase;
            if (crossed) {
                if (fired.isEmpty()) fired = new ArrayList<>(2);
                fired.add(eventNames[i]);
            }
        }
        return fired;
    }

    public static WorkAnimationClip parse(JsonObject json) {
        float length = GsonHelper.getAsFloat(json, "animation_length", 1.0F);

        Map<String, Channel> rotations = new HashMap<>();
        Map<String, Channel> positions = new HashMap<>();
        JsonObject bones = GsonHelper.getAsJsonObject(json, "bones", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            String bone = BONE_ALIASES.getOrDefault(entry.getKey(), entry.getKey());
            if (!BONES.contains(bone)) {
                throw new IllegalArgumentException("Unknown bone `" + entry.getKey() + "`, expected one of " + BONES);
            }
            JsonObject channels = entry.getValue().getAsJsonObject();
            if (channels.has("rotation")) rotations.put(bone, Channel.parse(channels.get("rotation")));
            if (channels.has("position")) positions.put(bone, Channel.parse(channels.get("position")));
        }

        TreeMap<Float, String> events = new TreeMap<>();
        GsonHelper.getAsJsonObject(json, "mca:events", new JsonObject())
                .entrySet()
                .forEach(e -> events.put(Float.parseFloat(e.getKey()), e.getValue().getAsString()));
        float[] eventTimes = new float[events.size()];
        String[] eventNames = new String[events.size()];
        int i = 0;
        for (Map.Entry<Float, String> e : events.entrySet()) {
            eventTimes[i] = e.getKey();
            eventNames[i] = e.getValue();
            i++;
        }

        Identifier tool = json.has("mca:tool") ? Identifier.tryParse(GsonHelper.getAsString(json, "mca:tool")) : null;
        Impact impact = Impact.parse(GsonHelper.getAsJsonObject(json, "mca:impact", new JsonObject()));
        Carry carry = json.has("mca:carry") ? Carry.parse(GsonHelper.getAsJsonObject(json, "mca:carry")) : null;

        return new WorkAnimationClip(length, rotations, positions, eventTimes, eventNames, tool, impact, carry);
    }

    /**
     * What an {@code impact} event does. {@code sound}/{@code particle} "block" use the block being worked on.
     */
    public record Impact(String sound, float volume, float pitch, String particle, int particleCount) {
        static Impact parse(JsonObject json) {
            return new Impact(
                    GsonHelper.getAsString(json, "sound", "block"),
                    GsonHelper.getAsFloat(json, "volume", 0.25F),
                    GsonHelper.getAsFloat(json, "pitch", 1.0F),
                    GsonHelper.getAsString(json, "particle", "block"),
                    GsonHelper.getAsInt(json, "particle_count", 4)
            );
        }
    }

    /**
     * An item held in front of the body with both arms, like a crate. Position in model pixels from the
     * neck (negative z is forward), rotation in degrees, scale 1 being half a block.
     */
    public record Carry(Identifier item, float[] position, float[] rotation, float scale) {
        static Carry parse(JsonObject json) {
            return new Carry(
                    Identifier.parse(GsonHelper.getAsString(json, "item")),
                    json.has("position") ? Channel.vector(GsonHelper.getAsJsonArray(json, "position")) : new float[]{0, 0, -8},
                    json.has("rotation") ? Channel.vector(GsonHelper.getAsJsonArray(json, "rotation")) : new float[]{0, 0, 0},
                    GsonHelper.getAsFloat(json, "scale", 1.0F)
            );
        }
    }

    enum Easing {
        LINEAR, CATMULLROM, STEP, EASE_IN, EASE_OUT, EASE_IN_OUT, EASE_OUT_BACK;

        float apply(float f) {
            return switch (this) {
                case EASE_IN -> f * f * f;
                case EASE_OUT -> 1 - (1 - f) * (1 - f) * (1 - f);
                case EASE_IN_OUT -> f < 0.5F ? 4 * f * f * f : 1 - (float) Math.pow(-2 * f + 2, 3) / 2;
                case EASE_OUT_BACK -> {
                    float c1 = 1.70158F;
                    float c3 = c1 + 1;
                    float g = f - 1;
                    yield 1 + c3 * g * g * g + c1 * g * g;
                }
                default -> f;
            };
        }

        static Easing parse(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    record Channel(float[] times, float[][] values, Easing[] easings) {
        static Channel parse(JsonElement element) {
            if (element.isJsonArray()) {
                // a constant offset
                return new Channel(new float[]{0}, new float[][]{vector(element.getAsJsonArray())}, new Easing[]{Easing.LINEAR});
            }

            TreeMap<Float, JsonElement> keys = new TreeMap<>();
            element.getAsJsonObject().entrySet().forEach(e -> keys.put(Float.parseFloat(e.getKey()), e.getValue()));

            float[] times = new float[keys.size()];
            float[][] values = new float[keys.size()][];
            Easing[] easings = new Easing[keys.size()];
            int i = 0;
            for (Map.Entry<Float, JsonElement> key : keys.entrySet()) {
                times[i] = key.getKey();
                JsonElement value = key.getValue();
                if (value.isJsonArray()) {
                    values[i] = vector(value.getAsJsonArray());
                    easings[i] = Easing.LINEAR;
                } else {
                    JsonObject object = value.getAsJsonObject();
                    JsonArray vector = object.has("post") ? GsonHelper.getAsJsonArray(object, "post") : GsonHelper.getAsJsonArray(object, "pre");
                    values[i] = vector(vector);
                    easings[i] = Easing.parse(GsonHelper.getAsString(object, "lerp_mode", "linear"));
                }
                i++;
            }
            return new Channel(times, values, easings);
        }

        private static float[] vector(JsonArray array) {
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
        }

        void sample(float t, float[] out) {
            int last = times.length - 1;
            if (t <= times[0] || last == 0) {
                System.arraycopy(values[0], 0, out, 0, 3);
                return;
            }
            if (t >= times[last]) {
                System.arraycopy(values[last], 0, out, 0, 3);
                return;
            }

            int i = 0;
            while (times[i + 1] < t) i++;
            float f = (t - times[i]) / (times[i + 1] - times[i]);
            Easing easing = easings[i + 1];
            float[] from = values[i];
            float[] to = values[i + 1];

            if (easing == Easing.STEP) {
                System.arraycopy(from, 0, out, 0, 3);
            } else if (easing == Easing.CATMULLROM) {
                float[] before = values[Math.max(i - 1, 0)];
                float[] after = values[Math.min(i + 2, last)];
                for (int axis = 0; axis < 3; axis++) {
                    out[axis] = Mth.catmullrom(f, before[axis], from[axis], to[axis], after[axis]);
                }
            } else {
                float eased = easing.apply(f);
                for (int axis = 0; axis < 3; axis++) {
                    out[axis] = from[axis] + (to[axis] - from[axis]) * eased;
                }
            }
        }
    }
}
