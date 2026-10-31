package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bands fight bands. Each pair of factions stands somewhere from war to alliance, and that moves day by day: bands
 * whose lands touch grow hostile, the rest drift back to a wary peace, and killings between them set them at each
 * other's throats. Once a day a band at war may strike an outpost of its enemy: near a player the strike is real, a
 * war party walking in; far from everyone it is settled by strength, and the loser may lose the outpost.
 */
public final class FactionWars {
    /** Below this, two bands are at war. */
    public static final int WAR = -40;
    /** Where relations drift to when nothing happens. */
    private static final int REST = -10;
    /** How far a band reaches for an enemy outpost to strike. */
    private static final double REACH = 1500.0;
    /** Within this of a player, a strike on an outpost is fought for real. */
    private static final double WITNESS = 160.0;
    private static final int PARTY = 4;

    private FactionWars() {
    }

    public static boolean enabled() {
        return Factions.enabled() && Config.getServerConfig().factionWars;
    }

    public static boolean atWar(FactionData data, int a, int b) {
        return a >= 0 && b >= 0 && a != b && data.relation(a, b) < WAR;
    }

    /** A new faction meets the others: most are wary of it, some already hate it. */
    static void meet(ServerLevel level, FactionData data, FactionData.Faction faction) {
        RandomSource random = level.getRandom();
        for (FactionData.Faction other : data.factions()) {
            if (other.id() != faction.id()) {
                data.setRelation(faction.id(), other.id(), -70 + random.nextInt(90));
            }
        }
    }

    /** Once a day: relations move, wars start and end, and bands at war strike. */
    static void day(ServerLevel level, FactionData data) {
        if (!enabled()) {
            return;
        }
        RandomSource random = level.getRandom();
        List<FactionData.Faction> factions = data.factions();
        double touch = Config.getServerConfig().factionTerritoryRadius * 2.0;
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                int a = factions.get(i).id();
                int b = factions.get(j).id();
                int before = data.relation(a, b);
                int change = before < REST ? 2 : before > REST ? -2 : 0;
                if (borders(data, a, b, touch)) {
                    change -= 5;
                }
                if (random.nextFloat() < 0.1F) {
                    change += random.nextBoolean() ? -15 : 10; // a quarrel over loot, or a shared enemy
                }
                setRelation(level, data, a, b, before + change);
                if (atWar(data, a, b) && random.nextFloat() < 0.35F) {
                    boolean first = random.nextBoolean();
                    strike(level, data, first ? a : b, first ? b : a);
                }
            }
        }
    }

    private static boolean borders(FactionData data, int a, int b, double distance) {
        for (FactionData.Outpost one : data.outposts()) {
            if (one.faction() != a) {
                continue;
            }
            for (FactionData.Outpost other : data.outposts()) {
                if (other.faction() == b && one.pos().distSqr(other.pos()) < distance * distance) {
                    return true;
                }
            }
        }
        return false;
    }

    static void setRelation(ServerLevel level, FactionData data, int a, int b, int value) {
        boolean wasAtWar = atWar(data, a, b);
        data.setRelation(a, b, Math.clamp(value, -100, 100));
        boolean isAtWar = atWar(data, a, b);
        if (wasAtWar != isAtWar) {
            Optional<FactionData.Faction> one = data.faction(a);
            Optional<FactionData.Faction> other = data.faction(b);
            if (one.isPresent() && other.isPresent()) {
                tell(level, Component.translatable(isAtWar ? "faction.war" : "faction.peace", Factions.name(one.get()), Factions.name(other.get())));
            }
        }
    }

    /** A killing between two bands sours them. */
    static void onKill(ServerLevel level, int killer, int victim) {
        if (enabled() && killer >= 0 && victim >= 0 && killer != victim) {
            FactionData data = FactionData.get(level);
            setRelation(level, data, killer, victim, data.relation(killer, victim) - 6);
        }
    }

    /** An outpost of the defender, the nearest to the attacker's own, is struck. */
    private static void strike(ServerLevel level, FactionData data, int attacker, int defender) {
        List<FactionData.Outpost> own = data.outposts().stream().filter(o -> o.faction() == attacker).toList();
        Optional<FactionData.Outpost> target = data.outposts().stream()
                .filter(o -> o.faction() == defender)
                .filter(o -> own.stream().anyMatch(home -> home.pos().distSqr(o.pos()) < REACH * REACH))
                .min(Comparator.comparingDouble(o -> own.stream().mapToDouble(home -> home.pos().distSqr(o.pos())).min().orElse(Double.MAX_VALUE)));
        if (target.isEmpty()) {
            return;
        }
        FactionData.Outpost outpost = target.get();
        boolean witnessed = level.players().stream().anyMatch(p -> p.blockPosition().distSqr(outpost.pos()) < WITNESS * WITNESS);
        if (witnessed) {
            sendParty(level, attacker, outpost.pos());
            return;
        }
        RandomSource random = level.getRandom();
        long attack = data.outpostCount(attacker) * 3 + random.nextInt(9);
        long defence = data.outpostCount(defender) * 3 + random.nextInt(9) + 2; // walls help
        if (attack > defence) {
            data.setOwner(outpost, attacker);
            data.faction(attacker).ifPresent(a -> data.faction(defender).ifPresent(d ->
                    tell(level, Component.translatable("faction.outpost_taken", Factions.name(a), Factions.name(d)))));
        }
    }

    /** A war party of the attacker walks toward the outpost from its edge. */
    private static void sendParty(ServerLevel level, int attacker, BlockPos outpost) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = 40;
        int x = outpost.getX() + (int) (Math.cos(angle) * distance);
        int z = outpost.getZ() + (int) (Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return;
        }
        BlockPos start = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int i = 0; i < PARTY; i++) {
            Bandits.spawn(level, start.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), attacker, i == 0)
                    .ifPresent(bandit -> Bandits.march(bandit, outpost));
        }
        FactionData.get(level).faction(attacker).ifPresent(a -> level.players().stream()
                .filter(p -> p.blockPosition().distSqr(outpost) < WITNESS * WITNESS)
                .forEach(p -> p.sendSystemMessage(Component.translatable("faction.war_party", Factions.name(a)), true)));
    }

    /** The nearest member of a band at war with the given one that the looker can see, if any. */
    static Optional<LivingEntity> enemyNear(ServerLevel level, LivingEntity looker, int faction, double range) {
        if (!enabled()) {
            return Optional.empty();
        }
        FactionData data = FactionData.get(level);
        return level.getEntitiesOfClass(LivingEntity.class, looker.getBoundingBox().inflate(range),
                        e -> e != looker && e.isAlive() && (e instanceof Raider || Bandits.isBandit(e)))
                .stream()
                .filter(e -> atWar(data, faction, Factions.factionOf(e)) && looker.hasLineOfSight(e))
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(looker)));
    }

    /** Whether two members of bands stand together: the same band, or bands not at war. */
    static boolean together(ServerLevel level, Entity a, Entity b) {
        int one = Factions.factionOf(a);
        int other = Factions.factionOf(b);
        return one >= 0 && other >= 0 && !atWar(FactionData.get(level), one, other);
    }

    private static void tell(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(message);
        }
    }
}
