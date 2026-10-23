package net.conczin.mca.expansion.minds;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.queue.QueuedAction;
import net.conczin.mca.expansion.minds.routine.IdleRoutineTask;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A short line above a villager saying what it is doing, shown to players close by: sleeping, reading, chopping
 * trees, going home... Worked out on the server about once a second and synced as a translation key.
 */
public final class ActionLabels {
    /** Translation key of what the villager is doing, or empty. */
    public static final CDataParameter<String> LABEL = CParameter.create("ActionLabel", "");
    private static final int INTERVAL = 20;
    /** Work and chore behaviours with a label of their own, by class name. */
    private static final Set<String> LABELLED_TASKS = Set.of(
            "lumberjacktask", "huntertask", "foragertask", "fishertask", "digsitetask", "haulertask", "workshopproductiontask",
            "stashtask", "workordertask", "choppingtask", "harvestingtask", "fishingtask", "huntingtask", "interacttask",
            "workatjobsitetask", "schooltask", "needsfetchfoodtask", "needsdrinktask", "gohometask");

    private ActionLabels() {
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(LABEL);
    }

    public static boolean enabled() {
        return Config.getInstance().villagerActionLabels;
    }

    public static void update(VillagerEntityMCA villager) {
        boolean needed = enabled() || Config.getInstance().villagerMouthAnimation;
        if (!needed || (villager.tickCount + villager.getId()) % INTERVAL != 0) {
            return;
        }
        String label = compute(villager);
        if (!label.equals(villager.getTrackedValue(LABEL))) {
            villager.setTrackedValue(LABEL, label);
        }
    }

    public static String compute(VillagerEntityMCA villager) {
        if (villager.isSleeping()) {
            return "action.mca.sleeping";
        }
        if (villager.getVillagerBrain().isPanicking()) {
            return "action.mca.panic";
        }
        String task = null;
        for (BehaviorControl<?> behavior : villager.getBrain().getRunningBehaviors()) {
            if (behavior instanceof IdleRoutineTask routine && routine.current() != null) {
                return "action.mca.routine." + routine.current().kind().name().toLowerCase(Locale.ROOT);
            }
            String name = behavior.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (task == null && LABELLED_TASKS.contains(name)) {
                task = "action.mca.task." + name;
            }
        }
        if (task != null) {
            return task;
        }
        QueuedAction queued = villager.getActionQueue().current();
        if (queued != null) {
            return queued.type().translationKey();
        }
        Optional<Activity> activity = villager.getBrain().getActiveNonCoreActivity();
        return activity.map(a -> "action.mca.activity." + a.getName().toLowerCase(Locale.ROOT)).orElse("");
    }
}
