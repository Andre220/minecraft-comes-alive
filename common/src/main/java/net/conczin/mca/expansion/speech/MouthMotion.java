package net.conczin.mca.expansion.speech;

import net.conczin.mca.expansion.minds.ActionLabels;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.conczin.mca.entity.VillagerLike;
import java.util.Set;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * How open a speaking villager's mouth is (client): wide on vowels, a little on consonants, closed between
 * words, eased so it moves like talking. Fed to Fresh Animations' villager model as the animation variable
 * {@code mca_talk} through Entity Model Features' API, looked up by reflection so EMF stays optional. Also gives
 * the model {@code mca_sleeping}, so a sleeping villager closes its eyes.
 */
public final class MouthMotion {
    private static final String VOWELS = "aeiouáàâãéêíóôõúAEIOUÁÀÂÃÉÊÍÓÔÕÚyY";
    /** How quickly the mouth follows the letters, per ms. */
    private static final float EASE = 0.02F;

    private record State(float openness, long time) {
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** Villagers asleep right now, refreshed every client tick, for when EMF hands over a render state. */
    private static final Set<UUID> SLEEPING = ConcurrentHashMap.newKeySet();
    /** Villagers chatting with each other right now (from their action label), refreshed every client tick. */
    private static final Set<UUID> CHATTING = ConcurrentHashMap.newKeySet();
    private static final String CHAT_LABEL = "action.mca.routine.chat";

    /**
     * Two villagers chatting with each other: no text, but their mouths move. Each talks for a few seconds, then
     * listens, at its own pace, so a pair seems to take turns.
     */
    static float babble(UUID speaker, long now) {
        int seed = speaker.hashCode();
        double t = now / 1000.0 + (seed & 1023) / 97.0;
        boolean talking = ((long) Math.floor(t / 2.7) + (seed >>> 10 & 1)) % 2 == 0;
        if (!talking) {
            return 0.0F;
        }
        double syllables = Math.sin(t * 11.0) * Math.sin(t * 3.7 + 1.3);
        return syllables > 0.15 ? (float) Math.min(1.0, syllables * 1.4) : 0.0F;
    }

    public static void tick(Minecraft client) {
        SLEEPING.clear();
        CHATTING.clear();
        if (client.level == null || !Config.getInstance().villagerMouthAnimation) {
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof VillagerLike<?> && entity instanceof LivingEntity living && living.isSleeping()) {
                SLEEPING.add(entity.getUUID());
            }
            if (entity instanceof VillagerLike<?> villager && CHAT_LABEL.equals(villager.getTrackedValue(ActionLabels.LABEL))) {
                CHATTING.add(entity.getUUID());
            }
        }
    }

    private MouthMotion() {
    }

    public static float openness(UUID speaker) {
        char letter = Config.getInstance().villagerMouthAnimation ? SpeechBubbles.currentLetter(speaker) : 0;
        float target = letter == 0 || Character.isWhitespace(letter) ? 0.0F
                : VOWELS.indexOf(letter) >= 0 ? 1.0F
                : Character.isLetterOrDigit(letter) ? 0.35F : 0.05F;
        long now = Util.getMillis();
        if (letter == 0 && CHATTING.contains(speaker)) {
            target = babble(speaker, now);
        }
        State previous = STATES.get(speaker);
        if (previous == null) {
            if (target == 0.0F) {
                return 0.0F;
            }
            previous = new State(0.0F, now);
        }
        float step = Math.min(1.0F, (now - previous.time()) * EASE);
        float value = previous.openness() + (target - previous.openness()) * step;
        if (target == 0.0F && value < 0.01F) {
            STATES.remove(speaker);
            return 0.0F;
        }
        STATES.put(speaker, new State(value, now));
        return value;
    }

    /** Registers {@code mca_talk} with Entity Model Features, if it is installed. */
    public static void registerWithEntityModelFeatures() {
        if (!MCA.platformHelper.isModLoaded("entity_model_features")) {
            return;
        }
        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method current = api.getMethod("getCurrentEntity");
            Method uuid = Class.forName("traben.entity_texture_features.utils.ETFEntity").getMethod("etf$getUuid");
            Method register = api.getMethod("registerSingletonAnimationVariable", String.class, String.class, String.class, Supplier.class);
            Supplier<Float> talk = () -> {
                try {
                    Object entity = current.invoke(null);
                    return entity == null ? 0.0F : openness((UUID) uuid.invoke(entity));
                } catch (ReflectiveOperationException | ClassCastException e) {
                    return 0.0F;
                }
            };
            register.invoke(null, MCA.MOD_ID, "mca_talk", "How open an MCA villager's mouth is while it speaks, 0 to 1", talk);
            // the entity being animated is the entity itself (EMF mixes its interface into Entity)
            Supplier<Float> sleeping = () -> {
                try {
                    Object entity = current.invoke(null);
                    if (entity instanceof LivingEntity living) {
                        return living.isSleeping() ? 1.0F : 0.0F;
                    }
                    return entity != null && SLEEPING.contains((UUID) uuid.invoke(entity)) ? 1.0F : 0.0F;
                } catch (ReflectiveOperationException | ClassCastException e) {
                    return 0.0F;
                }
            };
            register.invoke(null, MCA.MOD_ID, "mca_sleeping", "1 while an MCA villager sleeps (its eyes close), else 0", sleeping);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MCA.LOGGER.warn("Could not register the talking mouth with Entity Model Features", e);
        }
    }
}
