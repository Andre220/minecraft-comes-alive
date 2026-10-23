package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * A lonely villager with free time walks up to another villager nearby and chats for a moment,
 * which cheers both of them up (the lonely one more).
 */
public class NeedsSocializeTask extends Behavior<VillagerEntityMCA> {
    private static final float LONELY = 20.0F;
    private static final double SEARCH_RANGE = 16.0;
    private static final int CHAT_TICKS = 80;
    private static final int MAX_DURATION = 500;
    private static final int COOLDOWN = 1200;

    private @Nullable VillagerEntityMCA partner;
    private int chatTicks;
    private long nextAttempt;

    public NeedsSocializeTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Needs needs = villager.getNeeds();
        boolean wanted = ActionQueue.isEnabled() ? villager.getActionQueue().isCurrent(ActionType.SOCIALIZE) : needs.get(Needs.Need.SOCIAL) < LONELY;
        if (level.getGameTime() < nextAttempt || !needs.hasNeeds() || !wanted) {
            return false;
        }
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.MEET || villager.isSleeping() || villager.getVillagerBrain().isPanicking()) {
            return false;
        }
        partner = level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(SEARCH_RANGE),
                        other -> other != villager && other.isAlive() && !other.isSleeping() && !other.isBaby())
                .stream()
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(villager)))
                .orElse(null);
        if (partner == null) {
            nextAttempt = level.getGameTime() + COOLDOWN / 2;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        chatTicks = 0;
        walkToPartner(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return partner != null && partner.isAlive() && !partner.isSleeping() && chatTicks < CHAT_TICKS
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (partner == null) {
            return;
        }
        if (villager.distanceToSqr(partner) > 3.0 * 3.0) {
            walkToPartner(villager);
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(partner, true));
        partner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(villager, true));
        if (++chatTicks == CHAT_TICKS) {
            villager.getNeeds().add(Needs.Need.SOCIAL, 30.0F);
            partner.getNeeds().add(Needs.Need.SOCIAL, 10.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getEyeY() + 0.4, villager.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, partner.getX(), partner.getEyeY() + 0.4, partner.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextAttempt = time + COOLDOWN;
        partner = null;
    }

    private void walkToPartner(VillagerEntityMCA villager) {
        if (partner != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(partner, false), 0.5F, 2));
        }
    }
}
