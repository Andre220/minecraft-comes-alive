package net.conczin.mca.client.model;

import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.client.resources.WorkAnimationClip;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Poses a humanoid model from the work animation clip in its render state, blended over the regular animation.
 */
public final class WorkAnimationPlayer {
    private static final float TO_RADIANS = (float) Math.PI / 180.0F;

    private WorkAnimationPlayer() {
    }

    public static void apply(HumanoidModel<?> model, VillagerRenderState state) {
        WorkAnimationClip clip = state.workAnimationClip;
        float blend = state.workAnimationBlend;
        if (clip == null || blend <= 0.0F) return;

        // clips are authored right-handed, left-handed villagers play them mirrored
        boolean mirror = state.mainArm == HumanoidArm.LEFT;
        float[] rotation = new float[3];
        float[] position = new float[3];

        for (String bone : WorkAnimationClip.BONES) {
            if (bone.equals("root")) continue;
            String source = mirror ? mirrored(bone) : bone;
            boolean rotates = clip.sampleRotation(source, state.workAnimationPhase, rotation);
            boolean moves = clip.samplePosition(source, state.workAnimationPhase, position);
            if (!rotates && !moves) continue;

            if (mirror) {
                rotation[1] = -rotation[1];
                rotation[2] = -rotation[2];
                position[0] = -position[0];
            }

            ModelPart part = part(model, bone);
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

        // MCA renders the bones one by one, so moving the whole body means moving each of them
        if (clip.samplePosition("root", state.workAnimationPhase, position)) {
            float dx = (mirror ? -position[0] : position[0]) * blend;
            float dy = position[1] * blend;
            float dz = position[2] * blend;
            for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
                part.x += dx;
                part.y += dy;
                part.z += dz;
            }
        }
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
