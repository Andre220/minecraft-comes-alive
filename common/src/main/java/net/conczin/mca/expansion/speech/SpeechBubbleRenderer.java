package net.conczin.mca.expansion.speech;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.expansion.minds.ActionLabels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws above a villager what it says (a speech bubble, its lines stacked like name tags facing the camera) or,
 * when it says nothing, a short line about what it is doing for players close by.
 */
public final class SpeechBubbleRenderer {
    private static final double MAX_DISTANCE_SQR = 24.0 * 24.0;
    private static final double LABEL_DISTANCE_SQR = 8.0 * 8.0;
    private static final double LINE_HEIGHT = 9.0 * 1.15 * 0.025;
    /** Room left between the head (or name tag) and the bubble. */
    private static final double GAP = 0.15;
    /** Closer than this (in blocks) the bubble shrinks. */
    private static final double CLOSE = 5.0;
    private static final double MIN_SCALE = 0.3;

    private SpeechBubbleRenderer() {
    }

    /** The action line for a villager, if labels are on and the player is close enough. */
    public static @Nullable Component actionLabel(Entity villager, @Nullable String key) {
        var player = Minecraft.getInstance().player;
        if (!ActionLabels.enabled() || key == null || key.isEmpty() || player == null || player.distanceToSqr(villager) > LABEL_DISTANCE_SQR) {
            return null;
        }
        return Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    public static void submit(EntityRenderState state, List<Component> lines, @Nullable Component actionLabel, PoseStack pose,
                              SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.isInvisible || state.distanceToCameraSq > MAX_DISTANCE_SQR) {
            return;
        }
        List<Component> shown = !lines.isEmpty() ? lines : actionLabel != null ? List.of(actionLabel) : List.of();
        if (shown.isEmpty()) {
            return;
        }
        Vec3 base = state.nameTagAttachment != null && state.nameTag != null
                ? state.nameTagAttachment.add(0.0, LINE_HEIGHT + GAP, 0.0)
                : new Vec3(0.0, state.boundingBoxHeight + 0.5 + GAP, 0.0);
        // smaller up close, so the whole bubble stays on screen
        float scale = (float) Math.clamp(Math.sqrt(state.distanceToCameraSq) / CLOSE, MIN_SCALE, 1.0);
        pose.pushPose();
        pose.translate(base.x, base.y, base.z);
        pose.scale(scale, scale, scale);
        pose.translate(-base.x, -base.y, -base.z);
        // the newest line at the bottom, right above the head
        for (int i = 0; i < shown.size(); i++) {
            double up = (shown.size() - 1 - i) * LINE_HEIGHT;
            collector.submitNameTag(pose, base.add(0.0, up, 0.0), 0, shown.get(i), true, state.lightCoords, camera);
        }
        pose.popPose();
    }
}
