package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.expansion.minds.Provisions;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.ActionType;
import net.conczin.mca.entity.ai.VillagerDrinks;
import net.conczin.mca.server.property.PropertyRules;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * A hungry villager without food in its inventory (or a thirsty one without drinks, see
 * {@link #forDrinks()}) fetches some from a chest or barrel at home:
 * anywhere in its home structure, or near its bed when the bed is not in a known building. Locked
 * containers of players are left alone.
 */
public class NeedsFetchFoodTask extends Behavior<VillagerEntityMCA> {
    private static final float HUNGRY = 35.0F;
    private static final int MAX_DURATION = 600;
    private static final int RETRY_COOLDOWN = 1200;
    private static final int HOME_RADIUS = 8;
    private static final int FOOD_TO_TAKE = 4;
    private static final float THIRSTY = 35.0F;
    private static final int DRINKS_TO_TAKE = 2;

    private final Needs.Need need;
    private final float threshold;
    private final Predicate<ItemStack> wanted;
    private final int amount;

    private @Nullable BlockPos container;
    private long nextAttempt;
    private boolean done;

    public NeedsFetchFoodTask() {
        this(Needs.Need.HUNGER, HUNGRY, VillagerEntityMCA::isEdible, FOOD_TO_TAKE);
    }

    /** A thirsty villager with nothing to drink fetches drinks from home the same way. */
    public static NeedsFetchFoodTask forDrinks() {
        return new NeedsFetchFoodTask(Needs.Need.THIRST, THIRSTY, VillagerDrinks::isDrink, DRINKS_TO_TAKE);
    }

    private NeedsFetchFoodTask(Needs.Need need, float threshold, Predicate<ItemStack> wanted, int amount) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
        this.need = need;
        this.threshold = threshold;
        this.wanted = wanted;
        this.amount = amount;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Needs needs = villager.getNeeds();
        // With the action queue on, fetch when eating (or drinking) heads it.
        boolean wanted = ActionQueue.isEnabled()
                ? villager.getActionQueue().isCurrent(need == Needs.Need.THIRST ? ActionType.DRINK : ActionType.EAT)
                : needs.get(need) < threshold;
        if (level.getGameTime() < nextAttempt || !needs.hasNeeds() || !wanted) {
            return false;
        }
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.WORK && activity != Activity.MEET
            || villager.isSleeping() || villager.getVillagerBrain().isPanicking() || has(villager.getInventory())) {
            return false;
        }
        container = findFoodContainer(level, villager);
        if (container == null) {
            container = Provisions.storageWith(level, villager, this.wanted); // expansion: minds
        }
        if (container == null) {
            nextAttempt = level.getGameTime() + RETRY_COOLDOWN;
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        done = false;
        if (container != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(container, 0.5F, 1));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return !done && container != null && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (container == null) {
            return;
        }
        if (Vec3.atCenterOf(container).distanceToSqr(villager.position()) > 2.5 * 2.5) {
            // Other behaviors (strolling, favored buildings) keep setting their own targets: insist.
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(container, 0.5F, 1));
            return;
        }
        if (level.getBlockEntity(container) instanceof Container chest) {
            take(chest, villager.getInventory());
            level.playSound(null, container, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        done = true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        nextAttempt = time + RETRY_COOLDOWN / 4;
        container = null;
    }

    private boolean has(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (wanted.test(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private void take(Container chest, SimpleContainer inventory) {
        int taken = 0;
        for (int i = 0; i < chest.getContainerSize() && taken < amount; i++) {
            ItemStack stack = chest.getItem(i);
            if (!wanted.test(stack)) {
                continue;
            }
            ItemStack portion = stack.split(Math.min(stack.getCount(), amount - taken));
            int amount = portion.getCount();
            ItemStack leftover = inventory.addItem(portion);
            if (!leftover.isEmpty()) {
                // Inventory full: put the rest back.
                stack.grow(leftover.getCount());
                amount -= leftover.getCount();
                taken = amount;
            }
            taken += amount;
            chest.setChanged();
        }
    }

    private @Nullable BlockPos findFoodContainer(ServerLevel level, VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getResidency().getHome();
        if (home.isEmpty() || home.get().dimension() != level.dimension()) {
            return null;
        }
        BlockPos bed = home.get().pos();
        AABB area = villager.getResidency().getHomeVillage()
                .flatMap(village -> PropertyRules.homeOf(village, bed))
                .map(NeedsFetchFoodTask::bounds)
                .orElseGet(() -> new AABB(bed).inflate(HOME_RADIUS, 3, HOME_RADIUS));

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int cx = SectionPos.blockToSectionCoord(Mth.floor(area.minX)); cx <= SectionPos.blockToSectionCoord(Mth.floor(area.maxX)); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(Mth.floor(area.minZ)); cz <= SectionPos.blockToSectionCoord(Mth.floor(area.maxZ)); cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity blockEntity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity instanceof Container chest && area.contains(Vec3.atCenterOf(pos)) && has(chest)
                        && PropertyRules.canVillagerUse(villager, level, pos)) {
                        double distance = pos.distSqr(villager.blockPosition());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static AABB bounds(VillageBuilding building) {
        return AABB.encapsulatingFullBlocks(building.getPos0(), building.getPos1()).inflate(1.0);
    }
}
