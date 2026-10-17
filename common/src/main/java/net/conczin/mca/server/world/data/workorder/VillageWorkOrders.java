package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The work orders of one village: planned jobs, which villager works on which block, and
 * bookkeeping of what is left to do. Saved with the village.
 */
public class VillageWorkOrders {
    /** How long a villager keeps a block to itself without touching it. */
    private static final long RESERVATION_TICKS = 1200;
    private static final int REFRESH_INTERVAL = 200;
    /** Prefer blocks with support so builders don't start with floating ones. */
    private static final double UNSUPPORTED_PENALTY = 4096.0;

    private final Village village;
    private final Map<Integer, WorkOrder> orders = new LinkedHashMap<>();
    private int nextId = 1;

    // runtime only
    private final Map<BlockPos, Reservation> reservations = new HashMap<>();
    private final Map<UUID, BlockPos> reservationsByVillager = new HashMap<>();

    private record Reservation(UUID villager, int orderId, long until) {
    }

    public record Assignment(WorkOrder order, BlockPos pos) {
    }

    /** Either the created order or why it was refused. */
    public record CreateResult(@Nullable WorkOrder order, @Nullable Component error) {
        static CreateResult error(String key, Object... args) {
            return new CreateResult(null, Component.translatable(key, args));
        }
    }

    public VillageWorkOrders(Village village) {
        this.village = village;
    }

    public void load(CompoundTag tag) {
        orders.clear();
        nextId = Math.max(1, tag.getIntOr("nextId", 1));
        tag.getListOrEmpty("orders").compoundStream()
                .map(WorkOrder::load)
                .flatMap(Optional::stream)
                .forEach(order -> {
                    orders.put(order.getId(), order);
                    nextId = Math.max(nextId, order.getId() + 1);
                });
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        orders.values().forEach(order -> list.add(order.save()));
        tag.put("orders", list);
        return tag;
    }

    public Collection<WorkOrder> getOrders() {
        return Collections.unmodifiableCollection(orders.values());
    }

    public Optional<WorkOrder> get(int id) {
        return Optional.ofNullable(orders.get(id));
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public CreateResult create(ServerLevel level, WorkOrderType type, WorkArea area, @Nullable BlockState block,
                               ServerPlayer author, boolean free) {
        Config config = Config.getInstance();
        if (area.volume() > config.workOrderMaxVolume) {
            return CreateResult.error("workorder.error.too_big", area.volume(), config.workOrderMaxVolume);
        }
        if (orders.size() >= config.workOrderMaxPerVillage) {
            return CreateResult.error("workorder.error.too_many", config.workOrderMaxPerVillage);
        }
        if (!village.isWithinBorder(area.center(), Village.BORDER_MARGIN)) {
            return CreateResult.error("workorder.error.outside_village", village.getName());
        }
        if (type == WorkOrderType.BUILD) {
            if (block == null || block.isAir()) {
                return CreateResult.error("workorder.error.no_block");
            }
            if (!free && block.getBlock().asItem() == net.minecraft.world.item.Items.AIR) {
                return CreateResult.error("workorder.error.no_item", block.getBlock().getName());
            }
        }

        WorkOrder order = new WorkOrder(nextId, type, area, block, author.getUUID(),
                author.getGameProfile().name(), free, level.getGameTime());
        int work = countRemaining(level, order);
        if (work == 0) {
            return CreateResult.error("workorder.error.nothing_to_do");
        }
        order.setInitialWork(work);
        nextId++;
        orders.put(order.getId(), order);
        village.markDirty();
        return new CreateResult(order, null);
    }

    public boolean cancel(int id) {
        WorkOrder removed = orders.remove(id);
        if (removed == null) {
            return false;
        }
        releaseOrder(id);
        village.markDirty();
        return true;
    }

    public int clear() {
        int count = orders.size();
        orders.clear();
        reservations.clear();
        reservationsByVillager.clear();
        if (count > 0) {
            village.markDirty();
        }
        return count;
    }

    public boolean setPaused(int id, boolean paused) {
        WorkOrder order = orders.get(id);
        if (order == null) {
            return false;
        }
        order.setPaused(paused);
        if (paused) {
            releaseOrder(id);
        }
        village.markDirty();
        return true;
    }

    /**
     * Picks the order and block this villager should work on next, oldest order first.
     *
     * @param canDo         which kinds of orders this villager takes
     * @param hasMaterial   whether the material of a build order can be had (carried or stored)
     */
    public Optional<Assignment> findWork(ServerLevel level, VillagerEntityMCA villager,
                                         Predicate<WorkOrderType> canDo, Predicate<WorkOrder> hasMaterial) {
        for (WorkOrder order : orders.values()) {
            if (order.isPaused() || !canDo.test(order.getType())) {
                continue;
            }
            if (order.getType() == WorkOrderType.BUILD && !order.isFree()) {
                boolean available = hasMaterial.test(order);
                order.setWaitingForMaterial(!available);
                if (!available) {
                    continue;
                }
            }
            Optional<BlockPos> target = nextTarget(level, order, villager, pos -> false);
            if (target.isPresent()) {
                return Optional.of(new Assignment(order, target.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * The best unclaimed block of an order for this villager: the nearest one of the first unfinished layer.
     *
     * @param exclude blocks the villager wants to leave for later
     */
    public Optional<BlockPos> nextTarget(ServerLevel level, WorkOrder order, VillagerEntityMCA villager, Predicate<BlockPos> exclude) {
        long time = level.getGameTime();
        BlockPos origin = villager.blockPosition();
        UUID self = villager.getUUID();
        for (int y : order.getArea().layerOrder(order.getType().topDown())) {
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            for (BlockPos pos : order.getArea().layer(y)) {
                if (!level.isLoaded(pos) || order.isSkipped(pos) || isReservedByOther(pos, self, time) || exclude.test(pos)) {
                    continue;
                }
                if (!WorkOrderBlocks.needsWork(level, order, pos)) {
                    continue;
                }
                double score = pos.distSqr(origin);
                if (order.getType() == WorkOrderType.BUILD && !WorkOrderBlocks.isSupported(level, pos)) {
                    score += UNSUPPORTED_PENALTY;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = pos;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    private boolean isReservedByOther(BlockPos pos, UUID villager, long time) {
        Reservation reservation = reservations.get(pos);
        return reservation != null && reservation.until() > time && !reservation.villager().equals(villager);
    }

    public void reserve(VillagerEntityMCA villager, WorkOrder order, BlockPos pos, long time) {
        release(villager.getUUID());
        BlockPos key = pos.immutable();
        reservations.put(key, new Reservation(villager.getUUID(), order.getId(), time + RESERVATION_TICKS));
        reservationsByVillager.put(villager.getUUID(), key);
    }

    public void release(UUID villager) {
        BlockPos pos = reservationsByVillager.remove(villager);
        if (pos != null) {
            Reservation reservation = reservations.get(pos);
            if (reservation != null && reservation.villager().equals(villager)) {
                reservations.remove(pos);
            }
        }
    }

    private void releaseOrder(int orderId) {
        Iterator<Map.Entry<BlockPos, Reservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Reservation> entry = iterator.next();
            if (entry.getValue().orderId() == orderId) {
                reservationsByVillager.remove(entry.getValue().villager(), entry.getKey());
                iterator.remove();
            }
        }
    }

    /** The villager could not reach or work this block; after a few tries it is skipped. */
    public void reportFailure(WorkOrder order, BlockPos pos) {
        order.recordFailure(pos);
    }

    /** A block of the order got done. Finishes the order once nothing is left. */
    public void onBlockDone(ServerLevel level, WorkOrder order) {
        order.setRemainingWork(Math.max(0, order.getRemainingWork() - 1));
        village.markDirty();
        if (order.getRemainingWork() == 0) {
            // the counter is a guess, make sure with a real count
            refresh(level, order);
        }
    }

    /** @param time the village's staggered tick time, only used to spread refreshes */
    public void tick(ServerLevel level, long time) {
        if (orders.isEmpty() || time % REFRESH_INTERVAL != 0) {
            return;
        }
        long now = level.getGameTime();
        reservations.values().removeIf(reservation -> reservation.until() <= now);
        reservationsByVillager.entrySet().removeIf(entry -> !reservations.containsKey(entry.getValue()));
        for (WorkOrder order : new ArrayList<>(orders.values())) {
            refresh(level, order);
        }
    }

    /** Recounts what is left and completes the order when nothing is. Unloaded orders are left untouched. */
    private void refresh(ServerLevel level, WorkOrder order) {
        WorkArea area = order.getArea();
        if (!level.isLoaded(new BlockPos(area.minX(), area.minY(), area.minZ()))
            || !level.isLoaded(new BlockPos(area.maxX(), area.maxY(), area.maxZ()))) {
            return;
        }
        int remaining = countRemaining(level, order);
        if (remaining != order.getRemainingWork()) {
            order.setRemainingWork(remaining);
            village.markDirty();
        }
        if (remaining - countSkippedRemaining(level, order) <= 0) {
            complete(level, order, remaining > 0);
        }
    }

    private int countRemaining(ServerLevel level, WorkOrder order) {
        int count = 0;
        WorkArea area = order.getArea();
        for (int y : area.layerOrder(true)) {
            for (BlockPos pos : area.layer(y)) {
                if (WorkOrderBlocks.needsWork(level, order, pos)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countSkippedRemaining(ServerLevel level, WorkOrder order) {
        if (order.skippedCount() == 0) {
            return 0;
        }
        int count = 0;
        WorkArea area = order.getArea();
        for (int y : area.layerOrder(true)) {
            for (BlockPos pos : area.layer(y)) {
                if (order.isSkipped(pos) && WorkOrderBlocks.needsWork(level, order, pos)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void complete(ServerLevel level, WorkOrder order, boolean partially) {
        orders.remove(order.getId());
        releaseOrder(order.getId());
        village.markDirty();
        ServerPlayer author = level.getServer().getPlayerList().getPlayer(order.getAuthor());
        if (author != null) {
            author.sendSystemMessage(Component.translatable(
                    partially ? "workorder.completed_partially" : "workorder.completed",
                    order.getId(),
                    Component.translatable("workorder.type." + order.getType().id()),
                    village.getName()
            ).withStyle(partially ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
    }

    /** Lets the author know a build order is stuck for lack of material, at most every few minutes. */
    public void warnMissingMaterial(ServerLevel level, VillagerEntityMCA villager, WorkOrder order, Item material, long interval) {
        if (!order.shouldWarnAboutMaterial(level.getGameTime(), interval)) {
            return;
        }
        ServerPlayer author = level.getServer().getPlayerList().getPlayer(order.getAuthor());
        if (author != null && author.level() == level) {
            villager.sendChatMessage(author, "workorder.no_material", new net.minecraft.world.item.ItemStack(material).getHoverName());
        }
    }

    public List<WorkOrder> ordersAt(BlockPos pos) {
        return orders.values().stream().filter(order -> order.getArea().contains(pos)).toList();
    }
}
