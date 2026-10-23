package net.conczin.mca.expansion.economy.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.ConditionalTask;
import net.conczin.mca.entity.ai.brain.tasks.UpdateScheduleActivityTask;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.function.Predicate;

/**
 * Brain packages of the economy's jobs, plugged into MCA's villager brain.
 */
public final class EconomyTasks {
    private EconomyTasks() {
    }

    /** Whether a profession works by gathering around its station instead of at it. */
    public static boolean hasGathererPackage(VillagerProfession profession) {
        return EconomyRegistry.isGatherer(profession)
               || EconomyFeature.enabled() && BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession).is(VillagerProfession.FISHERMAN);
    }

    /** The work shift of lumberjacks, hunters, foragers and fishermen: gather, or hang around the station. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> gathererWorkPackage(VillagerProfession profession) {
        GathererTask gather = profession == EconomyRegistry.LUMBERJACK ? new LumberjackTask()
                : profession == EconomyRegistry.HUNTER ? new HunterTask()
                : profession == EconomyRegistry.FORAGER ? new ForagerTask()
                : new FisherTask();
        StashTask stash = new StashTask();
        return ImmutableList.of(
                Pair.of(4, stash),
                Pair.of(5, new ConditionalTask<>(gather, (VillagerEntityMCA v) -> stash.getStatus() != Behavior.Status.RUNNING)),
                Pair.of(10, new ConditionalTask<>(new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, 0.4F, 6), 2),
                                Pair.of(new DoNothing(30, 60), 3)
                        )), (VillagerEntityMCA v) -> gather.getStatus() != Behavior.Status.RUNNING && stash.getStatus() != Behavior.Status.RUNNING)),
                Pair.of(99, UpdateScheduleActivityTask.create())
        );
    }

    /**
     * Added to the work shift of craftsmen at their station: production, taking their share home
     * and, while the village has no hauler, fetching their own inputs now and then.
     */
    public static List<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> workshopTasks(VillagerProfession profession) {
        return List.of(
                Pair.of(4, new StashTask()),
                Pair.of(6, new WorkshopProductionTask()),
                Pair.of(7, new HaulerTask(true))
        );
    }

    /** The work shift of haulers. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>>> haulerWorkPackage() {
        HaulerTask haul = new HaulerTask(false);
        return ImmutableList.of(
                Pair.of(5, haul),
                Pair.of(10, new ConditionalTask<>(new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(VillageBoundRandomStroll.create(0.4F), 2),
                                Pair.of(new DoNothing(30, 60), 3)
                        )), (VillagerEntityMCA v) -> haul.getStatus() != Behavior.Status.RUNNING)),
                Pair.of(99, UpdateScheduleActivityTask.create())
        );
    }

    /**
     * Miners work a dig site while {@code idle} (no dig orders); a no-op for builders and with
     * the feature off.
     */
    public static BehaviorControl<VillagerEntityMCA> digSite(VillagerProfession profession, Predicate<VillagerEntityMCA> idle) {
        return new ConditionalTask<>(new DigSiteTask(),
                v -> profession == ProfessionsMCA.MINER && EconomyFeature.enabled() && idle.test(v));
    }
}
