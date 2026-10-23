package net.conczin.mca.expansion.minds;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;

/**
 * Children and teens go to the village school in the daytime and get a bit brighter there:
 * their intelligence gene grows, faster with a librarian teaching in the school.
 */
public class SchoolTask extends Behavior<VillagerEntityMCA> {
    public static final String SCHOOL = "school";

    private static final int LESSON_TICKS = 1200;
    private static final int COOLDOWN = 2400;
    private static final int STEP_TICKS = 100;
    /** Intelligence gained per step of a lesson, doubled with a teacher. */
    private static final float GAIN_PER_STEP = 0.002F;
    private static final double INSIDE_SQR = 3.0 * 3.0;

    private @Nullable BlockPos school;
    private int studied;
    private long nextTry;

    public SchoolTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), LESSON_TICKS * 3);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        AgeState age = villager.getAgeState();
        if (!Skills.enabled() || level.getGameTime() < nextTry || age == AgeState.ADULT || age == AgeState.BABY
            || !level.isBrightOutside()) {
            return false;
        }
        nextTry = level.getGameTime() + COOLDOWN;
        school = schoolOf(villager).map(Building::getCenter).orElse(null);
        return school != null;
    }

    public static Optional<Building> schoolOf(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager))
                .flatMap(village -> village.getBuildingsOfType(SCHOOL)
                        .min(Comparator.comparingDouble(building -> building.getCenter().distSqr(villager.blockPosition()))));
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        studied = 0;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return school != null && studied < LESSON_TICKS && !villager.getVillagerBrain().isPanicking();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (school == null) {
            return;
        }
        if (villager.blockPosition().distSqr(school) > INSIDE_SQR) {
            villager.moveTowardsPersistent(school, 0.5F, 1);
            return;
        }
        villager.lookAt(school);
        if (++studied % STEP_TICKS == 0) {
            learn(villager, hasTeacher(level, school) ? 2 : 1);
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        school = null;
    }

    /** One step of a lesson: the intelligence gene grows a little, up to its maximum. */
    public static void learn(VillagerEntityMCA villager, int factor) {
        Genetics genetics = villager.getGenetics();
        if (genetics != null) {
            genetics.setGene(Genetics.INTELLIGENCE, Math.min(1.0F, genetics.getGene(Genetics.INTELLIGENCE) + GAIN_PER_STEP * factor));
        }
    }

    private static boolean hasTeacher(ServerLevel level, BlockPos school) {
        return !level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(school).inflate(6),
                v -> v.isAlive() && v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)).isEmpty();
    }
}
