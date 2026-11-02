package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * The way back for an outlaw, three ways: a fine paid to any villager of the village, in emeralds by how much harm was
 * done; time served in the village's prison, longer for more harm, where walking out is an escape that only adds to
 * the debt; or service, some requests of its people done. Any of them lifts the player's standing there back to
 * even, and a member of a band leaves it, which the band takes as betrayal. Dialogue actions {@code village_fine}
 * ("ask" tells the price, "pay" pays it) and {@code village_sentence} ("jail" or "service"), for the constraint
 * {@code village_debt}: the villager's village sees an outlaw in the player; {@code village_prison}: it has a prison;
 * {@code village_sentenced}: the player is serving a sentence there.
 */
public final class Redemption {
    private static final int BASE = 8;
    /** Emeralds per point of reputation below zero. */
    private static final float PER_POINT = 0.2F;
    /** What riding with a band adds to the fine. */
    private static final int BAND = 24;
    private static final int MOST = 96;
    /** Hearts over even the village is left at, so the debt is paid for good. */
    private static final int MARGIN = 10;
    /** A prisoner carries "mca_jail:&lt;village&gt;:&lt;building&gt;:&lt;until&gt;". */
    private static final String JAIL = "mca_jail:";
    /** One serving carries "mca_service:&lt;village&gt;:&lt;requests left&gt;". */
    private static final String SERVICE = "mca_service:";
    /** Ticks in prison per point of reputation below zero, and the least and most. */
    private static final int JAIL_PER_POINT = 30;
    private static final int JAIL_LEAST = 2400;
    private static final int JAIL_MOST = 12000;
    /** Points of reputation below zero a request served pays off. */
    private static final int SERVICE_PER_REQUEST = 25;
    private static final int SERVICE_MOST = 5;
    /** What escaping costs with the village. */
    private static final int ESCAPE = -20;

    private Redemption() {
    }

    static void bootstrap() {
        Constraint.register("village_debt", (villager, player) -> player != null && village(villager)
                .filter(v -> Tasks.getRank(v, player) == Rank.OUTLAW).isPresent());
        Actions.register("village_fine", GsonHelper::convertToString, mode -> (villager, player) -> {
            Optional<Village> village = village(villager);
            if (village.isEmpty()) {
                return;
            }
            int fine = fine(village.get(), player);
            Component line;
            if (!"pay".equals(mode)) {
                line = Component.translatable("dialogue.redemption.price", fine);
            } else if (!FactionDeals.pay(player, fine)) {
                line = Component.translatable("dialogue.redemption.no_money", fine);
            } else {
                settle(village.get(), player);
                line = Component.translatable("dialogue.redemption.paid", village.get().getName());
            }
            Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
        });
        Constraint.register("village_prison", (villager, player) -> village(villager).filter(v -> v.hasBuilding("prison")).isPresent());
        Constraint.register("village_sentenced", (villager, player) -> player != null && sentenced(villager, player));
        Constraint.register("!village_sentenced", (villager, player) -> player == null || !sentenced(villager, player));
        Actions.register("village_sentence", GsonHelper::convertToString, mode -> (villager, player) -> {
            Optional<Village> village = village(villager);
            if (village.isEmpty() || Tasks.getRank(village.get(), player) != Rank.OUTLAW) {
                return;
            }
            Component line = "jail".equals(mode) ? jail(village.get(), player) : serve(village.get(), player);
            Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
        });
    }

    private static boolean sentenced(VillagerLike<?> villager, ServerPlayer player) {
        Optional<String[]> sentence = tag(player, JAIL).or(() -> tag(player, SERVICE));
        return village(villager).filter(v -> sentence.filter(parts -> parts[0].equals(String.valueOf(v.getId()))).isPresent()).isPresent();
    }

    /** The parts of the player's tag with the prefix, if they carry one. */
    private static Optional<String[]> tag(ServerPlayer player, String prefix) {
        return player.entityTags().stream().filter(t -> t.startsWith(prefix)).findFirst()
                .map(t -> t.substring(prefix.length()).split(":"));
    }

    private static void clear(ServerPlayer player, String prefix) {
        player.entityTags().stream().filter(t -> t.startsWith(prefix)).toList().forEach(player::removeTag);
    }

    /** Locked up in the village's prison, for longer the more harm was done. */
    private static Component jail(Village village, ServerPlayer player) {
        Optional<Building> prison = village.getBuildingsOfType("prison").findFirst();
        if (prison.isEmpty()) {
            return Component.translatable("dialogue.redemption.no_prison");
        }
        List<BlockPos> cells = List.copyOf(prison.get().getFloorCells());
        BlockPos cell = cells.isEmpty() ? prison.get().getCenter() : cells.get(player.getRandom().nextInt(cells.size()));
        int debt = Math.max(0, -village.getReputation(player));
        int ticks = Mth.clamp(debt * JAIL_PER_POINT, JAIL_LEAST, JAIL_MOST);
        clear(player, SERVICE);
        clear(player, JAIL);
        player.addTag(JAIL + village.getId() + ":" + prison.get().getId() + ":" + (player.level().getGameTime() + ticks));
        player.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        return Component.translatable("dialogue.redemption.jailed", Mth.ceil(ticks / 1200.0F));
    }

    /** Some requests of the village's people to do, more for more harm. */
    private static Component serve(Village village, ServerPlayer player) {
        int debt = Math.max(0, -village.getReputation(player));
        int requests = Mth.clamp(1 + debt / SERVICE_PER_REQUEST, 1, SERVICE_MOST);
        clear(player, JAIL);
        clear(player, SERVICE);
        player.addTag(SERVICE + village.getId() + ":" + requests);
        return Component.translatable("dialogue.redemption.service", requests);
    }

    /** Once a second: prisoners who served their time go free, and those who walked out escaped. */
    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Optional<String[]> jail = tag(player, JAIL);
            if (jail.isEmpty() || player.isDeadOrDying()) {
                continue;
            }
            try {
                Optional<Village> village = VillageManager.get(level).getOrEmpty(Integer.parseInt(jail.get()[0]));
                Building prison = village.map(v -> v.getBuildings().get(Integer.parseInt(jail.get()[1]))).orElse(null);
                long until = Long.parseLong(jail.get()[2]);
                if (village.isEmpty()) {
                    clear(player, JAIL);
                } else if (prison == null || level.getGameTime() >= until) {
                    clear(player, JAIL);
                    settle(village.get(), player);
                    player.sendSystemMessage(Component.translatable("redemption.released").withStyle(ChatFormatting.GREEN));
                } else if (!prison.containsPositionWithMargin(player.blockPosition(), 2, 2)) {
                    clear(player, JAIL);
                    village.get().pushHearts(player, ESCAPE);
                    player.sendSystemMessage(Component.translatable("redemption.escaped", village.get().getName()).withStyle(ChatFormatting.RED));
                }
            } catch (RuntimeException e) {
                clear(player, JAIL); // a tag that does not read
            }
        }
    }

    /** A request of a villager done: one less to serve, if the player serves the villager's village. */
    public static void requestDone(ServerPlayer player, VillagerEntityMCA villager) {
        Optional<String[]> service = tag(player, SERVICE);
        Optional<Village> village = village(villager);
        if (service.isEmpty() || village.isEmpty() || !service.get()[0].equals(String.valueOf(village.get().getId()))) {
            return;
        }
        int left;
        try {
            left = Integer.parseInt(service.get()[1]) - 1;
        } catch (RuntimeException e) {
            left = 0; // a tag that does not read counts as served
        }
        clear(player, SERVICE);
        if (left <= 0) {
            settle(village.get(), player);
        } else {
            player.addTag(SERVICE + village.get().getId() + ":" + left);
            player.sendSystemMessage(Component.translatable("redemption.service_left", left).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Optional<Village> village(VillagerLike<?> villager) {
        return villager instanceof VillagerEntityMCA mca && !Bandits.isBandit(mca) ? mca.getResidency().getHomeVillage() : Optional.empty();
    }

    static int fine(Village village, ServerPlayer player) {
        int debt = Math.max(0, -village.getReputation(player));
        boolean member = Factions.enabled() && FactionData.get(player.level()).memberOf(player.getUUID()) >= 0;
        return Mth.clamp(BASE + Mth.ceil(debt * PER_POINT) + (member ? BAND : 0), BASE, MOST);
    }

    /** The debt is paid: even with the village again, and out of any band. */
    private static void settle(Village village, ServerPlayer player) {
        int debt = Math.max(0, -village.getReputation(player));
        village.pushHearts(player, debt + MARGIN);
        int band = FactionData.get(player.level()).memberOf(player.getUUID());
        if (band >= 0) {
            FactionMembers.betray(player.level(), player, band);
        }
        player.sendSystemMessage(Component.translatable("redemption.settled", village.getName()).withStyle(ChatFormatting.GREEN));
    }
}
