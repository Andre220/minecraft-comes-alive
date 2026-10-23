package net.conczin.mca.expansion.barter;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * A villager haggling over its own belongings: what it sells leaves its inventory, what it is paid
 * goes in. Separate from its profession's trades, so anyone can do it.
 */
public class BarterMerchant implements Merchant {
    private static final double REACH_SQR = 8.0 * 8.0;

    private final VillagerEntityMCA villager;
    private MerchantOffers offers;
    private @Nullable Player trading;

    public BarterMerchant(VillagerEntityMCA villager, MerchantOffers offers) {
        this.villager = villager;
        this.offers = offers;
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        trading = player;
        // the villager stands still and looks at whoever haggles with it, like when trading
        villager.setTradingPlayer(player);
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return trading;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        SimpleContainer inventory = villager.getInventory();
        take(inventory, offer.getResult());
        give(inventory, offer.getCostA());
        give(inventory, offer.getCostB());
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    private static void take(SimpleContainer inventory, ItemStack wanted) {
        int left = wanted.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        inventory.setChanged();
    }

    private void give(SimpleContainer inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = inventory.addItem(stack.copy());
        if (!rest.isEmpty()) {
            villager.level().addFreshEntity(new ItemEntity(villager.level(), villager.getX(), villager.getY() + 0.5, villager.getZ(), rest));
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return villager.level().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return trading == player && villager.isAlive() && villager.distanceToSqr(player) <= REACH_SQR;
    }
}
