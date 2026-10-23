package net.conczin.mca.expansion.barter;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.ProductionRecipes;
import net.conczin.mca.expansion.requests.RequestPool;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Haggling with any villager over what it carries, whatever the friendship: it sells its belongings
 * for emeralds (cheaper for friends) and, with emeralds of its own, buys food and what its
 * workshop needs. Anyone may also ask what it carries.
 */
public final class Barter {
    /** Food a villager keeps for itself instead of selling. */
    private static final int KEEP_FOOD = 4;
    private static final int MAX_OFFERS = 24;

    private Barter() {
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerBarter;
    }

    /** Opens the trading screen with offers made from the villager's inventory. */
    public static void open(ServerPlayer player, VillagerEntityMCA villager) {
        MerchantOffers offers = offers(player, villager);
        if (offers.isEmpty()) {
            villager.sendChatMessage(Component.translatable("barter.nothing"), player);
            return;
        }
        villager.getInteractions().stopInteracting();
        new BarterMerchant(villager, offers).openTradingScreen(player, villager.getName(), 0);
    }

    /** The villager tells what it carries. */
    public static void describe(ServerPlayer player, VillagerEntityMCA villager) {
        Map<Item, Integer> carried = carried(villager.getInventory());
        if (carried.isEmpty()) {
            villager.sendChatMessage(Component.translatable("barter.carries_nothing"), player);
            return;
        }
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<Item, Integer> entry : carried.entrySet()) {
            if (!first) {
                list.append(", ");
            }
            list.append(entry.getValue() + "× ").append(new ItemStack(entry.getKey()).getHoverName());
            first = false;
        }
        player.sendSystemMessage(Component.translatable("barter.carries", villager.getName(), list));
    }

    public static MerchantOffers offers(Player player, VillagerEntityMCA villager) {
        MerchantOffers offers = new MerchantOffers();
        Map<Item, Integer> carried = carried(villager.getInventory());
        double markup = markup(player, villager);
        int emeraldWorth = Math.max(1, RequestPool.worth(Items.EMERALD));

        // what it sells
        int food = 0;
        for (Map.Entry<Item, Integer> entry : carried.entrySet()) {
            Item item = entry.getKey();
            int available = entry.getValue();
            if (item == Items.EMERALD) {
                continue;
            }
            if (VillagerEntityMCA.isEdible(new ItemStack(item))) {
                int keep = Math.max(0, KEEP_FOOD - food);
                food += Math.min(available, keep);
                available -= Math.min(available, keep);
            }
            if (available <= 0 || offers.size() >= MAX_OFFERS) {
                continue;
            }
            double each = worthEach(item);
            int batch = Math.min(available, Math.min(new ItemStack(item).getMaxStackSize(), Math.max(1, Mth.ceil(emeraldWorth / each))));
            int price = Math.max(1, Mth.ceil(batch * each * markup / emeraldWorth));
            if (price > 64) {
                continue;
            }
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(item, batch), available / batch, 0, 0.0F));
        }

        // what it buys, paying with its own emeralds
        int emeralds = carried.getOrDefault(Items.EMERALD, 0);
        if (emeralds > 0) {
            Map<Item, Boolean> wanted = new LinkedHashMap<>();
            ProductionRecipes.inputsFor(villager.getProfession(), null).forEach(item -> wanted.put(item, true));
            if (food < KEEP_FOOD) {
                wanted.put(Items.BREAD, true);
                wanted.put(Items.BAKED_POTATO, true);
            }
            for (Item item : wanted.keySet()) {
                if (offers.size() >= MAX_OFFERS) {
                    break;
                }
                double each = worthEach(item);
                // it pays a bit less than it sells for, more to friends
                int batch = Math.min(new ItemStack(item).getMaxStackSize(), Math.max(1, Mth.ceil(emeraldWorth * markup / each)));
                offers.add(new MerchantOffer(new ItemCost(item, batch), new ItemStack(Items.EMERALD, 1), emeralds, 0, 0.0F));
            }
        }
        return offers;
    }

    /** Strangers pay half again, good friends the fair price; those it dislikes double. */
    private static double markup(Player player, VillagerEntityMCA villager) {
        int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        if (hearts < 0) {
            return 2.0;
        }
        return 1.5 - 0.5 * Math.min(1.0, hearts / 100.0);
    }

    private static double worthEach(Item item) {
        int sixteen = RequestPool.worth(new ItemStack(item, 16));
        return Math.max(1.0 / 16.0, sixteen / 16.0);
    }

    private static Map<Item, Integer> carried(SimpleContainer inventory) {
        Map<Item, Integer> carried = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
                carried.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return carried;
    }
}
