package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Provisions;
import net.conczin.mca.expansion.minds.Skills;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Shared loop of the gathering jobs: find something to gather around the work spot, walk there,
 * work it, and bring the village's share of the haul to the workshop chest now and then.
 */
public abstract class GathererTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 2400;
    private static final int SEARCH_COOLDOWN = 200;
    private static final int FAILED_COOLDOWN = 400;
    private static final int TOOL_FETCH_COOLDOWN = 2400;
    private static final double CONTAINER_REACH_SQR = 2.5 * 2.5;
    /** The village's share is delivered once this many items are waiting. */
    private static final int DEPOSIT_THRESHOLD = 16;

    protected enum Progress {WORKING, DONE, FAILED}

    private enum Phase {WORK, DEPOSIT, FETCH_TOOL}


    /** The village's share of what was gathered, still in the villager's inventory. */
    private final Map<Item, Integer> haul = new LinkedHashMap<>();

    private @Nullable Phase phase;
    private boolean plannedDeposit;
    private @Nullable BlockPos plannedToolChest;
    private long nextToolFetch;
    private boolean finished;
    private boolean equipped;
    private long nextSearch;
    private @Nullable BlockPos container;

    private final ApproachTracker tracker = new ApproachTracker();

    protected GathererTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    // ---------------------------------------------------------------- for the jobs

    /** Where the work happens: the job site by default. */
    protected Optional<BlockPos> workCenter(ServerLevel level, VillagerEntityMCA villager) {
        return ProductionSink.jobSite(level, villager);
    }

    /** The tool this job works with, fetched from the village storage when missing; null for none. */
    protected @Nullable Predicate<ItemStack> tool() {
        return null;
    }

    protected int workRadius() {
        return EconomyFeature.config().economyWorkRadius;
    }

    /** Looks for something to do around {@code center} and remembers it; false when there is nothing. */
    protected abstract boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center);

    /** Works on what {@link #findWork} picked, one tick at a time. */
    protected abstract Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village);

    /** Forgets the current target. */
    protected abstract void reset();

    /** Where the village's share goes, nearest first: the workshop chest by default. */
    protected List<BlockPos> depositContainers(ServerLevel level, VillagerEntityMCA villager, Village village) {
        return workCenter(level, villager)
                .map(center -> ProductionSink.workshopContainers(level, village, center))
                .orElse(List.of());
    }

    /** Puts gathered items into the villager's inventory, setting the village's share aside. */
    protected void collect(ServerLevel level, VillagerEntityMCA villager, Village village, List<ItemStack> drops) {
        boolean delivers = !depositContainers(level, villager, village).isEmpty();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int count = drop.getCount();
            ItemStack rest = villager.getInventory().addItem(drop.copy());
            int kept = count - rest.getCount();
            if (delivers && kept > 0) {
                int share = ProductionSink.villageShare(village, kept, villager.getRandom());
                if (share > 0) {
                    haul.merge(drop.getItem(), share, Integer::sum);
                }
            }
            if (!rest.isEmpty()) {
                villager.spawnAtLocation(level, rest);
            }
        }
    }

    /** Real work done: a skilled hand gets one more now and then, and every bit of work teaches. */
    protected void harvested(ServerLevel level, VillagerEntityMCA villager, Village village, List<ItemStack> drops) {
        List<ItemStack> produced = new java.util.ArrayList<>(drops);
        Skills.addExtra(villager, produced);
        collect(level, villager, village, produced);
        Skills.gain(villager, Skills.GAIN_GATHER);
    }

    /** Holds a matching tool from the inventory; returns whether the villager has one in hand. */
    protected boolean equip(VillagerEntityMCA villager, Predicate<ItemStack> tool) {
        ItemStack held = villager.getItemInHand(villager.getDominantHand());
        if (!held.isEmpty() && tool.test(held)) {
            return true;
        }
        int slot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), tool);
        if (slot < 0) {
            return false;
        }
        villager.setItemInHand(villager.getDominantHand(), villager.getInventory().getItem(slot));
        equipped = true;
        return true;
    }

    protected ItemStack tool(VillagerEntityMCA villager) {
        return villager.getItemInHand(villager.getDominantHand());
    }

    /** Walks towards a spot; gives up when the villager stops getting closer. */
    protected ApproachTracker.Approach approach(VillagerEntityMCA villager, BlockPos pos, double reachSqr) {
        return tracker.approach(villager, pos, reachSqr);
    }

    protected void resetApproach() {
        tracker.reset();
    }

    /** Whether a spot lies in or right next to one of the village's buildings, which gatherers leave alone. */
    protected static boolean isNearBuilding(Village village, BlockPos pos, int margin) {
        return village.getBuildings().values().stream()
                .anyMatch(building -> building.containsPositionWithMargin(pos, margin, margin));
    }

    /** Spots in a box around {@code center}, nearest to {@code from} first. */
    protected static List<BlockPos> scan(ServerLevel level, BlockPos center, int radius, int below, int above,
                                         BlockPos from, Predicate<BlockPos> filter) {
        List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -below, -radius), center.offset(radius, above, radius))) {
            if (level.isLoaded(pos) && filter.test(pos)) {
                found.add(pos.immutable());
            }
        }
        found.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(from)));
        return found;
    }

    protected static boolean isInventoryFull(SimpleContainer inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** What the task is up to, for debug views and tests. */
    public String debugState() {
        return "phase=" + phase + " haul=" + haul + " nextSearch=" + nextSearch + " container=" + container;
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long time = level.getGameTime();
        if (!EconomyFeature.enabled() || time < nextSearch
            || villager.getVillagerBrain().isPanicking() || villager.isSleeping()) {
            return false;
        }
        Optional<Village> village = ProductionSink.villageOf(villager);
        Optional<BlockPos> center = workCenter(level, villager);
        if (village.isEmpty() || center.isEmpty()) {
            nextSearch = time + SEARCH_COOLDOWN;
            return false;
        }

        if (shouldDeposit(villager) && !depositContainers(level, villager, village.get()).isEmpty()) {
            plannedDeposit = true;
            return true;
        }
        Predicate<ItemStack> tool = tool();
        if (tool != null && time >= nextToolFetch && !tool.test(tool(villager))
            && !villager.getInventory().hasAnyMatching(tool) && !isInventoryFull(villager.getInventory())) {
            nextToolFetch = time + TOOL_FETCH_COOLDOWN;
            plannedToolChest = Provisions.storageWith(level, villager, tool);
            if (plannedToolChest != null) {
                return true;
            }
        }
        if (!isInventoryFull(villager.getInventory()) && findWork(level, villager, village.get(), center.get())) {
            return true;
        }
        if (!haul.isEmpty() && !depositContainers(level, villager, village.get()).isEmpty()) {
            plannedDeposit = true;
            return true;
        }
        nextSearch = time + SEARCH_COOLDOWN;
        return false;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        finished = false;
        resetApproach();
        if (plannedToolChest != null) {
            phase = Phase.FETCH_TOOL;
            container = plannedToolChest;
            plannedToolChest = null;
        } else if (plannedDeposit) {
            plannedDeposit = false;
            phase = Phase.DEPOSIT;
            container = null;
        } else {
            phase = Phase.WORK;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return !finished
               && !villager.getVillagerBrain().isPanicking()
               && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getVillagerBrain().stopWorkAnimation();
        if (villager.isUsingItem()) {
            villager.stopUsingItem();
        }
        if (equipped) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
            equipped = false;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        reset();
        phase = null;
        container = null;
        plannedDeposit = false;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        Optional<Village> village = ProductionSink.villageOf(villager);
        if (village.isEmpty() || phase == null) {
            finished = true;
            return;
        }
        if (phase == Phase.WORK) {
            Progress progress = tickWork(level, villager, village.get());
            if (progress == Progress.FAILED) {
                reset();
                finished = true;
                nextSearch = time + FAILED_COOLDOWN;
            } else if (progress == Progress.DONE) {
                reset();
                resetApproach();
                if (shouldDeposit(villager) && !depositContainers(level, villager, village.get()).isEmpty()) {
                    phase = Phase.DEPOSIT;
                    container = null;
                } else if (isInventoryFull(villager.getInventory())
                           || workCenter(level, villager).filter(center -> findWork(level, villager, village.get(), center)).isEmpty()) {
                    finished = true;
                }
            }
        } else if (phase == Phase.FETCH_TOOL) {
            tickFetchTool(level, villager);
        } else {
            tickDeposit(level, villager, village.get());
        }
    }

    /** A worker without a tool takes one from the village storage. */
    private void tickFetchTool(ServerLevel level, VillagerEntityMCA villager) {
        Predicate<ItemStack> tool = tool();
        if (container == null || tool == null) {
            finished = true;
            return;
        }
        switch (approach(villager, container, CONTAINER_REACH_SQR)) {
            case STUCK -> finished = true;
            case ARRIVED -> {
                VillageStorage.getContainer(level, container).ifPresent(chest -> {
                    for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                        if (tool.test(chest.getItem(slot))) {
                            villager.getInventory().addItem(chest.removeItem(slot, 1));
                            break;
                        }
                    }
                });
                villager.swing(villager.getDominantHand());
                finished = true;
            }
            case WALKING -> {
            }
        }
    }

    private boolean shouldDeposit(VillagerEntityMCA villager) {
        int waiting = haul.values().stream().mapToInt(Integer::intValue).sum();
        return waiting >= DEPOSIT_THRESHOLD || waiting > 0 && isInventoryFull(villager.getInventory());
    }

    private void tickDeposit(ServerLevel level, VillagerEntityMCA villager, Village village) {
        haul.entrySet().removeIf(entry -> entry.getValue() <= 0 || !villager.getInventory().hasAnyMatching(stack -> stack.is(entry.getKey())));
        if (haul.isEmpty()) {
            finished = true;
            return;
        }
        if (container == null) {
            container = depositContainers(level, villager, village).stream()
                    .filter(pos -> VillageStorage.getContainer(level, pos)
                            .map(c -> haul.keySet().stream().anyMatch(item -> VillageStorage.hasSpaceFor(c, new ItemStack(item))))
                            .orElse(false))
                    .findFirst()
                    .orElse(null);
            if (container == null) {
                // nowhere to put it: the villager keeps everything for now
                finished = true;
                nextSearch = level.getGameTime() + FAILED_COOLDOWN;
                return;
            }
        }
        switch (approach(villager, container, CONTAINER_REACH_SQR)) {
            case STUCK -> {
                finished = true;
                nextSearch = level.getGameTime() + FAILED_COOLDOWN;
            }
            case ARRIVED -> {
                Optional<Container> target = VillageStorage.getContainer(level, container);
                if (target.isPresent()) {
                    Iterator<Map.Entry<Item, Integer>> it = haul.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Item, Integer> entry = it.next();
                        int moved = VillageStorage.withdraw(villager.getInventory(), entry.getKey(), entry.getValue(), target.get());
                        int left = entry.getValue() - moved;
                        if (left <= 0) {
                            it.remove();
                        } else {
                            entry.setValue(left);
                        }
                    }
                    villager.swing(villager.getDominantHand());
                }
                // try the next container with whatever did not fit
                container = null;
            }
            case WALKING -> {
            }
        }
    }
}
