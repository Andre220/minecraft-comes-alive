package net.conczin.mca.expansion.minds;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.Map;
import java.util.Optional;

/**
 * A jobless villager looks for a free job site of what it is best at before taking just any:
 * it claims the site and lets the vanilla job behaviours walk it there and hire it.
 */
public class AptitudeJobTask extends Behavior<VillagerEntityMCA> {
    private static final int COOLDOWN = 600;
    private static final int RADIUS = 48;
    /** Skill worth looking for a matching job. */
    private static final float MIN_SKILL = 5.0F;

    private long nextTry;

    public AptitudeJobTask() {
        super(Map.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_ABSENT
        ), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!Skills.enabled() || level.getGameTime() < nextTry || !villager.getAgeState().isAdult()
            || !villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            return false;
        }
        nextTry = level.getGameTime() + COOLDOWN;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        for (VillagerProfession profession : Skills.known(villager)) {
            if (Skills.get(villager, profession) < MIN_SKILL) {
                break;
            }
            Optional<BlockPos> site = level.getPoiManager().take(
                    holder -> profession.heldJobSite().test(holder) && EconomyRegistry.isAcquirable(holder),
                    (holder, pos) -> true, villager.blockPosition(), RADIUS);
            if (site.isPresent()) {
                villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), site.get()));
                return;
            }
        }
    }
}
