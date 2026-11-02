package net.conczin.mca.addon.roads.faction;

import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.resources.data.tasks.Task;
import net.conczin.mca.resources.data.tasks.TaskRegistry;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelData;

import java.util.Comparator;
import java.util.Optional;

/**
 * Riding with a band: the outlaw's path. A player the band counts an ally can join it, and gets its banner. From then
 * on its people fight at the player's side (whoever the player strikes or is struck by), come along for nothing, and
 * the bands at war with it count the player an enemy; and every village sees an outlaw, as no village rank can be
 * held while riding with a band. Leaving is betrayal and costs the band's respect; so does paying one's debt to a
 * village ({@link Redemption}). A member who falls below friendly with the band is cast out.
 */
public final class FactionMembers {
    /** What joining costs with the bands at war with the one joined. */
    private static final int ENEMY_OF_ENEMY = 20;
    /** What leaving costs with the band. */
    public static final int BETRAYAL = 40;
    /** How long a band's people stand by a member who struck or was struck, in ticks. */
    private static final int FRESH = 100;
    /** How near an outpost of the band a member can make their home. */
    private static final int LODGE = 48;

    private FactionMembers() {
    }

    static void bootstrap() {
        Actions.register("bandit_join", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, join(player, Factions.factionOf(villager.asEntity()))));
        Actions.register("bandit_leave", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, leave(player)));
        // "Ride with me": a member takes one of the band along for a day, for nothing
        Actions.register("bandit_rally", GsonHelper::convertToString, ignored -> (villager, player) -> {
            if (villager instanceof VillagerEntityMCA bandit && isFellow(bandit, player)) {
                FactionDeals.hire(bandit, 1);
                say(player, Component.translatable("dialogue.bandit.rally"));
            }
        });
        // "Can I stay here?": a member makes the band's outpost their home, where they wake after a fall
        Actions.register("bandit_lodge", GsonHelper::convertToString, ignored -> (villager, player) ->
                say(player, lodge(player, villager.asEntity())));
        Constraint.register("bandit_can_join", (villager, player) -> player != null && canJoin(villager.asEntity(), player));
        Constraint.register("bandit_member", (villager, player) -> player != null && isFellow(villager.asEntity(), player));
        Constraint.register("!bandit_member", (villager, player) -> player == null || !isFellow(villager.asEntity(), player));
        TaskRegistry.register(FreeOfBands.TYPE, json -> new FreeOfBands());
    }

    private static void say(ServerPlayer player, Component line) {
        Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
    }

    /** Whether the player may join this bandit's band: it counts them an ally, and they ride with no band yet. */
    static boolean canJoin(Entity bandit, ServerPlayer player) {
        int faction = Factions.factionOf(bandit);
        return faction >= 0 && Bandits.isBandit(bandit) && FactionData.get(player.level()).memberOf(player.getUUID()) < 0
               && Factions.standing(player.level(), player.getUUID(), faction) == Factions.Standing.ALLY;
    }

    /** Whether the player rides with this bandit's band. */
    static boolean isFellow(Entity bandit, ServerPlayer player) {
        int faction = Factions.factionOf(bandit);
        return faction >= 0 && Bandits.isBandit(bandit) && FactionData.get(player.level()).memberOf(player.getUUID()) == faction;
    }

    private static Component join(ServerPlayer player, int faction) {
        ServerLevel level = player.level();
        FactionData data = FactionData.get(level);
        Optional<FactionData.Faction> band = data.faction(faction);
        if (band.isEmpty()) {
            return Component.translatable("dialogue.bandit.no_band");
        }
        if (data.memberOf(player.getUUID()) >= 0 || Factions.standing(level, player.getUUID(), faction) != Factions.Standing.ALLY) {
            return Component.translatable("dialogue.bandit.join.not_yet");
        }
        data.setMember(player.getUUID(), faction);
        for (FactionData.Faction other : data.factions()) {
            if (FactionWars.atWar(data, faction, other.id())) {
                Factions.changeReputation(level, player, other.id(), -ENEMY_OF_ENEMY);
            }
        }
        if (!player.getInventory().add(Factions.banner(band.get()))) {
            player.drop(Factions.banner(band.get()), false);
        }
        player.sendSystemMessage(Component.translatable("faction.joined", Factions.name(band.get())).withStyle(ChatFormatting.DARK_RED));
        return Component.translatable("dialogue.bandit.join.done", Factions.name(band.get()));
    }

    private static Component leave(ServerPlayer player) {
        ServerLevel level = player.level();
        FactionData data = FactionData.get(level);
        int faction = data.memberOf(player.getUUID());
        if (faction < 0) {
            return Component.translatable("dialogue.bandit.no_band");
        }
        betray(level, player, faction);
        return Component.translatable("dialogue.bandit.leave.done");
    }

    /** A member's home at the band's outpost nearby: they wake there after a fall. */
    private static Component lodge(ServerPlayer player, Entity bandit) {
        if (!isFellow(bandit, player)) {
            return Component.translatable("dialogue.bandit.no_band");
        }
        int faction = Factions.factionOf(bandit);
        BlockPos at = player.blockPosition();
        boolean near = FactionData.get(player.level()).outposts().stream()
                .anyMatch(o -> o.faction() == faction && o.pos().distSqr(at) < LODGE * LODGE);
        if (!near) {
            return Component.translatable("dialogue.bandit.lodge.far");
        }
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(player.level().dimension(), at, player.getYRot(), 0.0F), true), false);
        return Component.translatable("dialogue.bandit.lodge.done");
    }

    /** Walks out on a band: no longer a member, and the band will not forget it. A leader leaves it leaderless. */
    static void betray(ServerLevel level, ServerPlayer player, int faction) {
        FactionData data = FactionData.get(level);
        data.setMember(player.getUUID(), -1);
        data.faction(faction).filter(band -> player.getUUID().equals(band.leader)).ifPresent(band -> {
            band.leader = null;
            data.setDirty();
        });
        data.faction(faction).ifPresent(band -> player.sendSystemMessage(
                Component.translatable("faction.left", Factions.name(band)).withStyle(ChatFormatting.GRAY)));
        Factions.changeReputation(level, player, faction, -BETRAYAL);
    }

    /** A member the band no longer trusts is cast out. */
    static void expel(ServerLevel level, ServerPlayer player, int faction) {
        FactionData data = FactionData.get(level);
        data.setMember(player.getUUID(), -1);
        data.faction(faction).ifPresent(band -> player.sendSystemMessage(
                Component.translatable("faction.expelled", Factions.name(band)).withStyle(ChatFormatting.RED)));
    }

    /** Someone a member of the band near the bandit is fighting: whoever they struck or was struck by, just now. */
    static Optional<LivingEntity> brotherInArmsFoe(ServerLevel level, VillagerEntityMCA bandit, int faction, double range) {
        FactionData data = FactionData.get(level);
        Optional<ServerPlayer> member = level.players().stream()
                .filter(p -> data.memberOf(p.getUUID()) == faction && p.distanceTo(bandit) < range)
                .min(Comparator.comparingDouble(p -> p.distanceTo(bandit)));
        return member.flatMap(FactionMembers::foeOf)
                .filter(foe -> foe != bandit && foe.isAlive() && bandit.hasLineOfSight(foe))
                .filter(foe -> !(foe instanceof Player) || foe == member.get().getLastHurtByMob()) // players only if they struck first
                .filter(foe -> !Bandits.isBandit(foe) || !FactionWars.together(level, bandit, foe));
    }

    private static Optional<LivingEntity> foeOf(ServerPlayer member) {
        int now = member.tickCount;
        LivingEntity struckBy = member.getLastHurtByMob();
        if (struckBy != null && now - member.getLastHurtByMobTimestamp() < FRESH) {
            return Optional.of(struckBy);
        }
        LivingEntity struck = member.getLastHurtMob();
        if (struck != null && now - member.getLastHurtMobTimestamp() < FRESH) {
            return Optional.of(struck);
        }
        return Optional.empty();
    }

    /** A rank requirement of every village: riding with no band. Outlaws hold no rank. */
    static final class FreeOfBands extends Task {
        static final String TYPE = "faction_free";

        FreeOfBands() {
            super(TYPE);
        }

        @Override
        public boolean isCompleted(Village village, ServerPlayer player) {
            return !Factions.enabled() || FactionData.get(player.level()).memberOf(player.getUUID()) < 0;
        }

        @Override
        public JsonObject serialize() {
            JsonObject json = new JsonObject();
            json.addProperty("type", TYPE);
            return json;
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public MutableComponent getTranslatable() {
            return Component.translatable("task.faction_free");
        }
    }
}
