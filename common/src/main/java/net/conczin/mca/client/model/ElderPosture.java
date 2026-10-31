package net.conczin.mca.client.model;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Elders stand slightly bent forward. The torso turns about the hips, so the neck, the head and
 * the shoulders move forward and a little down while the legs stay where they are. The same
 * bend reaches the Fresh Animations model through the EMF variable {@code mca_elder}.
 */
public final class ElderPosture {
    /** How far an elder bends forward at the waist, in radians (about 11 degrees). */
    public static final float STOOP = 0.19F;
    /** Height of the torso in model pixels, from the neck down to the hips. */
    private static final float TORSO = 12.0F;
    private static final Set<UUID> ELDERS = ConcurrentHashMap.newKeySet();

    private ElderPosture() {
    }

    /** Bends the upper body of a humanoid model forward by the stoop of elders. */
    public static void apply(HumanoidModel<?> model, Iterable<ModelPart> breasts) {
        bend(model, breasts, STOOP);
    }

    /**
     * Bends the upper body forward about the hips by an angle in radians, back when negative; the
     * breasts sit on the torso, so they turn along.
     */
    public static void bend(HumanoidModel<?> model, Iterable<ModelPart> breasts, float angle) {
        float sin = Mth.sin(angle);
        float cos = Mth.cos(angle);
        float hipY = model.body.y + TORSO;
        float hipZ = model.body.z;
        for (ModelPart part : new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm}) {
            turnAboutHips(part, hipY, hipZ, sin, cos);
        }
        for (ModelPart part : breasts) {
            turnAboutHips(part, hipY, hipZ, sin, cos);
            part.xRot += angle;
        }
        model.body.xRot += angle;
    }

    private static void turnAboutHips(ModelPart part, float hipY, float hipZ, float sin, float cos) {
        float dy = part.y - hipY;
        float dz = part.z - hipZ;
        part.y = hipY + dy * cos - dz * sin;
        part.z = hipZ + dy * sin + dz * cos;
    }

    /** Elders in view, refreshed every client tick, for when EMF hands over a render state instead of the entity. */
    public static void tick(Minecraft client) {
        ELDERS.clear();
        if (client.level == null) {
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof VillagerLike<?> villager && villager.getAgeState() == AgeState.ELDER) {
                ELDERS.add(entity.getUUID());
            }
        }
    }

    /** Registers {@code mca_elder} (1 for elders, else 0) with Entity Model Features, if it is installed. */
    public static void registerWithEntityModelFeatures() {
        if (!MCA.platformHelper.isModLoaded("entity_model_features")) {
            return;
        }
        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method current = api.getMethod("getCurrentEntity");
            Method uuid = Class.forName("traben.entity_texture_features.utils.ETFEntity").getMethod("etf$getUuid");
            Method register = api.getMethod("registerSingletonAnimationVariable", String.class, String.class, String.class, Supplier.class);
            Supplier<Float> elder = () -> {
                try {
                    // usually the entity itself (EMF mixes its interface into Entity), else look it up by id
                    Object entity = current.invoke(null);
                    if (entity instanceof VillagerLike<?> villager) {
                        return villager.getAgeState() == AgeState.ELDER ? 1.0F : 0.0F;
                    }
                    return entity != null && ELDERS.contains((UUID) uuid.invoke(entity)) ? 1.0F : 0.0F;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return 0.0F;
                }
            };
            register.invoke(null, MCA.MOD_ID, "mca_elder", "1 for an MCA villager in old age (it stands bent forward), else 0", elder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MCA.LOGGER.warn("Could not register the elder posture with Entity Model Features", e);
        }
    }
}
