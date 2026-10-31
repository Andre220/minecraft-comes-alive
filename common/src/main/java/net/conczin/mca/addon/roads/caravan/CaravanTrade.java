package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.mixin.addon.roads.AbstractHorseAccessor;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A caravan trading with a village's storage when it gets there: it sells what the village is short of, for the
 * storage's emeralds, and buys up what the village has piled up, with its own. Goods move between the pack animals'
 * chests and the storage's containers, so both sides really change.
 */
final class CaravanTrade {
    /** The village is short of an item it holds fewer than this of. */
    private static final int SHORT = 16;
    /** The village has plenty of an item it holds more than this of. */
    private static final int PLENTY = 128;
    /** Items an emerald buys, either way. */
    private static final int PER_EMERALD = 12;
    private static final int MAX_DEALS = 4;

    private CaravanTrade() {
    }

    static void tradeWithVillage(ServerLevel level, CaravanData.Caravan caravan, Village village, BlockPos near) {
        if (!VillageStorage.exists(village)) {
            return;
        }
        List<Container> chests = chests(level, caravan);
        if (chests.isEmpty()) {
            return;
        }
        int sold = 0;
        int bought = 0;
        // sell: the caravan's goods the village is short of, for the storage's emeralds
        for (Container chest : chests) {
            for (int slot = 0; slot < chest.getContainerSize() && sold < MAX_DEALS; slot++) {
                ItemStack stack = chest.getItem(slot);
                if (stack.isEmpty() || stack.is(Items.EMERALD) || VillageStorage.count(level, village, stack.getItem()) >= SHORT) {
                    continue;
                }
                int price = Math.max(1, stack.getCount() / PER_EMERALD);
                if (VillageStorage.count(level, village, Items.EMERALD) < price
                    || VillageStorage.findSpaceFor(level, village, stack, near).isEmpty()) {
                    continue;
                }
                if (take(level, village, Items.EMERALD, price, near, chest)) {
                    ItemStack goods = chest.removeItemNoUpdate(slot);
                    put(level, village, goods, near);
                    sold++;
                }
            }
        }
        // buy: what the village has piled up, with the caravan's emeralds
        Map<Item, Integer> stock = VillageStorage.contents(level, village);
        for (Map.Entry<Item, Integer> entry : stock.entrySet()) {
            if (bought >= MAX_DEALS) {
                break;
            }
            Item item = entry.getKey();
            if (item == Items.EMERALD || entry.getValue() <= PLENTY) {
                continue;
            }
            int amount = Math.min(item.getDefaultMaxStackSize(), entry.getValue() - PLENTY);
            int price = Math.max(1, amount / PER_EMERALD);
            Container purse = chests.stream().filter(c -> VillageStorage.count(c, Items.EMERALD) >= price).findFirst().orElse(null);
            Container hold = chests.stream().filter(c -> VillageStorage.hasSpaceFor(c, new ItemStack(item, amount))).findFirst().orElse(null);
            if (purse == null || hold == null) {
                continue;
            }
            SimpleContainer paid = new SimpleContainer(1);
            VillageStorage.withdraw(purse, Items.EMERALD, price, paid);
            put(level, village, paid.getItem(0), near);
            if (take(level, village, item, amount, near, hold)) {
                bought++;
            }
        }
        if (sold + bought > 0) {
            Caravans.announce(level, village, Component.translatable("caravan.traded", caravan.name(), village.getName(), sold, bought));
        }
    }

    /** Moves up to {@code amount} of an item out of the storage into a container; false when there was none. */
    private static boolean take(ServerLevel level, Village village, Item item, int amount, BlockPos near, Container into) {
        int left = amount;
        while (left > 0) {
            var pos = VillageStorage.findContaining(level, village, item, near);
            if (pos.isEmpty()) {
                break;
            }
            Container from = VillageStorage.getContainer(level, pos.get()).orElse(null);
            if (from == null) {
                break;
            }
            int moved = VillageStorage.withdraw(from, item, left, into);
            if (moved <= 0) {
                break;
            }
            left -= moved;
        }
        return left < amount;
    }

    /** Puts a stack into the storage; what does not fit drops at the leader's feet. */
    private static void put(ServerLevel level, Village village, ItemStack stack, BlockPos near) {
        ItemStack rest = stack;
        while (!rest.isEmpty()) {
            var pos = VillageStorage.findSpaceFor(level, village, rest, near);
            if (pos.isEmpty()) {
                break;
            }
            Container container = VillageStorage.getContainer(level, pos.get()).orElse(null);
            if (container == null) {
                break;
            }
            ItemStack before = rest;
            rest = VillageStorage.insert(container, rest);
            if (rest.getCount() == before.getCount()) {
                break;
            }
        }
        if (!rest.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                    near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, rest);
            level.addFreshEntity(drop);
        }
    }

    /** The chests of the caravan's pack animals that are around. */
    static List<Container> chests(ServerLevel level, CaravanData.Caravan caravan) {
        List<Container> chests = new ArrayList<>();
        for (UUID id : caravan.animals) {
            if (level.getEntity(id) instanceof AbstractChestedHorse animal && animal.isAlive() && animal.hasChest()) {
                chests.add(((AbstractHorseAccessor) animal).mca$getInventory());
            }
        }
        return chests;
    }
}
