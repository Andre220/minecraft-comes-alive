package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Raiding with the band: a member asks a bandit to fall on the village nearest them, and a party of the band marches
 * there with them, the bandit at its head. In the village the party goes for its folk and golems; each one it brings
 * down is the member's share of the loot, in emeralds, and earns the band's respect. The village knows who brought
 * them. After a while the party goes home to the nearest outpost. One raid a day; never on a village the band was
 * paid a truce for. Dialogue action {@code bandit_raid}.
 */
final class FactionRaids {
    /** A raider carries "mca_raid:&lt;village&gt;:&lt;until&gt;:&lt;player&gt;". */
    private static final String RAID = "mca_raid:";
    /** The player carries "mca_raided:&lt;game time&gt;" of their last raid. */
    private static final String RAIDED = "mca_raided:";
    private static final int PARTY = 3;
    private static final double REACH = 320.0;
    private static final long LENGTH = 6000;
    private static final long REST = 24000;
    private static final double PREY = 16.0;
    /** What the village thinks of whoever brought the band on it. */
    private static final int BLAME = -15;

    private record Raid(int village, long until, UUID player) {
    }

    private FactionRaids() {
    }

    static void bootstrap() {
        Actions.register("bandit_raid", GsonHelper::convertToString, ignored -> (villager, player) -> {
            if (villager instanceof VillagerEntityMCA bandit) {
                Network.sendToPlayer(new InteractionDialogueQuestionResponse(raid(bandit, player), false), player);
            }
        });
    }

    private static Component raid(VillagerEntityMCA bandit, ServerPlayer player) {
        if (!FactionMembers.isFellow(bandit, player)) {
            return Component.translatable("dialogue.bandit.no_band");
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        long last = player.entityTags().stream().filter(t -> t.startsWith(RAIDED))
                .mapToLong(t -> Long.parseLong(t.substring(RAIDED.length()))).max().orElse(-REST);
        if (now - last < REST) {
            return Component.translatable("dialogue.bandit.raid.rest");
        }
        int faction = Factions.factionOf(bandit);
        BlockPos pos = player.blockPosition();
        Optional<Village> target = VillageManager.get(level).findVillages(v -> v.getBox().getMaxBlockCount() > 1
                        && new BlockPos(v.getCenter()).distSqr(pos) < REACH * REACH)
                .min(Comparator.comparingDouble(v -> new BlockPos(v.getCenter()).distSqr(pos)));
        if (target.isEmpty()) {
            return Component.translatable("dialogue.bandit.raid.no_village");
        }
        Village village = target.get();
        BlockPos center = new BlockPos(village.getCenter());
        if (FactionDeals.truceCovers(level, faction, center)) {
            return Component.translatable("dialogue.bandit.raid.truce", village.getName());
        }
        player.entityTags().stream().filter(t -> t.startsWith(RAIDED)).toList().forEach(player::removeTag);
        player.addTag(RAIDED + now);
        String tag = RAID + village.getId() + ":" + (now + LENGTH) + ":" + player.getUUID();
        enlist(bandit, tag, center);
        RandomSource random = level.getRandom();
        for (int i = 0; i < PARTY; i++) {
            Bandits.spawn(level, bandit.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), faction, false)
                    .ifPresent(raider -> enlist(raider, tag, center));
        }
        village.pushHearts(player, BLAME);
        return Component.translatable("dialogue.bandit.raid.done", village.getName());
    }

    private static void enlist(VillagerEntityMCA raider, String tag, BlockPos target) {
        clear(raider);
        raider.addTag(tag);
        Bandits.march(raider, target);
    }

    private static void clear(Entity entity) {
        entity.entityTags().stream().filter(t -> t.startsWith(RAID)).toList().forEach(entity::removeTag);
    }

    private static Optional<Raid> raidOf(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(RAID)) {
                String[] parts = tag.substring(RAID.length()).split(":");
                try {
                    return Optional.of(new Raid(Integer.parseInt(parts[0]), Long.parseLong(parts[1]), UUID.fromString(parts[2])));
                } catch (RuntimeException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** What a raider goes for in the village: its folk and golems in sight. A raid over, the raider goes home. */
    static Optional<LivingEntity> prey(ServerLevel level, VillagerEntityMCA raider, int faction) {
        Optional<Raid> raid = raidOf(raider);
        if (raid.isEmpty()) {
            return Optional.empty();
        }
        if (level.getGameTime() > raid.get().until()) {
            clear(raider);
            BlockPos at = raider.blockPosition();
            FactionData.get(level).outposts().stream()
                    .filter(o -> o.faction() == faction)
                    .min(Comparator.comparingDouble(o -> o.pos().distSqr(at)))
                    .ifPresent(home -> Bandits.march(raider, home.pos()));
            return Optional.empty();
        }
        Optional<Village> village = VillageManager.get(level).getOrEmpty(raid.get().village());
        if (village.isEmpty() || !village.get().isWithinBorder(raider.blockPosition(), Village.BORDER_MARGIN)) {
            return Optional.empty();
        }
        return level.getEntitiesOfClass(LivingEntity.class, raider.getBoundingBox().inflate(PREY),
                        e -> e.isAlive() && !e.isBaby() && (e instanceof IronGolem || e instanceof AbstractVillager && !Bandits.isBandit(e)))
                .stream()
                .filter(raider::hasLineOfSight)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(raider)));
    }

    /** A raider brought someone of the village down: the member's share, and the band's respect. */
    static void onKill(ServerLevel level, LivingEntity killer, LivingEntity victim) {
        Optional<Raid> raid = raidOf(killer);
        if (raid.isEmpty() || !(victim instanceof IronGolem || victim instanceof AbstractVillager && !Bandits.isBandit(victim))) {
            return;
        }
        if (level.getServer().getPlayerList().getPlayer(raid.get().player()) instanceof ServerPlayer player) {
            ItemStack share = new ItemStack(Items.EMERALD, victim instanceof IronGolem ? 4 : 2);
            if (!player.getInventory().add(share)) {
                player.drop(share, false);
            }
            player.sendSystemMessage(Component.translatable("faction.raid_share", victim.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            Factions.changeReputation(level, player, Factions.factionOf(killer), 2);
        }
    }
}
