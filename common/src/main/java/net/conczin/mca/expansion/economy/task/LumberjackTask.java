package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.entity.ai.brain.tasks.chore.TreeFelling;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Lumberjacks fell the trees around their chopping block, pick up what the leaves drop and plant
 * saplings where the trees stood.
 */
public class LumberjackTask extends GathererTask {
    private static final double REACH_SQR = 3.5 * 3.5;
    private static final double PICKUP_REACH_SQR = 1.5 * 1.5;
    private static final int MAX_STUMPS = 16;

    private enum Job {FELL, PICKUP, PLANT}

    /** Where trees were felled, replanted once the villager has saplings. */
    private final Deque<BlockPos> stumps = new ArrayDeque<>();

    private @Nullable Job job;
    private @Nullable BlockPos target;
    private @Nullable ItemEntity item;
    private int progress;
    private int required;

    @Override
    protected java.util.function.Predicate<ItemStack> tool() {
        return Chore.CHOP::matchesTool;
    }

    @Override
    protected boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center) {
        int radius = workRadius();

        // replant first, so the forest grows back
        if (villager.getInventory().hasAnyMatching(stack -> stack.is(ItemTags.SAPLINGS))) {
            Optional<BlockPos> stump = stumps.stream()
                    .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).isAir() && saplingFor(villager, level, pos).isPresent())
                    .findFirst();
            if (stump.isPresent()) {
                job = Job.PLANT;
                target = stump.get();
                return true;
            }
        }

        // what the leaves dropped
        AABB area = new AABB(center).inflate(radius, 8, radius);
        Optional<ItemEntity> drop = level.getEntitiesOfClass(ItemEntity.class, area, entity -> isLeafDrop(entity.getItem())).stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr));
        if (drop.isPresent()) {
            job = Job.PICKUP;
            item = drop.get();
            return true;
        }

        List<BlockPos> trees = scan(level, center, radius, 6, 10, villager.blockPosition(), pos ->
                level.getBlockState(pos).is(BlockTags.LOGS)
                && TreeFelling.isTreeStartLog(level, pos)
                && !isNearBuilding(village, pos, 1)
                && !ProductionSink.isSaturated(level, village, level.getBlockState(pos).getBlock().asItem()));
        if (trees.isEmpty()) {
            return false;
        }
        job = Job.FELL;
        target = trees.getFirst();
        progress = 0;
        required = -1;
        return true;
    }

    @Override
    protected Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (job == null) {
            return Progress.DONE;
        }
        return switch (job) {
            case FELL -> tickFell(level, villager, village);
            case PICKUP -> tickPickup(level, villager, village);
            case PLANT -> tickPlant(level, villager);
        };
    }

    private Progress tickFell(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (target == null || !level.getBlockState(target).is(BlockTags.LOGS)) {
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, target, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.WALKING) {
            return Progress.WORKING;
        }

        if (required < 0) {
            equip(villager, Chore.CHOP::matchesTool);
            required = Skills.ticks(villager, Math.max(20, TreeFelling.ticksToFell(level, target, tool(villager))));
            villager.getVillagerBrain().restartWorkAnimation();
        }
        // the chop animation plays the axe sound on each blow, see chop_lumberjack.json
        villager.getVillagerBrain().setWorkAnimation(WorkAnimation.CHOP, 10);
        villager.getLookControl().setLookAt(Vec3.atCenterOf(target));
        progress++;
        if (progress >= required) {
            harvested(level, villager, village, TreeFelling.fell(level, target, villager, tool(villager)));
            stumps.addLast(target);
            while (stumps.size() > MAX_STUMPS) {
                stumps.removeFirst();
            }
            return Progress.DONE;
        }
        return Progress.WORKING;
    }

    private Progress tickPickup(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (item == null || !item.isAlive()) {
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, item.blockPosition(), PICKUP_REACH_SQR + 1.0);
        if (approach == ApproachTracker.Approach.STUCK) {
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.ARRIVED) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            if (stack.is(ItemTags.SAPLINGS)) {
                // kept for replanting, not a product
                ItemStack rest = villager.getInventory().addItem(stack);
                if (!rest.isEmpty()) {
                    villager.spawnAtLocation(level, rest);
                }
            } else {
                collect(level, villager, village, List.of(stack));
            }
            return Progress.DONE;
        }
        return Progress.WORKING;
    }

    private Progress tickPlant(ServerLevel level, VillagerEntityMCA villager) {
        if (target == null || !level.getBlockState(target).isAir()) {
            stumps.remove(target);
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, target, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            stumps.remove(target);
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.ARRIVED) {
            Optional<ItemStack> sapling = saplingFor(villager, level, target);
            if (sapling.isPresent() && sapling.get().getItem() instanceof BlockItem blockItem) {
                level.setBlockAndUpdate(target, blockItem.getBlock().defaultBlockState());
                level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                villager.swing(villager.getDominantHand());
                sapling.get().shrink(1);
            }
            stumps.remove(target);
            return Progress.DONE;
        }
        return Progress.WORKING;
    }

    @Override
    protected void reset() {
        job = null;
        target = null;
        item = null;
        progress = 0;
        required = -1;
    }

    /** A sapling from the inventory that can grow at {@code pos}. */
    private static Optional<ItemStack> saplingFor(VillagerEntityMCA villager, ServerLevel level, BlockPos pos) {
        ItemStack fallback = null;
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().canSurvive(level, pos)) {
                if (fallback == null) {
                    fallback = stack;
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static boolean isLeafDrop(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE) || stack.is(Items.STICK);
    }
}
