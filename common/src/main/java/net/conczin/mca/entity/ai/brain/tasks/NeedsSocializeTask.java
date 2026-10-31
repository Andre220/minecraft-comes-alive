package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.relationship.Conversations;
import net.conczin.mca.entity.ai.relationship.Opinions;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
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
 * A lonely villager with free time walks up to another villager nearby, preferring friends and
 * avoiding enemies, and chats for a moment. A good chat cheers both of them up (the lonely one
 * more) and makes them like each other; an argument does the opposite (see Conversations).
 */
public class NeedsSocializeTask extends Behavior<VillagerEntityMCA> {
    private static final float LONELY = 20.0F;
    private static final double SEARCH_RANGE = 16.0;
    private static final int CHAT_TICKS = 80;
    private static final int MAX_DURATION = 500;
    private static final int COOLDOWN = 1200;
    /** Blocks closer a villager counts per point of opinion when choosing whom to talk to. */
    private static final double FRIEND_PULL = 0.1;

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
        // the nearest villager, but friends count as closer and enemies are avoided
        Opinions opinions = villager.getOpinions();
        partner = level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(SEARCH_RANGE),
                        other -> other != villager && other.isAlive() && !other.isSleeping() && !other.isBaby()
                                 && opinions.get(other.getUUID()) > Opinions.ENEMY)
                .stream()
                .min(Comparator.comparingDouble(other -> Math.sqrt(other.distanceToSqr(villager)) - opinions.get(other.getUUID()) * FRIEND_PULL))
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
            // a good chat cheers both up, an argument only a little
            boolean wentWell = Conversations.talk(level, villager, partner);
            villager.getNeeds().add(Needs.Need.SOCIAL, wentWell ? 30.0F : 10.0F);
            partner.getNeeds().add(Needs.Need.SOCIAL, wentWell ? 10.0F : 0.0F);
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
