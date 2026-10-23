package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A job planned by a player for a village: dig out or fill a box of blocks. The order only
 * stores its box and target block; what is left to do is read from the world, so players
 * helping (or griefing) never desync it.
 */
public class WorkOrder {
    /** A position that failed this often (unreachable, obstructed) is skipped for good. */
    static final int MAX_FAILURES = 3;

    private final int id;
    private final WorkOrderType type;
    private final WorkArea area;
    private final @Nullable BlockState block;
    private final UUID author;
    private final String authorName;
    /** Planned with the Villager Editor: needs no materials. */
    private final boolean free;
    private final long createdAt;

    private boolean paused;
    private int initialWork;
    private int remainingWork;
    /** Blueprint orders: which saved blueprint, turned by {@code rotation} quarter turns clockwise. */
    private @Nullable Identifier blueprint;
    private int rotation;

    // runtime only
    private final Map<BlockPos, Integer> failures = new HashMap<>();
    private long lastMaterialWarning = Long.MIN_VALUE;
    private boolean waitingForMaterial;
    /** For blueprints: the block every position of the area should end up as. */
    private @Nullable Map<BlockPos, BlockState> plan;
    /** For blueprints: a material builders went looking for and found nowhere. */
    private @Nullable Item missingMaterial;

    public WorkOrder(int id, WorkOrderType type, WorkArea area, @Nullable BlockState block,
                     UUID author, String authorName, boolean free, long createdAt) {
        if (type == WorkOrderType.BUILD && block == null) {
            throw new IllegalArgumentException("Build orders need a block");
        }
        this.id = id;
        this.type = type;
        this.area = area;
        this.block = type == WorkOrderType.BUILD ? block : null;
        this.author = author;
        this.authorName = authorName;
        this.free = free;
        this.createdAt = createdAt;
    }

    public static Optional<WorkOrder> load(CompoundTag tag) {
        Optional<WorkOrderType> type = tag.getString("type").flatMap(WorkOrderType::byId);
        Optional<int[]> area = tag.getIntArray("area").filter(values -> values.length == 6);
        Optional<UUID> author = tag.getString("author").flatMap(WorkOrder::parseUuid);
        if (type.isEmpty() || area.isEmpty() || author.isEmpty()) {
            return Optional.empty();
        }
        BlockState block = tag.getCompound("block")
                .map(blockTag -> NbtUtils.readBlockState(BuiltInRegistries.BLOCK, blockTag))
                .filter(state -> !state.isAir())
                .orElse(null);
        if (type.get() == WorkOrderType.BUILD && block == null) {
            return Optional.empty();
        }
        WorkOrder order = new WorkOrder(
                tag.getIntOr("id", 0),
                type.get(),
                WorkArea.fromArray(area.get()),
                block,
                author.get(),
                tag.getStringOr("authorName", ""),
                tag.getBooleanOr("free", false),
                tag.getLongOr("createdAt", 0L)
        );
        order.paused = tag.getBooleanOr("paused", false);
        order.initialWork = tag.getIntOr("initialWork", 0);
        order.remainingWork = tag.getIntOr("remainingWork", order.initialWork);
        order.blueprint = tag.getString("blueprint").map(Identifier::tryParse).orElse(null);
        order.rotation = Math.floorMod(tag.getIntOr("rotation", 0), 4);
        if (order.type == WorkOrderType.BLUEPRINT && order.blueprint == null) {
            return Optional.empty();
        }
        return Optional.of(order);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("type", type.id());
        tag.putIntArray("area", area.toArray());
        if (block != null) {
            tag.put("block", NbtUtils.writeBlockState(block));
        }
        tag.putString("author", author.toString());
        tag.putString("authorName", authorName);
        tag.putBoolean("free", free);
        tag.putLong("createdAt", createdAt);
        tag.putBoolean("paused", paused);
        tag.putInt("initialWork", initialWork);
        tag.putInt("remainingWork", remainingWork);
        if (blueprint != null) {
            tag.putString("blueprint", blueprint.toString());
            tag.putInt("rotation", rotation);
        }
        return tag;
    }

    public int getId() {
        return id;
    }

    public WorkOrderType getType() {
        return type;
    }

    public WorkArea getArea() {
        return area;
    }

    public @Nullable BlockState getBlock() {
        return block;
    }

    /** The item a builder has to carry to place this order's block, or empty for digging and free orders. */
    public Optional<Item> getMaterial() {
        if (free || block == null) {
            return Optional.empty();
        }
        Item item = block.getBlock().asItem();
        return item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    public @Nullable Identifier getBlueprint() {
        return blueprint;
    }

    /** Quarter turns clockwise the blueprint is placed with. */
    public int getRotation() {
        return rotation;
    }

    /** Ties a blueprint order to its blueprint; call right after creating it. */
    void setBlueprint(Identifier blueprint, int rotation) {
        this.blueprint = blueprint;
        this.rotation = Math.floorMod(rotation, 4);
    }

    public @Nullable Map<BlockPos, BlockState> getPlan() {
        return plan;
    }

    /** The material builders miss for this order: the order's block, or for blueprints the last one not found. */
    public Optional<Item> getMissingMaterial() {
        return getMaterial().or(() -> Optional.ofNullable(missingMaterial));
    }

    public void setMissingMaterial(@Nullable Item item) {
        this.missingMaterial = item;
    }

    void setPlan(@Nullable Map<BlockPos, BlockState> plan) {
        this.plan = plan;
    }

    public UUID getAuthor() {
        return author;
    }

    public String getAuthorName() {
        return authorName;
    }

    public boolean isFree() {
        return free;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getInitialWork() {
        return initialWork;
    }

    public int getRemainingWork() {
        return remainingWork;
    }

    void setInitialWork(int work) {
        this.initialWork = work;
        this.remainingWork = work;
    }

    void setRemainingWork(int work) {
        this.remainingWork = work;
        if (work > initialWork) {
            initialWork = work;
        }
    }

    /** Finished fraction, from 0 to 1. */
    public float getProgress() {
        if (initialWork <= 0) {
            return 1.0F;
        }
        return Math.clamp(1.0F - (float) remainingWork / initialWork, 0.0F, 1.0F);
    }

    public boolean isWaitingForMaterial() {
        return waitingForMaterial;
    }

    public void setWaitingForMaterial(boolean waiting) {
        this.waitingForMaterial = waiting;
    }

    /** True at most once per {@code interval} ticks, to throttle "no material" complaints. */
    boolean shouldWarnAboutMaterial(long time, long interval) {
        if (time - lastMaterialWarning >= interval) {
            lastMaterialWarning = time;
            return true;
        }
        return false;
    }

    boolean isSkipped(BlockPos pos) {
        return failures.getOrDefault(pos, 0) >= MAX_FAILURES;
    }

    void recordFailure(BlockPos pos) {
        failures.merge(pos.immutable(), 1, Integer::sum);
    }

    int skippedCount() {
        return (int) failures.values().stream().filter(count -> count >= MAX_FAILURES).count();
    }
}
