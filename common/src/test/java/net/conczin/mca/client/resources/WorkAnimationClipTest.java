package net.conczin.mca.client.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.ai.WorkAnimation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkAnimationClipTest {
    private static final List<String> VARIANTS = List.of(
            "hammer_toolsmith",
            "hammer_mason",
            "stir_leatherworker",
            "write_cartographer",
            "weave_fletcher",
            "chop_lumberjack",
            "chop_miner",
            "crouch_forager",
            "chop_builder",
            "hammer_builder"
    );

    @Test
    void everyAnimationHasAClipThatParses() throws IOException {
        for (WorkAnimation animation : WorkAnimation.values()) {
            if (animation != WorkAnimation.NONE) {
                assertPlayable(clip(animation.clipName()));
            }
        }
        for (String variant : VARIANTS) {
            assertPlayable(clip(variant));
        }
    }

    @Test
    void harvestingWaitsExactlyOneCrouchCycle() throws IOException {
        // HarvestingTask and MixinHarvestFarmland act after 40 ticks
        assertEquals(2.0F, clip("crouch").length());
    }

    @Test
    void samplesEaseBetweenKeys() {
        WorkAnimationClip clip = WorkAnimationClip.parse(JsonParser.parseString("""
                {"animation_length": 1.0, "bones": {"right_arm": {"rotation": {
                    "0.0": [0, 0, 0],
                    "0.5": {"post": [-100, 0, 0], "lerp_mode": "ease_in"},
                    "1.0": [0, 0, 0]
                }}}}
                """).getAsJsonObject());
        float[] out = new float[3];

        clip.sampleRotation("right_arm", 0.25F, out);
        assertEquals(-12.5F, out[0], 0.001F);
        clip.sampleRotation("right_arm", 0.5F, out);
        assertEquals(-100F, out[0], 0.001F);
        clip.sampleRotation("right_arm", 0.75F, out);
        assertEquals(-50F, out[0], 0.001F);
        assertFalse(clip.sampleRotation("left_arm", 0.5F, out));
    }

    @Test
    void eventsFireAcrossTheLoopWrap() {
        WorkAnimationClip clip = WorkAnimationClip.parse(JsonParser.parseString("""
                {"animation_length": 1.0, "mca:events": {"0.0": "start", "0.5": "impact"}}
                """).getAsJsonObject());

        assertEquals(List.of("impact"), clip.eventsBetween(0.45F, 0.5F));
        assertEquals(List.of(), clip.eventsBetween(0.5F, 0.55F));
        assertEquals(List.of("start"), clip.eventsBetween(0.95F, 0.0F));
        assertEquals(0.25F, clip.phase(25), 0.001F);
    }

    private static void assertPlayable(WorkAnimationClip clip) {
        assertTrue(clip.length() > 0);
        float[] out = new float[3];
        boolean movesSomething = false;
        for (String bone : WorkAnimationClip.BONES) {
            for (float t = 0; t <= clip.length(); t += 0.05F) {
                if (clip.sampleRotation(bone, t, out) | clip.samplePosition(bone, t, out)) {
                    movesSomething = true;
                    for (float v : out) assertTrue(Float.isFinite(v));
                }
            }
        }
        assertTrue(movesSomething);
    }

    private static WorkAnimationClip clip(String name) throws IOException {
        String path = "assets/mca/animations/work/" + name + ".json";
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(WorkAnimationClipTest.class.getClassLoader().getResourceAsStream(path), path),
                StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return WorkAnimationClip.parse(json);
        }
    }
}
