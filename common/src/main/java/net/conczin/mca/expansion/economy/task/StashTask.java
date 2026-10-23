package net.conczin.mca.expansion.economy.task;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.HomeChests;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.property.PropertyRules;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * When a worker's pockets fill up with its share of the work, it takes the goods home and puts
 * them in a chest there, keeping its tools, some food and what it plants.
 */
public class StashTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 2400;
    private static final int COOLDOWN = 600;
    /** Goes home once this few inventory slots are left. */
    private static final int FREE_SLOTS = 3;
    private static final double REACH_SQR = 2.5 * 2.5;
    private static final int KEEP_PLANTABLE = 16;
    private static final int KEEP_FOOD = 8;
    private static final Set<Item> PLANTABLE = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO,
            Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BONE_MEAL);

    private final ApproachTracker tracker = new ApproachTracker();
    private @Nullable BlockPos chest;
    private boolean finished;
    private long nextTry;

    public StashTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long time = level.getGameTime();
        if (!EconomyFeature.enabled() || time < nextTry || freeSlots(villager.getInventory()) > FREE_SLOTS
            || goods(villager.getInventory()).isEmpty()) {
            return false;
        }
        nextTry = time + COOLDOWN;
        chest = ProductionSink.villageOf(villager)
                .map(village -> HomeChests.of(level, villager))
                .orElse(List.of()).stream()
                .filter(pos -> VillageStorage.getContainer(level, pos)
                        .map(c -> goods(villager.getInventory()).keySet().stream().anyMatch(item -> VillageStorage.hasSpaceFor(c, new ItemStack(item))))
                        .orElse(false))
                .findFirst().orElse(null);
        return chest != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        finished = false;
        tracker.reset();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return !finished && chest != null
               && !villager.getVillagerBrain().isPanicking()
               && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (chest == null) {
            finished = true;
            return;
        }
        ApproachTracker.Approach approach = tracker.approach(villager, chest, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            finished = true;
        } else if (approach == ApproachTracker.Approach.ARRIVED) {
            Optional<Container> container = VillageStorage.getContainer(level, chest);
            container.ifPresent(c -> goods(villager.getInventory()).forEach((item, count) ->
                    VillageStorage.withdraw(villager.getInventory(), item, count, c)));
            villager.swing(villager.getDominantHand());
            finished = true;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        chest = null;
    }

    private static int freeSlots(SimpleContainer inventory) {
        int free = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** What the villager would put away, and how many: everything but tools, some food and what it plants. */
    static Map<Item, Integer> goods(SimpleContainer inventory) {
        Map<Item, Integer> totals = new HashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !isKept(stack)) {
                totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        Map<Item, Integer> goods = new HashMap<>();
        totals.forEach((item, count) -> {
            ItemStack sample = new ItemStack(item);
            int keep = sample.is(ItemTags.SAPLINGS) || PLANTABLE.contains(item) ? KEEP_PLANTABLE
                    : sample.has(DataComponents.FOOD) ? KEEP_FOOD : 0;
            if (count > keep) {
                goods.put(item, count - keep);
            }
        });
        return goods;
    }

    /** Tools, armour and MCA's own items (rings, letters) always stay with the villager. */
    private static boolean isKept(ItemStack stack) {
        return stack.isDamageableItem() || stack.has(DataComponents.EQUIPPABLE)
               || MCA.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }
}
