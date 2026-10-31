package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.traits.MentalBreaks;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.schedule.Activity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface SchedulesMCA {
    Map<Identifier, EnvironmentAttribute<Activity>> SCHEDULES = new HashMap<>();

    EnvironmentAttribute<Activity> DEFAULT = schedule("gameplay/default_villager_activity", Activity.IDLE);
    EnvironmentAttribute<Activity> NIGHT_OWL_DEFAULT = schedule("gameplay/night_owl_villager_activity", Activity.REST);
    EnvironmentAttribute<Activity> GUARD = schedule("gameplay/guard_villager_activity", Activity.WORK);
    EnvironmentAttribute<Activity> GUARD_NIGHT = schedule("gameplay/night_guard_villager_activity", Activity.REST);
    EnvironmentAttribute<Activity> GUESTS = schedule("gameplay/guest_villager_activity", Activity.IDLE);

    static EnvironmentAttribute<Activity> schedule(String name, Activity defaultActivity) {
        EnvironmentAttribute<Activity> schedule = EnvironmentAttribute.builder(AttributeTypes.ACTIVITY)
                .defaultValue(defaultActivity)
                .build();
        SCHEDULES.put(MCA.locate(name), schedule);
        return schedule;
    }

    static void bootstrap() {
    }

    static void registerSchedules(MCA.RegisterHelper<EnvironmentAttribute<?>> helper) {
        SCHEDULES.forEach((id, schedule) -> helper.register(id, schedule));
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(
            LivingEntity entity,
            boolean allowNightOwl,
            EnvironmentAttribute<Activity> normalSchedule,
            EnvironmentAttribute<Activity> nightSchedule
    ) {
        // the brain is first made in the entity's constructor, before its traits exist: night owls get theirs on a later refresh
        if (entity instanceof VillagerLike<?> villager && villager.getTraits() != null && villager.getTraits().hasTrait(PersonalityTraits.NIGHT_OWL)) {
            return nightSchedule; // the night owl trait (from RimWorld)
        }
        return (allowNightOwl && entity.getRandom().nextFloat() < Config.getInstance().nightOwlChance) ? nightSchedule : normalSchedule;
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(
            LivingEntity entity,
            EnvironmentAttribute<Activity> normalSchedule,
            EnvironmentAttribute<Activity> nightSchedule
    ) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl, normalSchedule, nightSchedule);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity, boolean allowNightOwl) {
        return getTypeSchedule(entity, allowNightOwl, SchedulesMCA.DEFAULT, SchedulesMCA.NIGHT_OWL_DEFAULT);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl);
    }

    /** Hours in a personal schedule: one per 1000 ticks of the overworld clock, starting at 06:00. */
    int SLOTS = 24;
    int SLOT_TICKS = 1000;
    /** Activities a personal schedule can hold, by their letter. */
    List<Activity> SCHEDULE_ACTIVITIES = List.of(Activity.IDLE, Activity.WORK, Activity.MEET, Activity.REST, Activity.PLAY);
    String SCHEDULE_LETTERS = "IWMRP";

    /**
     * The timelines of data/mca/timeline/villager_schedules.json (and vanilla's baby schedule) as
     * personal schedules, sampled in the middle of each hour. Keep in sync with the JSON.
     */
    enum Preset {
        DEFAULT("default", "IIWWWWWWWMMIRRRRRRRRRRRR"),
        NIGHT_OWL("night_owl", "RRRRRRRRRRRRIIIWWWMIIIII"),
        GUARD("guard", "WWWWWWWWWMMWWWIRRRRRRRRR"),
        NIGHT_GUARD("night_guard", "RRRRRRRRIMMMMMWWWWWWWWWW"),
        GUEST("guest", "IIIIIIIIIIIIRRRRRRRRRRRR"),
        CHILD("child", "IIIPPPIIIIPPRRRRRRRRRRRR");

        private final String id;
        private final String schedule;

        Preset(String id, String schedule) {
            this.id = id;
            this.schedule = schedule;
        }

        public String id() {
            return id;
        }

        public String schedule() {
            return schedule;
        }
    }

    static boolean isValidSchedule(String schedule) {
        if (schedule == null || schedule.length() != SLOTS) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (SCHEDULE_LETTERS.indexOf(schedule.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    static Activity activityOf(char letter) {
        int index = SCHEDULE_LETTERS.indexOf(letter);
        return index < 0 ? Activity.IDLE : SCHEDULE_ACTIVITIES.get(index);
    }

    static char letterOf(Activity activity) {
        int index = SCHEDULE_ACTIVITIES.indexOf(activity);
        return index < 0 ? 'I' : SCHEDULE_LETTERS.charAt(index);
    }

    /** The current hour of a personal schedule, 0 being 06:00. */
    static int currentSlot(Level level) {
        return (int) (Math.floorMod(level.getOverworldClockTime(), (long) SLOTS * SLOT_TICKS) / SLOT_TICKS);
    }

    /**
     * What the villager should be doing right now: its personal schedule or its timeline, unless a
     * critical need overrides it (see {@link Needs#overrideActivity}).
     */
    static Activity scheduledActivity(VillagerEntityMCA villager) {
        // A sulking villager does not work (a mental break).
        if (MentalBreaks.isSulking(villager)) {
            return Activity.IDLE;
        }
        // With the action queue on, its head decides (it already weighs schedule and needs).
        Activity queued = villager.getActionQueue().currentActivity();
        if (queued != null) {
            return queued;
        }
        Activity scheduled = plannedActivity(villager);
        Activity need = villager.getNeeds().overrideActivity(scheduled);
        return need != null ? need : scheduled;
    }

    /**
     * What the villager's schedule asks for at an hour of the day (0 = 06:00): its personal
     * schedule, or the preset closest to its timeline (timelines cannot be read ahead).
     */
    static Activity plannedAt(VillagerEntityMCA villager, int slot) {
        String custom = villager.getVillagerBrain().getCustomSchedule();
        if (!custom.isEmpty()) {
            return activityOf(custom.charAt(Math.floorMod(slot, SLOTS)));
        }
        return halfShift(villager, Math.floorMod(slot, SLOTS), activityOf(presetFor(villager).schedule().charAt(Math.floorMod(slot, SLOTS))));
    }

    /** What the villager's schedule asks for right now: its personal schedule, or its timeline. */
    static Activity plannedActivity(VillagerEntityMCA villager) {
        String custom = villager.getVillagerBrain().getCustomSchedule();
        if (!custom.isEmpty()) {
            return activityOf(custom.charAt(currentSlot(villager.level())));
        }
        EnvironmentAttribute<Activity> schedule = villager.getBrain().schedule;
        Activity activity = schedule == null ? Activity.IDLE : villager.level().environmentAttributes().getValue(schedule, villager.position());
        return halfShift(villager, currentSlot(villager.level()), activity);
    }

    /** Hour of the day (0 = 06:00) from which elders stop working: they only work the morning. */
    int ELDER_WORK_UNTIL = 6;

    /** Elders work half a shift; a personal schedule set by the player is left as it is. */
    private static Activity halfShift(VillagerEntityMCA villager, int slot, Activity activity) {
        return activity == Activity.WORK && slot >= ELDER_WORK_UNTIL && villager.getAgeState() == AgeState.ELDER ? Activity.IDLE : activity;
    }

    /** The preset closest to the timeline a villager of this kind follows; night owls cannot be told apart. */
    static Preset presetFor(VillagerEntityMCA villager) {
        if (!villager.getAgeState().isAdult()) {
            return Preset.CHILD;
        }
        if (villager.isGuard()) {
            return Preset.GUARD;
        }
        return villager.requiresHome() ? Preset.DEFAULT : Preset.GUEST;
    }
}
