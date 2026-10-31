package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.addon.roads.faction.FactionData;
import net.conczin.mca.addon.roads.faction.Factions;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/**
 * The dangers and friends of the road: a faction's band may ambush a caravan crossing its land, a player walking
 * along is thanked as its escort when it arrives, and a player who kills caravan people is talked about. Out of
 * sight, a road through a band's land may cost a caravan people and animals.
 */
final class CaravanRoad {
    /** Chance per second, in a band's land, of an ambush once per trip. */
    private static final float AMBUSH_CHANCE = 1.0F / 300.0F;
    /** Chance that a road through a band's land costs an unseen caravan someone. */
    private static final float UNSEEN_LOSS = 0.35F;
    private static final double ESCORT_RANGE = 24.0;
    /** Seconds walked along for it to count as an escort. */
    private static final int ESCORT_SECONDS = 60;
    private static final int ESCORT_HEARTS = 10;
    private static final int RAID_HEARTS = -15;

    private CaravanRoad() {
    }

    /** Each second on the road: counts who walks along, and maybe a band falls on it. */
    static void travelTick(ServerLevel level, CaravanData data, CaravanData.Caravan caravan, VillagerEntityMCA leader) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(leader) < ESCORT_RANGE) {
                caravan.escort.merge(player.getUUID(), 1, Integer::sum);
            }
        }
        if (caravan.ambushed || !Factions.enabled() || level.getRandom().nextFloat() >= AMBUSH_CHANCE) {
            return;
        }
        Optional<FactionData.Faction> band = Factions.territoryAt(FactionData.get(level), leader.blockPosition());
        if (band.isEmpty()) {
            return;
        }
        caravan.ambushed = true;
        data.setDirty();
        ambush(level, band.get(), leader);
    }

    /** Two or three of the band's pillagers turn up nearby and go for the caravan. */
    private static void ambush(ServerLevel level, FactionData.Faction band, VillagerEntityMCA leader) {
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(2);
        double angle = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            int x = leader.getBlockX() + (int) (Math.cos(angle) * 20) + random.nextInt(5) - 2;
            int z = leader.getBlockZ() + (int) (Math.sin(angle) * 20) + random.nextInt(5) - 2;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Pillager pillager = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
            if (pillager == null) {
                continue;
            }
            pillager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            pillager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            pillager.addTag(Factions.TAG_PREFIX + band.id());
            pillager.setTarget(leader);
            level.addFreshEntity(pillager);
        }
        Caravans.announceNear(level, leader, Component.translatable("caravan.ambush", Factions.name(band)));
    }

    /** On arrival: players who walked along most of the way are thanked, in hearts and emeralds. */
    static void rewardEscort(ServerLevel level, CaravanData.Caravan caravan, VillagerEntityMCA leader, Village village) {
        caravan.escort.forEach((id, seconds) -> {
            if (seconds >= ESCORT_SECONDS && level.getPlayerByUUID(id) instanceof ServerPlayer player && player.distanceTo(leader) < ESCORT_RANGE * 2) {
                village.pushHearts(player, ESCORT_HEARTS);
                leader.spawnAtLocation(level, new ItemStack(Items.EMERALD, 3 + level.getRandom().nextInt(4)));
                player.sendSystemMessage(Component.translatable("caravan.escorted", caravan.name()));
            }
        });
        caravan.escort.clear();
        caravan.ambushed = false;
    }

    /** Out of sight: a road crossing a band's land may have cost the caravan one of its people and an animal. */
    static void unseenDangers(ServerLevel level, CaravanData.Caravan caravan, BlockPos from, BlockPos to) {
        if (!Factions.enabled() || caravan.snapshot.size() <= 1) {
            return;
        }
        FactionData data = FactionData.get(level);
        for (int step = 1; step < 8; step++) {
            BlockPos point = new BlockPos(from.getX() + (to.getX() - from.getX()) * step / 8, from.getY(),
                    from.getZ() + (to.getZ() - from.getZ()) * step / 8);
            Optional<FactionData.Faction> band = Factions.territoryAt(data, point);
            if (band.isPresent()) {
                if (level.getRandom().nextFloat() < UNSEEN_LOSS) {
                    caravan.snapshot.removeLast();
                    if (caravan.animalTypes.size() > 1) {
                        caravan.animalTypes.removeLast();
                    }
                    caravan.lostOnRoad = Factions.name(band.get()).getString();
                }
                return;
            }
        }
    }

    /** A player killing one of a caravan's people: the villages on its route hear of it, the bands like it. */
    static void onDeath(ServerLevel level, LivingEntity victim, DamageSource source, CaravanData.Caravan caravan) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (int id : new int[]{caravan.at, caravan.to, caravan.from}) {
            Caravans.villageById(level, id).ifPresent(village -> village.pushHearts(player, RAID_HEARTS));
        }
        if (Factions.enabled()) {
            for (FactionData.Faction band : FactionData.get(level).factions()) {
                Factions.changeReputation(level, player, band.id(), 2);
            }
        }
        player.sendSystemMessage(Component.translatable("caravan.raided", caravan.name()));
    }
}
