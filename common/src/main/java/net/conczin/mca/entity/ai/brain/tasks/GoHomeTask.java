package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Optional;

/** Walks home (to the bed) while "go home" heads the action queue; the queue drops it on arrival. */
public class GoHomeTask extends Behavior<VillagerEntityMCA> {
    public GoHomeTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return villager.getActionQueue().isCurrent(ActionType.GO_HOME) && home(villager, level).isPresent() && !villager.getNeeds().isAtHome();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        home(villager, level).ifPresent(home -> villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(home.pos(), 0.5F, 2)));
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static Optional<GlobalPos> home(VillagerEntityMCA villager, ServerLevel level) {
        return villager.getResidency().getHome().filter(home -> home.dimension() == level.dimension());
    }
}
