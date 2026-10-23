package net.conczin.mca.expansion.economy.task;

import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.expansion.economy.ProductionRecipes;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Workers at their station really produce: every batch of working time runs a production recipe
 * of their profession, taking the inputs from the workshop chest (or their own pockets). Farmers
 * have no recipe; the village gets its share of what they really harvest.
 */
public class WorkshopProductionTask extends Behavior<VillagerEntityMCA> {
    private static final double NEAR_STATION_SQR = 3.5 * 3.5;
    private static final int CHECK_INTERVAL = 20;
    /** Working time counted per check at most, so a long absence is not paid for. */
    private static final int MAX_STEP = 2 * CHECK_INTERVAL;
    private static final Set<Item> CROPS = Set.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.PUMPKIN, Items.MELON);

    private long lastCheck;
    private int workTicks;
    /** How many of each crop the farmer had after the village took its share. */
    private final Map<Item, Integer> harvested = new HashMap<>();

    public WorkshopProductionTask() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT), CHECK_INTERVAL);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long time = level.getGameTime();
        if (!EconomyFeature.enabled() || time - lastCheck < CHECK_INTERVAL || villager.isSleeping()) {
            return false;
        }
        Optional<BlockPos> site = ProductionSink.jobSite(level, villager);
        boolean near = site.filter(pos -> villager.position().distanceToSqr(Vec3.atCenterOf(pos)) <= NEAR_STATION_SQR).isPresent();
        if (!near) {
            lastCheck = time;
        }
        return near;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        int step = (int) Math.min(MAX_STEP, time - lastCheck);
        lastCheck = time;
        Optional<Village> village = ProductionSink.villageOf(villager);
        Optional<BlockPos> site = ProductionSink.jobSite(level, villager);
        if (village.isEmpty() || site.isEmpty()) {
            return;
        }
        if (villager.getVillagerData().profession().is(VillagerProfession.FARMER)) {
            shareHarvest(level, villager, village.get(), site.get());
        } else {
            workTicks += step;
            produce(level, villager, village.get(), site.get());
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return false;
    }

    private void produce(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos site) {
        if (GathererTask.isInventoryFull(villager.getInventory())) {
            return;
        }
        String building = ProductionSink.buildingTypeOf(village, site);
        List<BlockPos> chests = ProductionSink.workshopContainers(level, village, site);
        for (ProductionRecipes.Recipe recipe : byDemand(level, village, ProductionRecipes.forWorker(villager.getProfession(), building))) {
            if (recipe.outputs().isEmpty() && recipe.rare().isEmpty()) {
                continue;
            }
            if (!recipe.outputs().isEmpty() && ProductionSink.isSaturated(level, village, recipe.outputs().getFirst().item())) {
                continue;
            }
            if (!hasInputs(level, villager, chests, recipe)) {
                continue;
            }
            if (workTicks < Skills.ticks(villager, recipe.ticks())) {
                return; // still working on this batch
            }
            workTicks = 0;
            recipe.inputs().forEach(input -> take(level, villager, chests, input.item(), input.count()));
            List<ItemStack> produced = new ArrayList<>();
            recipe.outputs().forEach(output -> produced.add(output.create()));
            recipe.rare().stream()
                    .filter(rare -> villager.getRandom().nextDouble() < rare.chance() * Skills.rareFactor(villager))
                    .forEach(rare -> produced.add(rare.stack().create()));
            Skills.addExtra(villager, produced);
            produced.replaceAll(stack -> masterwork(level, villager, stack));
            ProductionSink.deliver(level, villager, village, site, produced);
            Skills.gain(villager, Skills.GAIN_BATCH);
            villager.swing(villager.getDominantHand());
            villager.playWorkSound();
            return;
        }
        // nothing can be made right now: don't bank the waiting as working time
        workTicks = 0;
    }

    /** Smarter villagers make first what the village storage lacks most. */
    private static List<ProductionRecipes.Recipe> byDemand(ServerLevel level, Village village, List<ProductionRecipes.Recipe> recipes) {
        if (!Skills.enabled() || recipes.size() < 2 || !VillageStorage.exists(village)) {
            return recipes;
        }
        Map<Item, Integer> stock = VillageStorage.contents(level, village);
        List<ProductionRecipes.Recipe> sorted = new ArrayList<>(recipes);
        sorted.sort(java.util.Comparator.comparingInt(recipe -> recipe.outputs().isEmpty() ? Integer.MAX_VALUE
                : stock.getOrDefault(recipe.outputs().getFirst().item(), 0)));
        return sorted;
    }

    /** A skilled smith now and then enchants what they make: up to one in four at skill 100. */
    private static ItemStack masterwork(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        float skill = Skills.get(villager);
        if (!Skills.enabled() || !stack.isEnchantable() || stack.isEnchanted()
            || villager.getRandom().nextFloat() >= skill / Skills.MAX * 0.25F) {
            return stack;
        }
        return EnchantmentHelper.enchantItem(villager.getRandom(), stack, 5 + (int) (skill / 4),
                level.registryAccess(), Optional.empty());
    }

    private static boolean hasInputs(ServerLevel level, VillagerEntityMCA villager, List<BlockPos> chests, ProductionRecipes.Recipe recipe) {
        for (ProductionRecipes.Stack input : recipe.inputs()) {
            int available = VillageStorage.count(villager.getInventory(), input.item());
            for (BlockPos pos : chests) {
                available += VillageStorage.getContainer(level, pos).map(c -> VillageStorage.count(c, input.item())).orElse(0);
            }
            if (available < input.count()) {
                return false;
            }
        }
        return true;
    }

    /** Uses up inputs, from the workshop chest first. */
    private static void take(ServerLevel level, VillagerEntityMCA villager, List<BlockPos> chests, Item item, int count) {
        int left = count;
        for (BlockPos pos : chests) {
            Optional<Container> container = VillageStorage.getContainer(level, pos);
            if (container.isPresent()) {
                left -= remove(container.get(), item, left);
            }
        }
        remove(villager.getInventory(), item, left);
    }

    static int remove(Container container, Item item, int max) {
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize() && removed < max; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                int take = Math.min(stack.getCount(), max - removed);
                stack.shrink(take);
                removed += take;
            }
        }
        if (removed > 0) {
            container.setChanged();
        }
        return removed;
    }

    /** The village's share of crops harvested since the last visit to the composter. */
    private void shareHarvest(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos site) {
        List<BlockPos> chests = ProductionSink.workshopContainers(level, village, site);
        for (Item crop : CROPS) {
            int count = VillageStorage.count(villager.getInventory(), crop);
            int before = harvested.getOrDefault(crop, count);
            int share = chests.isEmpty() || count <= before ? 0 : ProductionSink.villageShare(village, count - before, villager.getRandom());
            int moved = 0;
            for (BlockPos pos : chests) {
                if (moved >= share) {
                    break;
                }
                Optional<Container> container = VillageStorage.getContainer(level, pos);
                if (container.isPresent()) {
                    moved += VillageStorage.withdraw(villager.getInventory(), crop, share - moved, container.get());
                }
            }
            if (count > before) {
                Skills.gain(villager, Math.min(Skills.GAIN_BATCH, (count - before) * 0.1F));
            }
            harvested.put(crop, count - moved);
        }
    }
}
