package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Skills;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.conczin.mca.expansion.economy.ProductionRecipes;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.expansion.economy.Workshops;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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

/**
 * Haulers keep the goods moving: they bring what workshops made to the village storage and
 * stock the workshops with the inputs they consume, combining both on one trip where they can.
 * <p>
 * In self supply mode (a village without haulers) a craftsman fetches a single batch of inputs
 * for its own workshop every two days, so production never stalls completely.
 */
public class HaulerTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 3600;
    private static final int PLAN_COOLDOWN = 200;
    private static final int SELF_SUPPLY_COOLDOWN = 48000;
    private static final double REACH_SQR = 2.5 * 2.5;
    /** Workshops are emptied once this many products wait in their chest. */
    private static final int PICKUP_THRESHOLD = 8;
    /** How many batches of inputs a hauler keeps in each workshop. */
    private static final int STOCK_BATCHES = 5;

    private enum Step {PICKUP, DROP, FETCH, DELIVER}

    private final boolean selfSupply;
    private final ApproachTracker tracker = new ApproachTracker();
    /** What the villager carries for the job, as opposed to its own things. */
    private final Map<Item, Integer> carried = new LinkedHashMap<>();

    private @Nullable Step step;
    private Workshops.@Nullable Workshop workshop;
    private @Nullable Item fetchItem;
    private int fetchCount;
    private @Nullable BlockPos container;
    private boolean finished;
    private long nextPlan;

    public HaulerTask(boolean selfSupply) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
        this.selfSupply = selfSupply;
    }

    public String debugState() {
        return "step=" + step + " carried=" + carried + " fetch=" + fetchCount + " " + fetchItem
               + " workshop=" + (workshop == null ? null : workshop.station()) + " nextPlan=" + nextPlan;
    }

    // ---------------------------------------------------------------- planning

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long time = level.getGameTime();
        if (!EconomyFeature.enabled() || time < nextPlan || villager.getVillagerBrain().isPanicking() || villager.isSleeping()) {
            return false;
        }
        Optional<Village> village = ProductionSink.villageOf(villager);
        if (village.isEmpty() || !VillageStorage.exists(village.get())
            || selfSupply && village.get().getResidents(level).stream().anyMatch(v -> v.getProfession() == EconomyRegistry.HAULER)) {
            nextPlan = time + PLAN_COOLDOWN;
            return false;
        }
        if (plan(level, villager, village.get())) {
            return true;
        }
        nextPlan = time + PLAN_COOLDOWN;
        return false;
    }

    private List<Workshops.Workshop> workshops(ServerLevel level, VillagerEntityMCA villager, Village village) {
        List<Workshops.Workshop> all = Workshops.of(level, village);
        if (!selfSupply) {
            return all;
        }
        Optional<BlockPos> own = ProductionSink.jobSite(level, villager);
        return all.stream().filter(w -> own.isPresent() && w.station().equals(own.get())).toList();
    }

    /** Picks the next step; false when there is nothing to do. */
    private boolean plan(ServerLevel level, VillagerEntityMCA villager, Village village) {
        container = null;
        tracker.reset();
        carried.entrySet().removeIf(entry -> VillageStorage.count(villager.getInventory(), entry.getKey()) <= 0);
        if (!carried.isEmpty()) {
            step = Step.DROP;
            return true;
        }
        List<Workshops.Workshop> workshops = workshops(level, villager, village);
        for (Workshops.Workshop candidate : workshops) {
            for (Item input : candidate.inputs()) {
                int target = batchSize(candidate, input) * (selfSupply ? 1 : STOCK_BATCHES);
                int have = count(level, candidate.chests(), input);
                if (have < target && VillageStorage.count(level, village, input) > 0) {
                    step = Step.FETCH;
                    workshop = candidate;
                    fetchItem = input;
                    fetchCount = Math.min(target - have, input.getDefaultMaxStackSize());
                    return true;
                }
            }
        }
        if (!selfSupply) {
            for (Workshops.Workshop candidate : workshops) {
                if (products(level, candidate) >= PICKUP_THRESHOLD) {
                    step = Step.PICKUP;
                    workshop = candidate;
                    return true;
                }
            }
        }
        return false;
    }

    private static int batchSize(Workshops.Workshop workshop, Item input) {
        return ProductionRecipes.forWorker(workshop.profession(), workshop.building()).stream()
                .flatMap(recipe -> recipe.inputs().stream())
                .filter(stack -> stack.item() == input)
                .mapToInt(ProductionRecipes.Stack::count)
                .max().orElse(1);
    }

    private static int count(ServerLevel level, List<BlockPos> chests, Item item) {
        int total = 0;
        for (BlockPos pos : chests) {
            total += VillageStorage.getContainer(level, pos).map(c -> VillageStorage.count(c, item)).orElse(0);
        }
        return total;
    }

    /** Whether an item in a workshop chest is something it made (not one of its inputs). */
    private static boolean isProduct(Workshops.Workshop workshop, ItemStack stack) {
        return !stack.isEmpty() && !workshop.inputs().contains(stack.getItem());
    }

    private static int products(ServerLevel level, Workshops.Workshop workshop) {
        int total = 0;
        for (BlockPos pos : workshop.chests()) {
            Optional<Container> chest = VillageStorage.getContainer(level, pos);
            if (chest.isPresent()) {
                for (int slot = 0; slot < chest.get().getContainerSize(); slot++) {
                    ItemStack stack = chest.get().getItem(slot);
                    if (isProduct(workshop, stack)) {
                        total += stack.getCount();
                    }
                }
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        finished = false;
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
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        step = null;
        container = null;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        Optional<Village> village = ProductionSink.villageOf(villager);
        if (village.isEmpty() || step == null) {
            finished = true;
            return;
        }
        switch (step) {
            case PICKUP -> tickPickup(level, villager, village.get());
            case DROP -> tickDrop(level, villager, village.get());
            case FETCH -> tickFetch(level, villager, village.get());
            case DELIVER -> tickDeliver(level, villager, village.get());
        }
        // arms out with a crate while carrying goods for the job, see carry.json
        if (!carried.isEmpty()) {
            villager.getVillagerBrain().setWorkAnimation(WorkAnimation.CARRY, 10);
        }
    }

    /** Arrived somewhere and done there: go on with the next step, or call it a day. */
    private void next(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (!plan(level, villager, village)) {
            finished = true;
        }
    }

    private void giveUp(ServerLevel level) {
        finished = true;
        nextPlan = level.getGameTime() + PLAN_COOLDOWN * 3;
    }

    /** Walks to {@link #container}; true once there. */
    private boolean reach(ServerLevel level, VillagerEntityMCA villager) {
        if (container == null) {
            giveUp(level);
            return false;
        }
        ApproachTracker.Approach approach = tracker.approach(villager, container, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            giveUp(level);
            return false;
        }
        return approach == ApproachTracker.Approach.ARRIVED;
    }

    private void tickPickup(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (workshop == null) {
            next(level, villager, village);
            return;
        }
        if (container == null) {
            container = workshop.chests().stream()
                    .filter(pos -> VillageStorage.getContainer(level, pos).map(c -> products(c, workshop) > 0).orElse(false))
                    .findFirst().orElse(null);
            if (container == null) {
                next(level, villager, village);
                return;
            }
        }
        if (reach(level, villager)) {
            VillageStorage.getContainer(level, container).ifPresent(chest -> takeProducts(villager, chest, workshop));
            villager.swing(villager.getDominantHand());
            container = null;
            if (GathererTask.isInventoryFull(villager.getInventory()) || products(level, workshop) == 0) {
                step = Step.DROP;
            }
        }
    }

    private static int products(Container chest, Workshops.Workshop workshop) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (isProduct(workshop, chest.getItem(slot))) {
                total += chest.getItem(slot).getCount();
            }
        }
        return total;
    }

    private void takeProducts(VillagerEntityMCA villager, Container chest, Workshops.Workshop from) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!isProduct(from, stack)) {
                continue;
            }
            Item item = stack.getItem();
            int count = stack.getCount();
            ItemStack rest = villager.getInventory().addItem(stack.copy());
            int taken = count - rest.getCount();
            if (taken > 0) {
                stack.shrink(taken);
                carried.merge(item, taken, Integer::sum);
            }
        }
        chest.setChanged();
    }

    private void tickDrop(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (carried.isEmpty()) {
            next(level, villager, village);
            return;
        }
        if (container == null) {
            Item first = carried.keySet().iterator().next();
            container = VillageStorage.findSpaceFor(level, village, new ItemStack(first), villager.blockPosition()).orElse(null);
            if (container == null) {
                // the storage is full: keep carrying it for now
                giveUp(level);
                return;
            }
        }
        if (reach(level, villager)) {
            Optional<Container> storage = VillageStorage.getContainer(level, container);
            if (storage.isPresent()) {
                unload(villager, storage.get());
                Skills.gain(villager, Skills.GAIN_DELIVERY);
            }
            villager.swing(villager.getDominantHand());
            container = null;
        }
    }

    /** Moves the carried goods into a container, as many as fit. */
    private void unload(VillagerEntityMCA villager, Container into) {
        Iterator<Map.Entry<Item, Integer>> it = carried.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Item, Integer> entry = it.next();
            int moved = VillageStorage.withdraw(villager.getInventory(), entry.getKey(), entry.getValue(), into);
            int left = entry.getValue() - moved;
            if (left <= 0 || VillageStorage.count(villager.getInventory(), entry.getKey()) <= 0) {
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    private void tickFetch(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (fetchItem == null || workshop == null) {
            next(level, villager, village);
            return;
        }
        if (container == null) {
            container = VillageStorage.findContaining(level, village, fetchItem, villager.blockPosition()).orElse(null);
            if (container == null) {
                next(level, villager, village);
                return;
            }
        }
        if (reach(level, villager)) {
            Optional<Container> storage = VillageStorage.getContainer(level, container);
            int moved = storage.map(c -> VillageStorage.withdraw(c, fetchItem, fetchCount, villager.getInventory())).orElse(0);
            villager.swing(villager.getDominantHand());
            container = null;
            if (moved > 0) {
                carried.merge(fetchItem, moved, Integer::sum);
                step = Step.DELIVER;
            } else {
                next(level, villager, village);
            }
        }
    }

    private void tickDeliver(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (workshop == null || fetchItem == null) {
            next(level, villager, village);
            return;
        }
        if (container == null) {
            container = workshop.chests().stream()
                    .filter(pos -> VillageStorage.getContainer(level, pos).map(c -> VillageStorage.hasSpaceFor(c, new ItemStack(fetchItem))).orElse(false))
                    .findFirst().orElse(null);
            if (container == null) {
                step = Step.DROP; // no room: take it back
                return;
            }
        }
        if (reach(level, villager)) {
            Optional<Container> chest = VillageStorage.getContainer(level, container);
            if (chest.isPresent()) {
                int amount = carried.getOrDefault(fetchItem, 0);
                int moved = VillageStorage.withdraw(villager.getInventory(), fetchItem, amount, chest.get());
                if (amount - moved <= 0) {
                    carried.remove(fetchItem);
                } else {
                    carried.put(fetchItem, amount - moved);
                }
                Skills.gain(villager, Skills.GAIN_DELIVERY);
                // on the way back anyway: take along what the workshop made
                if (!selfSupply) {
                    takeProducts(villager, chest.get(), workshop);
                }
            }
            villager.swing(villager.getDominantHand());
            container = null;
            fetchItem = null;
            if (selfSupply) {
                finished = true;
                nextPlan = level.getGameTime() + SELF_SUPPLY_COOLDOWN;
            } else {
                next(level, villager, village);
            }
        }
    }
}
