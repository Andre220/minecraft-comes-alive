package net.conczin.mca.client.model;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * Work animations for Fresh Animations' villager model, which animates its parts itself: the clip playing is handed
 * to Entity Model Features as animation variables, which MCA's villager.jem blends over Fresh Animations' own
 * animation. {@code mca_work} is how much the clip shows (0 to 1); {@code mca_work_<bone>_rx/ry/rz} are a bone's
 * rotations in radians, {@code mca_work_root_tx/ty/tz} the whole body's movement in pixels. Looked up by reflection,
 * so EMF stays optional.
 */
public final class WorkAnimationVariables {
    private static final List<String> BONES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    private static final float TO_RADIANS = (float) Math.PI / 180.0F;
    /** Index of each value in a sampled pose: blend, then rx, ry, rz per bone, then the root's tx, ty, tz. */
    private static final int ROOT = 1 + BONES.size() * 3;
    /** How long a sampled pose is reused, in ms: one frame, and every variable of it. */
    private static final long FRAME = 4;

    private static int cachedEntity = Integer.MIN_VALUE;
    private static long cachedAt;
    private static float[] cached = new float[ROOT + 3];

    private WorkAnimationVariables() {
    }

    /** The values for a villager now; all zero when no clip plays. */
    static float[] sample(@Nullable Object entity) {
        if (!(entity instanceof VillagerEntityMCA villager)) {
            return new float[ROOT + 3];
        }
        long now = Util.getMillis();
        if (villager.getId() == cachedEntity && now - cachedAt < FRAME) {
            return cached;
        }
        float[] values = new float[ROOT + 3];
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        WorkAnimationPlayer.Pose pose = WorkAnimationPlayer.poseFor(villager, villager, partialTicks);
        if (pose != null && pose.blend() > 0.0F) {
            values[0] = pose.blend();
            float[] sample = new float[3];
            for (int i = 0; i < BONES.size(); i++) {
                if (WorkAnimationPlayer.sampleRotation(pose, BONES.get(i), sample)) {
                    values[1 + i * 3] = sample[0] * TO_RADIANS;
                    values[2 + i * 3] = sample[1] * TO_RADIANS;
                    values[3 + i * 3] = sample[2] * TO_RADIANS;
                }
            }
            if (WorkAnimationPlayer.samplePosition(pose, "root", sample)) {
                values[ROOT] = sample[0];
                values[ROOT + 1] = sample[1];
                values[ROOT + 2] = sample[2];
            }
        }
        cachedEntity = villager.getId();
        cachedAt = now;
        cached = values;
        return values;
    }

    /** Registers the variables with Entity Model Features, if it is installed. */
    public static void registerWithEntityModelFeatures() {
        if (!MCA.platformHelper.isModLoaded("entity_model_features")) {
            return;
        }
        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method current = api.getMethod("getCurrentEntity");
            Method register = api.getMethod("registerSingletonAnimationVariable", String.class, String.class, String.class, Supplier.class);
            register.invoke(null, MCA.MOD_ID, "mca_work", "How much an MCA villager's work animation shows, 0 to 1", value(current, 0));
            String[] axes = {"rx", "ry", "rz"};
            for (int i = 0; i < BONES.size(); i++) {
                for (int axis = 0; axis < 3; axis++) {
                    register.invoke(null, MCA.MOD_ID, "mca_work_" + BONES.get(i) + "_" + axes[axis],
                            "The work animation's " + BONES.get(i) + " rotation, radians", value(current, 1 + i * 3 + axis));
                }
            }
            String[] moves = {"tx", "ty", "tz"};
            for (int axis = 0; axis < 3; axis++) {
                register.invoke(null, MCA.MOD_ID, "mca_work_root_" + moves[axis], "The work animation's body movement, pixels",
                        value(current, ROOT + axis));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MCA.LOGGER.warn("Could not register the work animations with Entity Model Features", e);
        }
    }

    private static Supplier<Float> value(Method current, int index) {
        return () -> {
            try {
                return sample(current.invoke(null))[index];
            } catch (ReflectiveOperationException e) {
                return 0.0F;
            }
        };
    }
}
