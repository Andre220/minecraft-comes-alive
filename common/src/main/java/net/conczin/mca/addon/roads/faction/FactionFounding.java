package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.Optional;
import java.util.UUID;

/**
 * A band of one's own. A member the band counts an ally can found a band under the banner they hold: it takes the
 * player's name, makes camp where they stand, and the band they came from parts with them on good terms. Its leader
 * can then win people over: bandits of bands that trust them, for a few emeralds, and outlaws for nothing. Dialogue
 * actions {@code faction_found} and {@code faction_recruit}, constraints {@code faction_can_found} and
 * {@code faction_recruitable}.
 */
final class FactionFounding {
    /** The kind of a band a player founded; its people are equipped like raiders. */
    static final String FOUNDED = "founded";
    private static final int COST = 32;
    private static final int RECRUIT = 8;
    /** How the band a player came from stands with the one they founded. */
    private static final int PARTING = 40;

    private FactionFounding() {
    }

    static void bootstrap() {
        Actions.register("faction_found", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, found(player, villager.asEntity())));
        Actions.register("faction_recruit", GsonHelper::convertToString, ignored -> (villager, player) -> {
            if (villager instanceof VillagerEntityMCA recruit) {
                say(player, recruit(player, recruit));
            }
        });
        Constraint.register("faction_can_found", (villager, player) -> player != null && canFound(villager.asEntity(), player));
        Constraint.register("faction_recruitable", (villager, player) -> player != null
                && villager instanceof VillagerEntityMCA recruit && recruitable(recruit, player));
    }

    private static void say(ServerPlayer player, Component line) {
        Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
    }

    /** The band a player leads, if any. */
    static Optional<FactionData.Faction> led(ServerLevel level, UUID player) {
        FactionData data = FactionData.get(level);
        return data.factions().stream().filter(f -> player.equals(f.leader())).findFirst();
    }

    /** A member the band counts an ally, leading no band yet. */
    private static boolean canFound(Entity bandit, ServerPlayer player) {
        return FactionMembers.isFellow(bandit, player) && led(player.level(), player.getUUID()).isEmpty()
               && Factions.standing(player.level(), player.getUUID(), Factions.factionOf(bandit)) == Factions.Standing.ALLY;
    }

    private static Component found(ServerPlayer player, Entity bandit) {
        if (!canFound(bandit, player)) {
            return Component.translatable("dialogue.faction.found.not_yet");
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof AbstractBannerBlock banner)) {
            return Component.translatable("dialogue.faction.found.banner");
        }
        if (!FactionDeals.pay(player, COST)) {
            return Component.translatable("dialogue.faction.found.no_money", COST);
        }
        ServerLevel level = player.level();
        FactionData data = FactionData.get(level);
        int old = data.memberOf(player.getUUID());
        BannerPatternLayers patterns = held.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        FactionData.Faction born = data.create(player.getName().getString(), 0, banner.getColor(), patterns, FOUNDED);
        born.leader = player.getUUID();
        FactionWars.meet(level, data, born);
        FactionWars.setRelation(level, data, old, born.id(), PARTING);
        BlockPos camp = player.blockPosition();
        data.addOutpost(new FactionData.Outpost(camp, born.id()));
        data.learn(player.getUUID(), camp);
        data.setMember(player.getUUID(), born.id());
        data.setReputation(player.getUUID(), born.id(), 100);
        Factions.setFaction(bandit, born.id()); // the one who heard it out comes along
        FactionWars.tell(level, Component.translatable("faction.founded", player.getName(), Factions.name(born), camp.getX(), camp.getZ())
                .withStyle(ChatFormatting.GOLD));
        return Component.translatable("dialogue.faction.found.done", Factions.name(born));
    }

    /** Who a leader can win over: an outlaw, or a bandit of another band that trusts them. */
    private static boolean recruitable(VillagerEntityMCA villager, ServerPlayer player) {
        Optional<FactionData.Faction> own = led(player.level(), player.getUUID());
        if (own.isEmpty()) {
            return false;
        }
        if (villager.getProfession() == ProfessionsMCA.OUTLAW) {
            return true;
        }
        return Bandits.isBandit(villager) && Factions.factionOf(villager) != own.get().id() && Bandits.trusts(villager, player);
    }

    private static Component recruit(ServerPlayer player, VillagerEntityMCA villager) {
        Optional<FactionData.Faction> own = led(player.level(), player.getUUID());
        if (own.isEmpty() || !recruitable(villager, player)) {
            return Component.translatable("dialogue.faction.recruit.no");
        }
        boolean outlaw = villager.getProfession() == ProfessionsMCA.OUTLAW;
        if (!outlaw && !FactionDeals.pay(player, RECRUIT)) {
            return Component.translatable("dialogue.faction.recruit.no_money", RECRUIT);
        }
        if (outlaw) {
            Exiles.takeIn(villager, own.get().id());
        } else {
            Factions.setFaction(villager, own.get().id());
            villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        FactionData.get(player.level()).outposts().stream()
                .filter(o -> o.faction() == own.get().id())
                .findFirst()
                .ifPresent(camp -> Bandits.march(villager, camp.pos()));
        return Component.translatable("dialogue.faction.recruit.done", Factions.name(own.get()));
    }
}
