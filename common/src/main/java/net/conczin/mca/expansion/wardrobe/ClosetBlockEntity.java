package net.conczin.mca.expansion.wardrobe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a wardrobe door or a dresser side holds: an outfit per place (three shelves behind a door, one per drawer),
 * and which of its doors or drawers stand open. On the client it also plays their swinging and sliding.
 */
public class ClosetBlockEntity extends BlockEntity {
    /** How long opening or closing takes, in ms. */
    private static final float DURATION = 350.0F;
    static final int MAX_COMPARTMENTS = 4;

    private final NonNullList<ItemStack> items;
    /** A bit per door or drawer, set while it is open. */
    private int open;

    // client: the animation of each door or drawer
    private int shown = -1;
    private final float[] from = new float[MAX_COMPARTMENTS];
    private final long[] changed = new long[MAX_COMPARTMENTS];

    public ClosetBlockEntity(BlockPos pos, BlockState state) {
        super(Wardrobes.CLOSET, pos, state);
        int slots = state.getBlock() instanceof FurnitureBlock furniture ? furniture.slots() : MAX_COMPARTMENTS;
        items = NonNullList.withSize(slots, ItemStack.EMPTY);
    }

    public int size() {
        return items.size();
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    public ItemStack takeItem(int slot) {
        ItemStack taken = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        setChanged();
        return taken;
    }

    public boolean isOpen(int compartment) {
        return (open & 1 << compartment) != 0;
    }

    public void setOpen(int compartment, boolean value) {
        open = value ? open | 1 << compartment : open & ~(1 << compartment);
        setChanged();
    }

    /** How open a door or drawer looks now, 0 shut to 1 open, eased in and out after a change (client). */
    public float openAmount(int compartment, long millis) {
        if (shown == -1) {
            shown = open;
        }
        int bit = 1 << compartment;
        boolean target = (open & bit) != 0;
        if (((shown & bit) != 0) != target) {
            from[compartment] = current(compartment, !target, millis);
            changed[compartment] = millis;
            shown ^= bit;
        }
        return current(compartment, target, millis);
    }

    private float current(int compartment, boolean target, long millis) {
        float to = target ? 1.0F : 0.0F;
        if (changed[compartment] == 0) {
            return to;
        }
        float t = Math.min(1.0F, (millis - changed[compartment]) / DURATION);
        if (t >= 1.0F) {
            changed[compartment] = 0;
            return to;
        }
        float eased = t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
        return from[compartment] + (to - from[compartment]) * eased;
    }

    /** Saves and tells the clients. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        open = input.getIntOr("Open", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        output.putInt("Open", open);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }
}
