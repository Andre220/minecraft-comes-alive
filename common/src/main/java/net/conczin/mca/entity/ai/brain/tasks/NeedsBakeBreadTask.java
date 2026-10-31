package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A hungry villager with no food but wheat (its own, or fetched from home by {@link NeedsFetchFoodTask#forBaking()})
 * bakes bread at a crafting table nearby: it works there a few seconds and comes away with a loaf for each three
 * wheat, which it then eats like any food it carries.
 */
public class NeedsBakeBreadTask extends Behavior<VillagerEntityMCA> {
    public static final int WHEAT_PER_BREAD = 3;
    private static final float HUNGRY = 35.0F;
    private static final int MOST_LOAVES = 2;
    private static final int SEARCH_RADIUS = 16;
    private static final int SEARCH_HEIGHT = 4;
    private static final int WORK_TICKS = 60;
    private static final int MAX_DURATION = 600;
    private static final int RETRY_COOLDOWN = 1200;

    private @Nullable BlockPos table;
    private int worked;
    private long nextAttempt;

    public NeedsBakeBreadTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    public static boolean hasFood(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (VillagerEntityMCA.isEdible(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public static boolean canBake(VillagerEntityMCA villager) {
        return villager.getInventory().countItem(Items.WHEAT) >= WHEAT_PER_BREAD;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Needs needs = villager.getNeeds();
        boolean wanted = ActionQueue.isEnabled() ? villager.getActionQueue().isCurrent(ActionType.EAT) : needs.get(Needs.Need.HUNGER) < HUNGRY;
        if (level.getGameTime() < nextAttempt || !needs.hasNeeds() || !wanted || hasFood(villager) || !canBake(villager)) {
            return false;
        }
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.WORK && activity != Activity.MEET
            || villager.isSleeping() || villager.getVillagerBrain().isPanicking()) {
            return false;
        }
        table = BlockPos.findClosestMatch(villager.blockPosition(), SEARCH_RADIUS, SEARCH_HEIGHT,
                pos -> level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)).map(BlockPos::immutable).orElse(null);
        if (table == null) {
            nextAttempt = level.getGameTime() + RETRY_COOLDOWN;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        worked = 0;
        walk(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return table != null && canBake(villager) && level.getBlockState(table).is(Blocks.CRAFTING_TABLE)
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && !villager.getVillagerBrain().isPanicking();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (table == null) {
            return;
        }
        if (Vec3.atCenterOf(table).distanceToSqr(villager.position()) > 2.5 * 2.5) {
            if (villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty()) {
                walk(villager);
            }
            return;
        }
        // kneading at the table
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(table));
        if (worked % 15 == 0) {
            villager.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, table, SoundEvents.VILLAGER_WORK_FARMER, SoundSource.NEUTRAL, 0.6F, 1.0F);
        }
        if (++worked >= WORK_TICKS) {
            int loaves = Math.min(MOST_LOAVES, villager.getInventory().countItem(Items.WHEAT) / WHEAT_PER_BREAD);
            villager.getInventory().removeItemType(Items.WHEAT, loaves * WHEAT_PER_BREAD);
            ItemStack leftover = villager.getInventory().addItem(new ItemStack(Items.BREAD, loaves));
            if (!leftover.isEmpty()) {
                villager.spawnAtLocation(level, leftover);
            }
            level.playSound(null, table, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.2F);
            table = null; // done
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextAttempt = time + RETRY_COOLDOWN / 4;
        table = null;
    }

    private void walk(VillagerEntityMCA villager) {
        if (table != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(table, 0.5F, 1));
        }
    }
}
