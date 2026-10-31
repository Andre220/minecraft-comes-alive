package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.Optional;

/**
 * Dealing with a band through one of its bandits, once it will talk: hiring the bandit as a mercenary (from friendly
 * on), and paying tribute for a truce, which keeps the band off a village for some days: its people leave the village's
 * folk and golems alone, and its patrols stay away. Bartering is the expansion's own, over the loot bandits carry.
 */
public final class FactionDeals {
    /** How far from the player a village can be to be named in a truce. */
    private static final int TRUCE_REACH = 2000;
    /** Patrols of a band in truce do not show up this far around the village. */
    private static final int PATROL_MARGIN = 64;

    private FactionDeals() {
    }

    static void bootstrap() {
        // "Come with me": the bandit leaves its band and fights for the player for some days
        Actions.register("bandit_hire", GsonHelper::convertToInt, days -> (villager, player) -> {
            if (!(villager instanceof VillagerEntityMCA bandit) || !Bandits.isBandit(bandit)) {
                return;
            }
            int cost = days <= 3 ? 8 : 16;
            if (!pay(player, cost)) {
                return;
            }
            Factions.clearFaction(bandit);
            bandit.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            bandit.makeMercenary();
            bandit.setDespawnDelay(24000 * days);
        });
        // "Leave that village alone": tribute for a truce over the village nearest the player
        Actions.register("bandit_truce", GsonHelper::convertToString, ignored -> (villager, player) -> {
            int faction = Factions.factionOf(villager.asEntity());
            Optional<FactionData.Faction> band = FactionData.get(player.level()).faction(faction);
            Optional<Village> village = nearestVillage(player);
            Component line;
            if (band.isEmpty()) {
                line = Component.translatable("dialogue.bandit.no_band");
            } else if (village.isEmpty()) {
                line = Component.translatable("dialogue.bandit.truce.no_village");
            } else if (!pay(player, Config.getServerConfig().factionTruceCost)) {
                line = Component.translatable("dialogue.bandit.truce.no_money", Config.getServerConfig().factionTruceCost);
            } else {
                int days = Config.getServerConfig().factionTruceDays;
                FactionData.get(player.level()).addTruce(new FactionData.Truce(faction, village.get().getId(),
                        player.level().getGameTime() + 24000L * days));
                line = Component.translatable("dialogue.bandit.truce.done", Factions.name(band.get()), village.get().getName(), days);
            }
            Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
        });
    }

    private static Optional<Village> nearestVillage(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return VillageManager.get(player.level()).findVillages(v -> v.getBox().getMaxBlockCount() > 1
                        && new BlockPos(v.getCenter()).distSqr(pos) < (double) TRUCE_REACH * TRUCE_REACH)
                .min(Comparator.comparingDouble(v -> new BlockPos(v.getCenter()).distSqr(pos)));
    }

    /** Takes emeralds from the player if they have enough. */
    private static boolean pay(ServerPlayer player, int emeralds) {
        Inventory inventory = player.getInventory();
        if (player.isCreative()) {
            return true;
        }
        if (inventory.countItem(Items.EMERALD) < emeralds) {
            return false;
        }
        for (int i = 0; i < inventory.getContainerSize() && emeralds > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(stack.getCount(), emeralds);
                stack.shrink(taken);
                emeralds -= taken;
            }
        }
        return true;
    }

    private static Optional<Village> truceVillage(ServerLevel level, int faction, BlockPos pos, int margin) {
        if (faction < 0) {
            return Optional.empty();
        }
        FactionData data = FactionData.get(level);
        long now = level.getGameTime();
        VillageManager villages = VillageManager.get(level);
        return data.truces().stream()
                .filter(t -> t.faction() == faction && t.until() > now)
                .map(t -> villages.getOrEmpty(t.village()))
                .flatMap(Optional::stream)
                .filter(v -> v.isWithinBorder(pos, margin))
                .findFirst();
    }

    /** Whether a band is in truce with the village around a place (patrols keep a wider berth). */
    public static boolean truceCovers(ServerLevel level, int faction, BlockPos pos) {
        return truceVillage(level, faction, pos, Village.BORDER_MARGIN + PATROL_MARGIN).isPresent();
    }

    /** Whether a band in truce leaves this one alone: a villager or golem of the village it was paid for. */
    static boolean truceSpares(ServerLevel level, int faction, LivingEntity target) {
        return (target instanceof AbstractVillager || target instanceof IronGolem) && truceVillage(level, faction, target.blockPosition(), Village.BORDER_MARGIN).isPresent();
    }
}
