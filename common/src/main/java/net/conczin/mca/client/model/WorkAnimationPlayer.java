package net.conczin.mca.client.model;

import net.minecraft.world.entity.Mob;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.client.resources.WorkAnimationLoader;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.client.resources.WorkAnimationClip;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Poses a model from a work animation clip, blended over the regular animation: MCA's own humanoid model, and the
 * human-like villager model (whose arms sit in the body).
 */
public final class WorkAnimationPlayer {
    private static final float TO_RADIANS = (float) Math.PI / 180.0F;

    /** A clip playing: where in it, how much it shows (0 to 1), and whether mirrored for a left-handed villager. */
    public record Pose(WorkAnimationClip clip, float phase, float blend, boolean mirror) {
    }

    private WorkAnimationPlayer() {
    }

    /** The work animation a villager is playing now, or null. */
    public static @Nullable Pose poseFor(VillagerLike<?> villager, Mob mob, float partialTicks) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        WorkAnimationClip clip = WorkAnimationLoader.get(brain.getShownWorkAnimation(), villager.getProfessionId());
        if (clip == null) {
            return null;
        }
        return new Pose(clip, clip.phase(brain.getWorkAnimationTicks(partialTicks)), brain.getWorkAnimationProgress(partialTicks),
                mob.getMainArm() == HumanoidArm.LEFT);
    }

    public static void apply(HumanoidModel<?> model, VillagerRenderState state) {
        WorkAnimationClip clip = state.workAnimationClip;
        if (clip == null || state.workAnimationBlend <= 0.0F) return;
        Pose pose = new Pose(clip, state.workAnimationPhase, state.workAnimationBlend, state.mainArm == HumanoidArm.LEFT);
        // MCA renders the bones one by one, so moving the whole body means moving each of them
        apply(pose, bone -> part(model, bone), List.of(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg));
    }

    /**
     * Poses the parts {@code parts} finds by bone name (null where the model has none) from the clip; the clip's
     * root movement moves each of {@code movedByRoot}.
     */
    public static void apply(Pose pose, Function<String, @Nullable ModelPart> parts, List<ModelPart> movedByRoot) {
        if (pose.blend() <= 0.0F) return;
        float blend = pose.blend();
        float[] rotation = new float[3];
        float[] position = new float[3];

        for (String bone : WorkAnimationClip.BONES) {
            if (bone.equals("root")) continue;
            boolean rotates = sampleRotation(pose, bone, rotation);
            boolean moves = samplePosition(pose, bone, position);
            ModelPart part = parts.apply(bone);
            if (!rotates && !moves || part == null) continue;

            PartPose rest = part.getInitialPose();
            if (rotates) {
                // the head keeps looking where it looks, everything else is posed from rest
                boolean additive = bone.equals("head");
                part.xRot = Mth.lerp(blend, part.xRot, (additive ? part.xRot : rest.xRot()) + rotation[0] * TO_RADIANS);
                part.yRot = Mth.lerp(blend, part.yRot, (additive ? part.yRot : rest.yRot()) + rotation[1] * TO_RADIANS);
                part.zRot = Mth.lerp(blend, part.zRot, (additive ? part.zRot : rest.zRot()) + rotation[2] * TO_RADIANS);
            }
            if (moves) {
                part.x = Mth.lerp(blend, part.x, rest.x() + position[0]);
                part.y = Mth.lerp(blend, part.y, rest.y() + position[1]);
                part.z = Mth.lerp(blend, part.z, rest.z() + position[2]);
            }
        }

        if (samplePosition(pose, "root", position)) {
            for (ModelPart part : movedByRoot) {
                part.x += position[0] * blend;
                part.y += position[1] * blend;
                part.z += position[2] * blend;
            }
        }
    }

    /** The clip's rotation of a bone now, in degrees, mirrored for left-handed villagers. */
    public static boolean sampleRotation(Pose pose, String bone, float[] out) {
        if (!pose.clip().sampleRotation(pose.mirror() ? mirrored(bone) : bone, pose.phase(), out)) {
            return false;
        }
        if (pose.mirror()) {
            out[1] = -out[1];
            out[2] = -out[2];
        }
        return true;
    }

    /** The clip's movement of a bone now, in model pixels, mirrored for left-handed villagers. */
    public static boolean samplePosition(Pose pose, String bone, float[] out) {
        if (!pose.clip().samplePosition(pose.mirror() ? mirrored(bone) : bone, pose.phase(), out)) {
            return false;
        }
        if (pose.mirror()) {
            out[0] = -out[0];
        }
        return true;
    }

    private static ModelPart part(HumanoidModel<?> model, String bone) {
        return switch (bone) {
            case "head" -> model.head;
            case "body" -> model.body;
            case "right_arm" -> model.rightArm;
            case "left_arm" -> model.leftArm;
            case "right_leg" -> model.rightLeg;
            case "left_leg" -> model.leftLeg;
            default -> throw new IllegalArgumentException(bone);
        };
    }

    private static String mirrored(String bone) {
        return switch (bone) {
            case "right_arm" -> "left_arm";
            case "left_arm" -> "right_arm";
            case "right_leg" -> "left_leg";
            case "left_leg" -> "right_leg";
            default -> bone;
        };
    }
}
