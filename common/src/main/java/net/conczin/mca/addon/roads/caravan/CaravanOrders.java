package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.MCA;
import net.conczin.mca.addon.roads.faction.FactionDeals;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;
import java.util.UUID;

/**
 * Orders: a player shows a caravan what they hold and asks for more of it. The caravan brings a stack of it from the
 * road and has it ready once it reached another village, where the player pays and collects. Common goods are cheap,
 * rare ones dear; epic ones, spawn eggs and the {@code mca:caravan_unorderable} tag cannot be had. One order per player
 * per caravan. Dialogue actions {@code caravan_order} and {@code caravan_pickup}, constraint {@code caravan_ordered}.
 */
final class CaravanOrders {
    private static final TagKey<Item> UNORDERABLE = TagKey.create(Registries.ITEM, MCA.locate("caravan_unorderable"));
    /** The most items of one order. */
    private static final int MOST = 32;
    private static final int FEE = 2;
    private static final int DEAREST = 64;

    private CaravanOrders() {
    }

    static void bootstrap() {
        Actions.register("caravan_order", GsonHelper::convertToString, ignored -> (villager, player) -> {
            if (villager instanceof VillagerEntityMCA member) {
                say(player, order(player.level(), member, player));
            }
        });
        Actions.register("caravan_pickup", GsonHelper::convertToString, ignored -> (villager, player) -> {
            if (villager instanceof VillagerEntityMCA member) {
                say(player, pickup(player.level(), member, player));
            }
        });
        Constraint.register("caravan_ordered", (villager, player) -> player != null && hasOrder(villager, player));
        Constraint.register("!caravan_ordered", (villager, player) -> player == null || !hasOrder(villager, player));
    }

    private static void say(ServerPlayer player, Component line) {
        Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
    }

    private static boolean hasOrder(VillagerLike<?> villager, ServerPlayer player) {
        return villager instanceof VillagerEntityMCA member
               && Caravans.caravanOf(player.level(), member).flatMap(c -> orderOf(c, player.getUUID())).isPresent();
    }

    private static Optional<CaravanData.Order> orderOf(CaravanData.Caravan caravan, UUID player) {
        return caravan.orders.stream().filter(o -> o.player.equals(player)).findFirst();
    }

    /** What a stack of this costs to order, in emeralds; -1 for what cannot be had. */
    static int price(ItemStack stack, int count) {
        if (stack.isEmpty() || stack.is(UNORDERABLE) || stack.getItem() instanceof SpawnEggItem || stack.is(Items.EMERALD)) {
            return -1;
        }
        Rarity rarity = stack.getRarity();
        float each = switch (rarity) {
            case COMMON -> 0.125F;
            case UNCOMMON -> 0.5F;
            case RARE -> 2.0F;
            case EPIC -> -1.0F;
        };
        return each < 0 ? -1 : Math.min(DEAREST, FEE + Mth.ceil(count * each));
    }

    private static Component order(ServerLevel level, VillagerEntityMCA member, ServerPlayer player) {
        Optional<CaravanData.Caravan> found = Caravans.caravanOf(level, member);
        if (found.isEmpty()) {
            return Component.translatable("dialogue.caravan.from.none");
        }
        CaravanData.Caravan caravan = found.get();
        if (orderOf(caravan, player.getUUID()).isPresent()) {
            return Component.translatable("dialogue.caravan.order.already");
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return Component.translatable("dialogue.caravan.order.show");
        }
        int count = Math.min(MOST, held.getMaxStackSize());
        int price = price(held, count);
        if (price < 0) {
            return Component.translatable("dialogue.caravan.order.cannot", held.getHoverName());
        }
        caravan.orders.add(new CaravanData.Order(player.getUUID(), BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), count, price, caravan.at));
        CaravanData.get(level).setDirty();
        return Component.translatable("dialogue.caravan.order.done", count, held.getHoverName(), price);
    }

    private static Component pickup(ServerLevel level, VillagerEntityMCA member, ServerPlayer player) {
        Optional<CaravanData.Caravan> found = Caravans.caravanOf(level, member);
        Optional<CaravanData.Order> order = found.flatMap(c -> orderOf(c, player.getUUID()));
        if (order.isEmpty()) {
            return Component.translatable("dialogue.caravan.pickup.none");
        }
        if (!order.get().ready) {
            return Component.translatable("dialogue.caravan.pickup.not_yet");
        }
        if (!FactionDeals.pay(player, order.get().price)) {
            return Component.translatable("dialogue.caravan.pickup.no_money", order.get().price);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(order.get().item));
        ItemStack goods = new ItemStack(item, order.get().count);
        Component name = goods.getHoverName();
        if (!player.getInventory().add(goods)) {
            player.drop(goods, false);
        }
        found.get().orders.remove(order.get());
        CaravanData.get(level).setDirty();
        return Component.translatable("dialogue.caravan.pickup.done", order.get().count, name);
    }

    /** At a village: the orders asked for elsewhere are ready, and whoever asked is told. */
    static void arrive(ServerLevel level, CaravanData.Caravan caravan, Village village) {
        for (CaravanData.Order order : caravan.orders) {
            if (order.ready || order.askedAt == village.getId()) {
                continue;
            }
            order.ready = true;
            if (level.getServer().getPlayerList().getPlayer(order.player) instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("caravan.order_ready", caravan.name(), village.getName())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
