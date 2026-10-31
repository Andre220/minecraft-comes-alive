package net.conczin.mca.client.model;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.traits.WalkStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The walk styles of traits ({@link WalkStyle}) on the humanoid model, on top of the vanilla walk, and
 * for Fresh Animations as the EMF variables {@code mca_walk_stride}, {@code _arms}, {@code _bounce},
 * {@code _sway}, {@code _head} and {@code _lean}, which MCA's villager.jem applies the same way.
 */
public final class WalkPosture {
    /** Walk styles of the villagers in view other than the normal one, refreshed every client tick. */
    private static final Map<UUID, WalkStyle> STYLES = new ConcurrentHashMap<>();
    /** The walk speed at which a style shows fully; it fades in from standing still. */
    private static final float FULL_AT = 0.33F;

    private WalkPosture() {
    }

    /**
     * Applies a style after the vanilla walk; pos and speed are the walk animation values the model
     * walked with, speedValue its divisor of the swing.
     */
    public static void apply(HumanoidModel<?> model, Iterable<ModelPart> breasts, WalkStyle style, float pos, float speed, float speedValue) {
        float moving = Mth.clamp(speed / FULL_AT, 0.0F, 1.0F);
        if (style == WalkStyle.NORMAL || moving <= 0.0F) {
            return;
        }
        float phase = pos * 0.6662F;
        float swing = Mth.cos(phase) * speed / speedValue;

        // the vanilla swing scaled: the right arm swings against the right leg
        float arms = (style.arms - 1.0F) * swing;
        model.rightArm.xRot -= arms;
        model.leftArm.xRot += arms;
        float legs = (style.stride - 1.0F) * swing * 1.4F;
        model.rightLeg.xRot += legs;
        model.leftLeg.xRot -= legs;

        // the body rises twice a cycle, once for each step
        float lift = style.bounce * Math.abs(Mth.sin(phase)) * moving;
        for (ModelPart part : new ModelPart[]{model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.y -= lift;
        }
        for (ModelPart part : breasts) {
            part.y -= lift;
        }

        // the shoulders turn with the steps, the arms go along
        float sway = style.sway * Mth.cos(phase) * moving;
        model.body.yRot += sway;
        model.rightArm.yRot += sway;
        model.leftArm.yRot += sway;
        model.rightArm.z += Mth.sin(sway) * 5.0F;
        model.leftArm.z -= Mth.sin(sway) * 5.0F;

        model.head.xRot += style.head * moving;
        if (style.lean != 0.0F) {
            ElderPosture.bend(model, breasts, style.lean * moving);
        }
    }

    /** Refreshes the styles of the villagers in view, for EMF, which may hand over a render state instead of the entity. */
    public static void tick(Minecraft client) {
        STYLES.clear();
        if (client.level == null) {
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof VillagerLike<?> villager) {
                WalkStyle style = villager.getTraits().getWalkStyle(villager.getAgeState());
                if (style != WalkStyle.NORMAL) {
                    STYLES.put(entity.getUUID(), style);
                }
            }
        }
    }

    /** Registers the {@code mca_walk_*} variables with Entity Model Features, if it is installed. */
    public static void registerWithEntityModelFeatures() {
        if (!MCA.platformHelper.isModLoaded("entity_model_features")) {
            return;
        }
        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method current = api.getMethod("getCurrentEntity");
            Method uuid = Class.forName("traben.entity_texture_features.utils.ETFEntity").getMethod("etf$getUuid");
            Method register = api.getMethod("registerSingletonAnimationVariable", String.class, String.class, String.class, Supplier.class);
            register.invoke(null, MCA.MOD_ID, "mca_walk_stride", "Multiplier of an MCA villager's leg swing, from its traits", value(current, uuid, s -> s.stride));
            register.invoke(null, MCA.MOD_ID, "mca_walk_arms", "Multiplier of an MCA villager's arm swing, from its traits", value(current, uuid, s -> s.arms));
            register.invoke(null, MCA.MOD_ID, "mca_walk_bounce", "How high an MCA villager rises with each step, pixels", value(current, uuid, s -> s.bounce));
            register.invoke(null, MCA.MOD_ID, "mca_walk_sway", "How far an MCA villager's shoulders turn with its steps, radians", value(current, uuid, s -> s.sway));
            register.invoke(null, MCA.MOD_ID, "mca_walk_head", "How far an MCA villager's head tilts down while walking, radians", value(current, uuid, s -> s.head));
            register.invoke(null, MCA.MOD_ID, "mca_walk_lean", "How far an MCA villager leans forward while walking, radians", value(current, uuid, s -> s.lean));
        } catch (ReflectiveOperationException | RuntimeException e) {
            MCA.LOGGER.warn("Could not register the walk styles with Entity Model Features", e);
        }
    }

    private static Supplier<Float> value(Method current, Method uuid, Function<WalkStyle, Float> field) {
        return () -> {
            try {
                Object entity = current.invoke(null);
                if (entity == null) {
                    return field.apply(WalkStyle.NORMAL);
                }
                UUID id = entity instanceof Entity e ? e.getUUID() : (UUID) uuid.invoke(entity);
                return field.apply(STYLES.getOrDefault(id, WalkStyle.NORMAL));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return field.apply(WalkStyle.NORMAL);
            }
        };
    }
}
