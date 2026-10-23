package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.List;

/**
 * Vanilla's UpdateActivityFromSchedule, but villagers with a personal schedule follow it instead
 * of their timeline, and critical needs override both (see Needs#overrideActivity). A scheduled
 * activity that cannot run (work without a job site, meeting without a meeting point) falls back
 * to idling.
 */
public class UpdateScheduleActivityTask {
    private static final int UPDATE_INTERVAL = 20;

    public static BehaviorControl<LivingEntity> create() {
        return create(false);
    }

    /**
     * For the HIDE activity, which vanilla only uses for raids: leaves hiding once the needs no
     * longer ask for it, but never interrupts hiding from a raid (heard bell).
     */
    public static BehaviorControl<LivingEntity> createForHiding() {
        return create(true);
    }

    private static BehaviorControl<LivingEntity> create(boolean hiding) {
        return BehaviorBuilder.create(context -> context.point((Trigger<LivingEntity>) (level, body, time) -> {
            if (!(body instanceof VillagerEntityMCA villager)) {
                if (!hiding) {
                    body.getBrain().updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), body.position());
                }
                return true;
            }
            Brain<VillagerEntityMCA> brain = villager.getMCABrain();
            if (hiding && brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)) {
                return true;
            }

            boolean custom = !villager.getVillagerBrain().getCustomSchedule().isEmpty();
            if (custom || villager.getNeeds().isOverridingSchedule() || hiding || ActionQueue.isEnabled()) {
                if (time % UPDATE_INTERVAL == 0) {
                    Activity activity = SchedulesMCA.scheduledActivity(villager);
                    if (!brain.isActive(activity)) {
                        brain.setActiveActivityToFirstValid(List.of(activity, Activity.IDLE));
                    }
                }
            } else {
                brain.updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), body.position());
            }
            return true;
        }));
    }
}
