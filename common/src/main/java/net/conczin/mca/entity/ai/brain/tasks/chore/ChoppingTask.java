package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ChoppingTask extends AbstractChoreTask {
    private static final double MAX_WORK_DISTANCE_SQUARED = 6.0;

    private int chopTicks, targetTreeTicks;
    private BlockPos targetTree;

    public ChoppingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.CHOP && super.checkExtraStartConditions(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return checkExtraStartConditions(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        clearChoreItem(villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), Chore.CHOP::matchesTool);
            if (i == -1) {
                abandonJobWithMessage("chore.chopping.noaxe");
            } else {
                villager.setItemInHand(villager.getDominantHand(), villager.getInventory().getItem(i));
            }
        }
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (this.villager == null) this.villager = villager;

        if (!InventoryUtils.contains(villager.getInventory(), Chore.CHOP::matchesTool) && !villager.hasItemInSlot(villager.getDominantSlot())) {
            abandonJobWithMessage("chore.chopping.noaxe");
        } else if (!villager.hasItemInSlot(villager.getDominantSlot())) {
            int i = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), Chore.CHOP::matchesTool);
            ItemStack stack = villager.getInventory().getItem(i);
            villager.setItemInHand(villager.getDominantHand(), stack);
        }

        if (targetTree == null) {
            List<BlockPos> nearbyLogs = TaskUtils.getNearbyBlocks(villager.blockPosition(), world, (blockState -> blockState.is(BlockTags.LOGS)), 15, 5);
            List<BlockPos> nearbyTrees = new ArrayList<>();

            // valid "trees" are logs on the ground with leaves around them
            nearbyLogs.stream()
                    .filter(log -> TreeFelling.isTreeStartLog(world, log))
                    .forEach(nearbyTrees::add);
            targetTree = TaskUtils.getNearestPoint(villager.blockPosition(), nearbyTrees);

            if (targetTree != null) {
                targetTreeTicks += TreeFelling.ticksToFell(world, targetTree, villager.getItemInHand(villager.getDominantHand()));
            }

            failedTicks = FAILED_COOLDOWN;
            return;
        }

        villager.moveTowardsPersistent(targetTree, 0.5F, 1);

        BlockState state = world.getBlockState(targetTree);
        if (state.is(BlockTags.LOGS)) {
            if (villager.distanceToSqr(Vec3.atBottomCenterOf(targetTree)) <= MAX_WORK_DISTANCE_SQUARED) {
                villager.swing(villager.getDominantHand());
                chopTicks++;

                // cut down a tree every few seconds, dependent on config + the mining speed multiplier
                if (chopTicks >= targetTreeTicks) {
                    chopTicks = 0;

                    TreeFelling.fell(world, targetTree, villager, villager.getItemInHand(villager.getDominantHand()))
                            .forEach(villager.getInventory()::addItem);
                }
            }
        } else {
            targetTree = null;
            targetTreeTicks = 0;
        }

        super.tick(world, villager, time);
    }
}
