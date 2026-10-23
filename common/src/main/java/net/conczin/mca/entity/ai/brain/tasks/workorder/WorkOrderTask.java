package net.conczin.mca.entity.ai.brain.tasks.workorder;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.server.world.data.workorder.VillageWorkOrders;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrder;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builders and miners working on their village's work orders: walk to a planned block, dig it
 * out or place it, fetch blocks from the village storage when they run out, and bring what they
 * dug back to it.
 */
public class WorkOrderTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 6000;
    private static final int SEARCH_COOLDOWN = 200;
    private static final int FAILED_COOLDOWN = 600;
    private static final float SPEED = 0.5F;
    /** Squared distance from the eyes to the block centre a villager can work at, about a player's reach. */
    private static final double REACH_SQR = 4.5 * 4.5;
    private static final double CONTAINER_REACH_SQR = 2.5 * 2.5;
    /** Ticks without getting closer before a target counts as unreachable. */
    private static final int STUCK_TICKS = 160;
    private static final int MAX_FAILURES_IN_ROW = 6;
    /** Most blocks fetched from storage in one trip. */
    private static final int BATCH_SIZE = 64;
    private static final int PLACE_TICKS = 10;
    private static final long MATERIAL_WARNING_INTERVAL = 6000;

    private enum Phase {WORK, FETCH, DEPOSIT}

    private final List<WorkOrderType> types;

    private @Nullable Phase phase;
    private int orderId = -1;
    private @Nullable BlockPos target;
    private @Nullable BlockPos container;
    private VillageWorkOrders.@Nullable Assignment planned;
    private boolean plannedDeposit;
    private boolean finished;
    private boolean active;
    private boolean equipped;

    private int progress;
    private int required;
    private int stuckTicks;
    private double bestDistance;
    private int failuresInRow;
    private long nextSearch;

    /** What this villager dug and still has to bring to the storage. */
    private final Set<Item> haul = new HashSet<>();
    /** Spots the villager stood in itself, tried again later. */
    private final Set<BlockPos> deferred = new HashSet<>();
    /** Blueprint spots whose material is nowhere to be found right now. */
    private final Set<BlockPos> materialBlocked = new HashSet<>();
    /** What the villager is on its way to the storage for. */
    private @Nullable Item fetching;

    /**
     * @param types the kinds of orders this villager takes, most preferred first
     */
    public WorkOrderTask(List<WorkOrderType> types) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
        this.types = List.copyOf(types);
    }

    /** True while the villager is busy with an order, so idle behaviours can step back. */
    public boolean isActive() {
        return active;
    }

    private static Optional<Village> getVillage(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long time = level.getGameTime();
        if (time < nextSearch || villager.getVillagerBrain().isPanicking() || villager.isSleeping()) {
            return false;
        }
        Optional<Village> village = getVillage(villager);
        if (village.isEmpty() || village.get().getWorkOrders().isEmpty() && !carriesHaul(villager)) {
            nextSearch = time + SEARCH_COOLDOWN;
            return false;
        }

        VillageWorkOrders orders = village.get().getWorkOrders();
        for (WorkOrderType type : types) {
            Optional<VillageWorkOrders.Assignment> assignment = orders.findWork(level, villager, type::equals,
                    order -> hasMaterial(level, village.get(), villager, order));
            if (assignment.isPresent()) {
                planned = assignment.get();
                return true;
            }
        }

        if (carriesHaul(villager) && VillageStorage.exists(village.get())) {
            plannedDeposit = true;
            return true;
        }

        // nothing to do: complain about orders stuck for lack of material
        for (WorkOrder order : orders.getOrders()) {
            if (order.isWaitingForMaterial() && types.contains(order.getType())) {
                order.getMissingMaterial().ifPresent(material ->
                        orders.warnMissingMaterial(level, villager, order, material, MATERIAL_WARNING_INTERVAL));
            }
        }
        nextSearch = time + SEARCH_COOLDOWN;
        return false;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        active = true;
        finished = false;
        failuresInRow = 0;
        deferred.clear();
        materialBlocked.clear();
        if (plannedDeposit) {
            plannedDeposit = false;
            beginDeposit(level, villager);
        } else if (planned != null) {
            VillageWorkOrders.Assignment assignment = planned;
            planned = null;
            getVillage(villager).ifPresentOrElse(
                    village -> beginOrder(level, villager, village, assignment.order(), assignment.pos()),
                    () -> finished = true);
        } else {
            finished = true;
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
        getVillage(villager).ifPresent(village -> village.getWorkOrders().release(villager.getUUID()));
        if (target != null) {
            level.destroyBlockProgress(villager.getId(), target, -1);
        }
        if (equipped) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
            equipped = false;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        phase = null;
        target = null;
        container = null;
        fetching = null;
        orderId = -1;
        active = false;
        planned = null;
        plannedDeposit = false;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        Optional<Village> village = getVillage(villager);
        if (village.isEmpty() || phase == null) {
            finished = true;
            return;
        }
        switch (phase) {
            case WORK -> tickWork(level, villager, village.get());
            case FETCH -> tickFetch(level, villager, village.get());
            case DEPOSIT -> tickDeposit(level, villager, village.get());
        }
    }

    // ---------------------------------------------------------------- orders

    private void beginOrder(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order, BlockPos pos) {
        orderId = order.getId();
        Optional<Item> material = materialFor(level, order, pos);
        if (material.isPresent() && !carries(villager, material.get())) {
            if (!beginFetch(level, villager, village, order, material.get())) {
                materialBlocked.add(pos.immutable());
                order.setMissingMaterial(material.get());
                nextTarget(level, villager, village, order);
            }
            return;
        }
        setTarget(level, villager, village, order, pos);
    }

    private Optional<WorkOrder> currentOrder(Village village) {
        return village.getWorkOrders().get(orderId).filter(order -> !order.isPaused());
    }

    private void setTarget(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order, BlockPos pos) {
        if (target != null && !target.equals(pos)) {
            level.destroyBlockProgress(villager.getId(), target, -1);
        }
        phase = Phase.WORK;
        target = pos.immutable();
        progress = 0;
        required = 0;
        stuckTicks = 0;
        bestDistance = Double.MAX_VALUE;
        village.getWorkOrders().reserve(villager, order, target, level.getGameTime());
        equipFor(level, villager, order, target);
    }

    /** Moves on to the next block of the current order, or wraps up when there is none. */
    private void nextTarget(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order) {
        if (order.getType() != WorkOrderType.BUILD && isInventoryFull(villager) && carriesHaul(villager) && VillageStorage.exists(village)) {
            beginDeposit(level, villager);
            return;
        }
        Optional<BlockPos> next = village.getWorkOrders().nextTarget(level, order, villager,
                pos -> deferred.contains(pos) || materialBlocked.contains(pos));
        if (next.isEmpty() && !deferred.isEmpty()) {
            deferred.clear();
            next = village.getWorkOrders().nextTarget(level, order, villager, materialBlocked::contains);
        }
        if (next.isPresent()) {
            setTarget(level, villager, village, order, next.get());
        } else {
            if (!materialBlocked.isEmpty()) {
                // the rest of the blueprint waits for materials nobody has
                order.setWaitingForMaterial(true);
                order.getMissingMaterial().ifPresent(item -> village.getWorkOrders()
                        .warnMissingMaterial(level, villager, order, item, MATERIAL_WARNING_INTERVAL));
            }
            wrapUp(level, villager, village, order);
        }
    }

    private void wrapUp(ServerLevel level, VillagerEntityMCA villager, Village village, @Nullable WorkOrder order) {
        if (order != null && order.getType() != WorkOrderType.BUILD) {
            climbOut(level, villager, order.getArea());
        }
        if (carriesHaul(villager) && VillageStorage.exists(village)) {
            beginDeposit(level, villager);
        } else {
            finished = true;
            nextSearch = 0;
        }
    }

    private void fail(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order) {
        if (target != null) {
            village.getWorkOrders().reportFailure(order, target);
        }
        failuresInRow++;
        if (failuresInRow >= MAX_FAILURES_IN_ROW) {
            // probably trapped or the order is out of reach, give others a go
            if (order.getType() != WorkOrderType.BUILD) {
                climbOut(level, villager, order.getArea());
            }
            finished = true;
            nextSearch = level.getGameTime() + FAILED_COOLDOWN;
        } else {
            nextTarget(level, villager, village, order);
        }
    }

    // ---------------------------------------------------------------- what a spot needs

    private static @Nullable BlockState planned(WorkOrder order, BlockPos pos) {
        return order.getType() == WorkOrderType.BLUEPRINT && order.getPlan() != null ? order.getPlan().get(pos) : null;
    }

    /** Whether the villager has to dig at the spot: always for dig orders, for blueprints where something is in the way. */
    private static boolean isDigging(ServerLevel level, WorkOrder order, BlockPos pos) {
        return switch (order.getType()) {
            case DIG -> true;
            case BUILD -> false;
            case BLUEPRINT -> {
                BlockState planned = planned(order, pos);
                yield planned != null && (planned.isAir() || WorkOrderBlocks.mustClear(level, pos, planned));
            }
        };
    }

    /** The block the villager places at the spot. */
    private static @Nullable BlockState blockFor(WorkOrder order, BlockPos pos) {
        return order.getType() == WorkOrderType.BLUEPRINT ? planned(order, pos) : order.getBlock();
    }

    /** The item the villager needs in hand to work the spot, if any. */
    private static Optional<Item> materialFor(ServerLevel level, WorkOrder order, BlockPos pos) {
        if (order.getType() == WorkOrderType.BUILD) {
            return order.getMaterial();
        }
        if (order.getType() != WorkOrderType.BLUEPRINT || order.isFree() || isDigging(level, order, pos)) {
            return Optional.empty();
        }
        BlockState planned = planned(order, pos);
        if (planned == null || !WorkOrderBlocks.isObtainable(planned)) {
            return Optional.empty();
        }
        return Optional.of(planned.getBlock().asItem());
    }

    // ---------------------------------------------------------------- work

    private void tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        Optional<WorkOrder> current = currentOrder(village);
        if (current.isEmpty() || target == null) {
            wrapUp(level, villager, village, null);
            return;
        }
        WorkOrder order = current.get();
        if (!WorkOrderBlocks.needsWork(level, order, target)) {
            // done by someone else in the meantime
            nextTarget(level, villager, village, order);
            return;
        }

        Optional<Item> material = materialFor(level, order, target);
        if (material.isPresent() && !carries(villager, material.get())) {
            if (!beginFetch(level, villager, village, order, material.get())) {
                if (order.getType() == WorkOrderType.BLUEPRINT) {
                    // other blocks of the blueprint may still be possible
                    materialBlocked.add(target);
                    order.setMissingMaterial(material.get());
                    nextTarget(level, villager, village, order);
                } else {
                    village.getWorkOrders().warnMissingMaterial(level, villager, order, material.get(), MATERIAL_WARNING_INTERVAL);
                    wrapUp(level, villager, village, order);
                }
            }
            return;
        }

        if (!isInReach(villager, target)) {
            if (walkTowards(villager, target, 2)) {
                fail(level, villager, village, order);
            }
            return;
        }

        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        villager.getLookControl().setLookAt(Vec3.atCenterOf(target));

        boolean digging = isDigging(level, order, target);
        // digging chops at the block, building hammers it in place; see chop/hammer_<profession>.json
        villager.getVillagerBrain().setWorkAnimation(digging ? WorkAnimation.CHOP : WorkAnimation.HAMMER, 10);
        progress++;

        if (digging) {
            if (required == 0) {
                required = Skills.ticks(villager, digTicks(level, target, villager.getItemInHand(villager.getDominantHand()))); // expansion: minds
            }
            level.destroyBlockProgress(villager.getId(), target, Math.min(9, progress * 10 / required));
            if (progress >= required) {
                dig(level, villager, village, order);
            }
        } else {
            if (required == 0) {
                required = Skills.ticks(villager, Math.max(2, Math.round(PLACE_TICKS / speedMultiplier()))); // expansion: minds
            }
            if (progress >= required) {
                build(level, villager, village, order);
            }
        }
    }

    private void dig(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order) {
        BlockPos pos = target;
        BlockState state = level.getBlockState(pos);
        ItemStack tool = villager.getItemInHand(villager.getDominantHand());
        ItemStack lootTool = !tool.isEmpty() && tool.isCorrectToolForDrops(state) ? tool : fallbackTool(state);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, villager, lootTool);

        level.destroyBlockProgress(villager.getId(), pos, -1);
        if (!level.destroyBlock(pos, false, villager)) {
            fail(level, villager, village, order);
            return;
        }
        for (ItemStack drop : drops) {
            collect(level, villager, drop);
        }
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        }
        failuresInRow = 0;
        Skills.gain(villager, Skills.GAIN_BLOCK); // expansion: minds
        village.getWorkOrders().onBlockDone(level, order);
        nextTarget(level, villager, village, order);
    }

    private void build(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order) {
        BlockPos pos = target;
        BlockState state = blockFor(order, pos);
        if (state == null) {
            finished = true;
            return;
        }
        if (!state.canSurvive(level, pos)) {
            // a torch needs its wall first: try again once the rest stands
            if (order.getType() == WorkOrderType.BLUEPRINT && deferred.add(pos)) {
                nextTarget(level, villager, village, order);
            } else {
                fail(level, villager, village, order);
            }
            return;
        }
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            // usually the villager itself standing there; come back to it later
            if (villager.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) {
                deferred.add(pos);
                nextTarget(level, villager, village, order);
            } else {
                fail(level, villager, village, order);
            }
            return;
        }

        Optional<Item> material = materialFor(level, order, pos);
        if (material.isPresent() && !consume(villager, material.get())) {
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (order.getType() == WorkOrderType.BLUEPRINT) {
            placeSecondHalves(level, order, pos, state);
        }
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(villager, GameEvent.BLOCK_PLACE, pos);

        failuresInRow = 0;
        deferred.remove(pos);
        Skills.gain(villager, Skills.GAIN_BLOCK); // expansion: minds
        village.getWorkOrders().onBlockDone(level, order);
        nextTarget(level, villager, village, order);
    }

    /** Doors, beds and tall plants: the other half goes up together with the first, like a player placing one. */
    private static void placeSecondHalves(ServerLevel level, WorkOrder order, BlockPos pos, BlockState placed) {
        Map<BlockPos, BlockState> plan = order.getPlan();
        if (plan == null) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            BlockState other = plan.get(neighbour);
            if (other != null && other.is(placed.getBlock()) && WorkOrderBlocks.isSecondHalf(other)
                && level.getBlockState(neighbour).canBeReplaced()) {
                level.setBlock(neighbour, other, Block.UPDATE_ALL);
            }
        }
    }

    private int digTicks(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        float hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        float speed = tool.isEmpty() ? 1.0F : Math.max(1.0F, tool.getDestroySpeed(state));
        boolean correct = !state.requiresCorrectToolForDrops() || !tool.isEmpty() && tool.isCorrectToolForDrops(state);
        // the same pace as a player
        float ticks = hardness * (correct ? 30.0F : 100.0F) / speed / speedMultiplier();
        return Math.clamp((int) Math.ceil(ticks), 4, 600);
    }

    private static float speedMultiplier() {
        return Math.max(0.05F, Config.getInstance().workOrderSpeedMultiplier);
    }

    /** Villagers know their trade: without a proper tool they dig slowly but still get the drops of a stone one. */
    private static ItemStack fallbackTool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return new ItemStack(Items.STONE_PICKAXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return new ItemStack(Items.STONE_SHOVEL);
        } else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return new ItemStack(Items.STONE_AXE);
        } else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return new ItemStack(Items.STONE_HOE);
        }
        return ItemStack.EMPTY;
    }

    private void equipFor(ServerLevel level, VillagerEntityMCA villager, WorkOrder order, BlockPos pos) {
        ItemStack stack;
        if (isDigging(level, order, pos)) {
            stack = bestTool(villager, level.getBlockState(pos));
        } else if (order.isFree()) {
            BlockState block = blockFor(order, pos);
            stack = block == null ? ItemStack.EMPTY : new ItemStack(block.getBlock().asItem());
        } else {
            stack = materialFor(level, order, pos)
                    .map(item -> InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), s -> s.is(item)))
                    .filter(slot -> slot >= 0)
                    .map(slot -> villager.getInventory().getItem(slot))
                    .orElse(ItemStack.EMPTY);
        }
        // like chores, the hand shares the inventory's stack, so using it up empties both
        villager.setItemInHand(villager.getDominantHand(), stack);
        equipped = true;
    }

    private static ItemStack bestTool(VillagerEntityMCA villager, BlockState state) {
        SimpleContainer inventory = villager.getInventory();
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0F;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = stack;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- materials

    private static boolean carries(VillagerEntityMCA villager, Item item) {
        return InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.is(item)) >= 0;
    }

    private static boolean hasMaterial(ServerLevel level, Village village, VillagerEntityMCA villager, WorkOrder order) {
        if (order.getType() == WorkOrderType.BLUEPRINT) {
            return blueprintHasWork(level, village, villager, order);
        }
        Optional<Item> material = order.getMaterial();
        return material.isEmpty()
               || carries(villager, material.get())
               || VillageStorage.count(level, village, material.get()) > 0;
    }

    /**
     * A blueprint can be worked on while something is in the way, or while at least one of the
     * missing materials is carried or stored. Otherwise it waits and remembers what it misses.
     */
    private static boolean blueprintHasWork(ServerLevel level, Village village, VillagerEntityMCA villager, WorkOrder order) {
        Map<BlockPos, BlockState> plan = order.getPlan();
        if (order.isFree() || plan == null) {
            return true;
        }
        for (Map.Entry<BlockPos, BlockState> entry : plan.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState planned = entry.getValue();
            if (level.isLoaded(pos) && (planned.isAir() ? WorkOrderBlocks.needsDigging(level, pos) : WorkOrderBlocks.mustClear(level, pos, planned))) {
                return true;
            }
        }
        Map<Item, Integer> missing = VillageWorkOrders.missingMaterials(level, order);
        if (missing.isEmpty()) {
            return true;
        }
        Map<Item, Integer> stock = VillageStorage.contents(level, village);
        for (Item item : missing.keySet()) {
            if (carries(villager, item) || stock.getOrDefault(item, 0) > 0) {
                return true;
            }
        }
        order.setMissingMaterial(missing.keySet().iterator().next());
        return false;
    }

    private static boolean consume(VillagerEntityMCA villager, Item item) {
        int slot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(), stack -> stack.is(item));
        if (slot < 0) {
            return false;
        }
        villager.getInventory().getItem(slot).shrink(1);
        villager.getInventory().setChanged();
        return true;
    }

    private boolean beginFetch(ServerLevel level, VillagerEntityMCA villager, Village village, WorkOrder order, Item item) {
        Optional<BlockPos> found = VillageStorage.findContaining(level, village, item, villager.blockPosition());
        if (found.isEmpty()) {
            return false;
        }
        phase = Phase.FETCH;
        fetching = item;
        container = found.get();
        stuckTicks = 0;
        bestDistance = Double.MAX_VALUE;
        if (equipped) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
            equipped = false;
        }
        return true;
    }

    /** How many of the item one trip should bring: what the order still needs of it, at most a stack. */
    private static int wanted(ServerLevel level, WorkOrder order, Item item) {
        int needed = order.getType() == WorkOrderType.BLUEPRINT
                ? VillageWorkOrders.missingMaterials(level, order).getOrDefault(item, 1)
                : order.getRemainingWork();
        return Math.min(BATCH_SIZE, Math.max(1, needed));
    }

    private void tickFetch(ServerLevel level, VillagerEntityMCA villager, Village village) {
        Optional<WorkOrder> current = currentOrder(village);
        Item material = fetching;
        if (current.isEmpty() || material == null || container == null) {
            finished = true;
            return;
        }
        WorkOrder order = current.get();
        if (!isNear(villager, container)) {
            if (walkTowards(villager, container, 1)) {
                finished = true;
                nextSearch = level.getGameTime() + FAILED_COOLDOWN;
            }
            return;
        }

        Optional<Container> chest = VillageStorage.getContainer(level, container);
        int wanted = wanted(level, order, material);
        int moved = chest.map(c -> VillageStorage.withdraw(c, material, wanted, villager.getInventory())).orElse(0);
        if (moved > 0) {
            villager.swing(villager.getDominantHand());
        }
        if (carries(villager, material)) {
            fetching = null;
            // blueprints pick a spot the fetched material fits now
            village.getWorkOrders().nextTarget(level, order, villager, materialBlocked::contains).ifPresentOrElse(
                    pos -> setTarget(level, villager, village, order, pos),
                    () -> finished = true);
        } else if (!beginFetch(level, villager, village, order, material)) {
            // someone emptied the storage meanwhile
            village.getWorkOrders().warnMissingMaterial(level, villager, order, material, MATERIAL_WARNING_INTERVAL);
            finished = true;
        }
    }

    // ---------------------------------------------------------------- hauling

    private void collect(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        haul.add(stack.getItem());
        ItemStack rest = villager.getInventory().addItem(stack);
        if (!rest.isEmpty()) {
            Block.popResource(level, villager.blockPosition(), rest);
        }
    }

    private boolean carriesHaul(VillagerEntityMCA villager) {
        if (haul.isEmpty()) {
            return false;
        }
        haul.removeIf(item -> !carries(villager, item));
        return !haul.isEmpty();
    }

    private static boolean isInventoryFull(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void beginDeposit(ServerLevel level, VillagerEntityMCA villager) {
        if (target != null) {
            level.destroyBlockProgress(villager.getId(), target, -1);
        }
        phase = Phase.DEPOSIT;
        container = null;
        stuckTicks = 0;
        bestDistance = Double.MAX_VALUE;
        if (equipped) {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
            equipped = false;
        }
    }

    private void tickDeposit(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (!carriesHaul(villager)) {
            finishDeposit(level, villager, village);
            return;
        }
        if (container == null) {
            Optional<ItemStack> next = firstHaulStack(villager);
            container = next.flatMap(stack -> VillageStorage.findSpaceFor(level, village, stack, villager.blockPosition())).orElse(null);
            if (container == null) {
                // storage is full or gone, keep the items for now
                haul.clear();
                finishDeposit(level, villager, village);
                return;
            }
            stuckTicks = 0;
            bestDistance = Double.MAX_VALUE;
        }
        if (!isNear(villager, container)) {
            if (walkTowards(villager, container, 1)) {
                finished = true;
                nextSearch = level.getGameTime() + FAILED_COOLDOWN;
            }
            return;
        }

        boolean moved = false;
        Optional<Container> chest = VillageStorage.getContainer(level, container);
        if (chest.isPresent()) {
            SimpleContainer inventory = villager.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && haul.contains(stack.getItem())) {
                    ItemStack rest = VillageStorage.insert(chest.get(), stack);
                    moved |= rest.getCount() != stack.getCount();
                    inventory.setItem(slot, rest);
                }
            }
            inventory.setChanged();
            villager.swing(villager.getDominantHand());
        }
        if (!moved) {
            // nothing fits anywhere sensible, keep the rest
            haul.clear();
        }
        // whatever did not fit goes to the next container
        container = null;
    }

    private void finishDeposit(ServerLevel level, VillagerEntityMCA villager, Village village) {
        Optional<WorkOrder> order = currentOrder(village);
        if (order.isPresent()) {
            Optional<BlockPos> next = village.getWorkOrders().nextTarget(level, order.get(), villager, pos -> false);
            if (next.isPresent()) {
                setTarget(level, villager, village, order.get(), next.get());
                return;
            }
        }
        finished = true;
        nextSearch = 0;
    }

    private Optional<ItemStack> firstHaulStack(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && haul.contains(stack.getItem())) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- movement

    private static boolean isInReach(VillagerEntityMCA villager, BlockPos pos) {
        return villager.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH_SQR;
    }

    private static boolean isNear(VillagerEntityMCA villager, BlockPos pos) {
        return villager.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) <= CONTAINER_REACH_SQR;
    }

    /**
     * Keeps walking towards the position.
     *
     * @return true once the villager stopped getting closer for too long
     */
    private boolean walkTowards(VillagerEntityMCA villager, BlockPos pos, int closeEnough) {
        villager.moveTowardsPersistent(pos, SPEED, closeEnough);
        double distance = villager.position().distanceToSqr(Vec3.atCenterOf(pos));
        if (distance < bestDistance - 0.25) {
            bestDistance = distance;
            stuckTicks = 0;
            return false;
        }
        return ++stuckTicks > STUCK_TICKS;
    }

    /** A villager who dug itself into a pit gets back up to the rim. */
    private static void climbOut(ServerLevel level, VillagerEntityMCA villager, WorkArea area) {
        BlockPos feet = villager.blockPosition();
        if (!area.isNear(feet, 0) || area.maxY() - feet.getY() < 2) {
            return;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = area.minX() - 1; x <= area.maxX() + 1; x++) {
            for (int z = area.minZ() - 1; z <= area.maxZ() + 1; z++) {
                boolean rim = x < area.minX() || x > area.maxX() || z < area.minZ() || z > area.maxZ();
                if (!rim) {
                    continue;
                }
                for (int y = area.maxY() + 1; y >= feet.getY(); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isStandable(level, pos)) {
                        double distance = pos.distSqr(feet);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = pos;
                        }
                        break;
                    }
                }
            }
        }
        if (best != null) {
            villager.getNavigation().stop();
            villager.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
        }
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
               && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
               && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
               && level.getFluidState(pos).isEmpty();
    }
}
