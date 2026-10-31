package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.conczin.mca.resources.API;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Factions, inspired by RimWorld: the pillagers are not one horde but several bands, each with a name, a banner and
 * outposts of its own, and each player stands somewhere with each band. Raiding villages wins their respect, killing
 * their people loses it; a band that respects a player leaves them alone. Pillager outposts are claimed as players
 * find them: by the band nearby, or by a new band while there is room for one, and fly its banner from then on.
 */
public final class Factions {
    /** Illagers carry "mca_faction:<id>" (or -1 for none) once their band is known. */
    public static final String TAG_PREFIX = "mca_faction:";
    private static final Identifier OUTPOST = Identifier.withDefaultNamespace("pillager_outpost");
    private static final int TEMPLATES = 6;
    private static final int SEARCH_CHUNKS = 6;
    /** Outposts this close to another one of a band join that band. */
    private static final double SPREAD = 1200.0;
    private static final int MIN = -100;
    private static final int MAX = 100;

    private static final Map<UUID, Integer> LAST_TERRITORY = new HashMap<>();

    public enum Standing {
        HOSTILE(ChatFormatting.RED), NEUTRAL(ChatFormatting.GRAY), FRIENDLY(ChatFormatting.GREEN), ALLY(ChatFormatting.AQUA);

        private final ChatFormatting color;

        Standing(ChatFormatting color) {
            this.color = color;
        }

        public static Standing of(int reputation) {
            return reputation < 0 ? HOSTILE : reputation < 30 ? NEUTRAL : reputation < 70 ? FRIENDLY : ALLY;
        }

        public Component getName() {
            return Component.translatable("faction.standing." + name().toLowerCase()).withStyle(color);
        }
    }

    private Factions() {
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerFactions;
    }

    public static Component name(FactionData.Faction faction) {
        return Component.translatable("faction.name." + faction.template, faction.place);
    }

    /** The banner of a faction, as an item. */
    public static ItemStack banner(FactionData.Faction faction) {
        ItemStack banner = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(faction.base.getSerializedName() + "_banner")));
        banner.set(DataComponents.BANNER_PATTERNS, faction.patterns);
        return banner;
    }

    // ---------------------------------------------------------------- the world

    /** From the level tick: makes the first factions, claims outposts players come near, tells who holds the land. */
    public static void levelTick(ServerLevel level) {
        if (!enabled() || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        long time = level.getGameTime();
        if (time % 400 == 0) {
            FactionData data = FactionData.get(level);
            ensure(level, data);
            for (ServerPlayer player : level.players()) {
                discover(level, data, player);
            }
            repaint(level, data);
        }
        if (time % 24000 == 0) {
            FactionData data = FactionData.get(level);
            data.dropExpiredTruces(time);
            FactionWars.day(level, data);
        }
        if (time % 40 == 0) {
            FactionData data = FactionData.get(level);
            for (ServerPlayer player : level.players()) {
                announceTerritory(level, data, player);
            }
        }
    }

    private static void ensure(ServerLevel level, FactionData data) {
        int count = Config.getServerConfig().factionCount;
        while (data.factions().size() < count) {
            create(level, data);
        }
    }

    private static FactionData.Faction create(ServerLevel level, FactionData data) {
        RandomSource random = level.getRandom();
        String place = API.getVillagePool().pickVillageName("village");
        DyeColor base = DyeColor.values()[random.nextInt(DyeColor.values().length)];
        List<Holder.Reference<BannerPattern>> patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements().toList();
        BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count && !patterns.isEmpty(); i++) {
            DyeColor color;
            do {
                color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
            } while (color == base);
            layers.add(patterns.get(random.nextInt(patterns.size())), color);
        }
        FactionData.Faction faction = data.create(place, random.nextInt(TEMPLATES), base, layers.build());
        FactionWars.meet(level, data, faction);
        return faction;
    }

    /** Outposts taken in a war get the new band's banners once someone is near enough to load them. */
    private static void repaint(ServerLevel level, FactionData data) {
        for (FactionData.Outpost outpost : data.outposts()) {
            if (data.repaint().contains(outpost.pos().asLong()) && level.isLoaded(outpost.pos())
                && level.players().stream().anyMatch(p -> horizontal(p.blockPosition(), outpost.pos()) < 96 * 96)) {
                data.faction(outpost.faction()).ifPresent(faction -> flyBanners(level, faction, outpost.pos()));
                data.repainted(outpost.pos());
            }
        }
    }

    /** A pillager outpost near the player that no faction holds yet gets one, and its banners. */
    private static void discover(ServerLevel level, FactionData data, ServerPlayer player) {
        Optional<BlockPos> found = WorldUtils.getClosestStructurePosition(level, player.blockPosition(), OUTPOST, SEARCH_CHUNKS);
        if (found.isEmpty()) {
            return;
        }
        BlockPos pos = found.get();
        if (data.outposts().stream().anyMatch(o -> horizontal(o.pos(), pos) < 48 * 48)) {
            return;
        }
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockPos ground = new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()), pos.getZ());
        FactionData.Faction faction = claimant(level, data, ground);
        data.addOutpost(new FactionData.Outpost(ground, faction.id()));
        data.learn(player.getUUID(), ground);
        flyBanners(level, faction, ground);
    }

    /** Who takes a newly found outpost: the band holding one nearby, a new band while there is room, else the smallest. */
    private static FactionData.Faction claimant(ServerLevel level, FactionData data, BlockPos pos) {
        Optional<FactionData.Outpost> near = data.outposts().stream()
                .filter(o -> horizontal(o.pos(), pos) < SPREAD * SPREAD)
                .min(Comparator.comparingDouble(o -> horizontal(o.pos(), pos)));
        if (near.isPresent()) {
            return data.faction(near.get().faction()).orElseGet(() -> create(level, data));
        }
        List<FactionData.Faction> unsettled = data.factions().stream().filter(f -> data.outpostCount(f.id()) == 0).toList();
        if (!unsettled.isEmpty()) {
            return unsettled.get(level.getRandom().nextInt(unsettled.size()));
        }
        if (data.factions().size() < Config.getServerConfig().factionMax) {
            return create(level, data); // an outpost far from every band starts a new one
        }
        return data.factions().stream().min(Comparator.comparingLong(f -> data.outpostCount(f.id()))).orElseGet(() -> create(level, data));
    }

    /** Swaps the outpost's banners for the faction's. */
    private static void flyBanners(ServerLevel level, FactionData.Faction faction, BlockPos ground) {
        ItemStack banner = banner(faction);
        for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-16, -6, -16), ground.offset(16, 30, 16))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractBannerBlock)) {
                continue;
            }
            Block colored = colored(state.getBlock(), faction.base);
            BlockState replaced = colored.withPropertiesOf(state);
            level.setBlock(pos, replaced, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof BannerBlockEntity entity) {
                entity.applyComponentsFromItemStack(banner);
                entity.setChanged();
                level.sendBlockUpdated(pos, replaced, replaced, Block.UPDATE_ALL);
            }
        }
    }

    /** The banner block of a color, standing or on a wall like the one it replaces. */
    private static Block colored(Block block, DyeColor color) {
        String suffix = block instanceof WallBannerBlock ? "_wall_banner" : "_banner";
        return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(color.getSerializedName() + suffix));
    }

    /** Tells a player whose land they walk into. */
    private static void announceTerritory(ServerLevel level, FactionData data, ServerPlayer player) {
        double radius = Config.getServerConfig().factionTerritoryRadius;
        data.outposts().stream()
                .filter(o -> horizontal(o.pos(), player.blockPosition()) < radius * radius)
                .forEach(o -> data.learn(player.getUUID(), o.pos())); // walking its land shows it on the map
        int here = territoryAt(data, player.blockPosition()).map(FactionData.Faction::id).orElse(-1);
        Integer before = LAST_TERRITORY.put(player.getUUID(), here);
        if (before != null && before == here || here < 0) {
            return;
        }
        data.faction(here).ifPresent(faction -> player.sendSystemMessage(Component.translatable("faction.territory",
                name(faction), standing(level, player.getUUID(), faction.id()).getName()), true));
    }

    /** The faction whose land a position is in: within the territory radius of one of its outposts. */
    public static Optional<FactionData.Faction> territoryAt(FactionData data, BlockPos pos) {
        double radius = Config.getServerConfig().factionTerritoryRadius;
        return data.outposts().stream()
                .filter(o -> horizontal(o.pos(), pos) < radius * radius)
                .min(Comparator.comparingDouble(o -> horizontal(o.pos(), pos)))
                .flatMap(o -> data.faction(o.faction()));
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // ---------------------------------------------------------------- members and reputation

    /** The faction of an illager: the land it stands in, else the nearest band's within reach; -1 for none. */
    public static int factionOf(Entity entity) {
        if (!(entity instanceof Raider) && !Bandits.isBandit(entity) || !(entity.level() instanceof ServerLevel level)) {
            return -1;
        }
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                try {
                    return Integer.parseInt(tag.substring(TAG_PREFIX.length()));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        FactionData data = FactionData.get(level);
        int faction = territoryAt(data, entity.blockPosition()).map(FactionData.Faction::id)
                .or(() -> data.outposts().stream()
                        .filter(o -> horizontal(o.pos(), entity.blockPosition()) < SPREAD * SPREAD)
                        .min(Comparator.comparingDouble(o -> horizontal(o.pos(), entity.blockPosition())))
                        .map(FactionData.Outpost::faction))
                .orElse(-1);
        entity.addTag(TAG_PREFIX + faction);
        return faction;
    }

    /** Takes an illager or bandit out of its band, as when hired away. */
    public static void clearFaction(Entity entity) {
        entity.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).toList().forEach(entity::removeTag);
        entity.addTag(TAG_PREFIX + -1);
    }

    public static int reputation(ServerLevel level, UUID player, int faction) {
        return FactionData.get(level).reputation(player, faction, Config.getServerConfig().factionStartReputation);
    }

    public static Standing standing(ServerLevel level, UUID player, int faction) {
        return Standing.of(reputation(level, player, faction));
    }

    public static void changeReputation(ServerLevel level, ServerPlayer player, int faction, int amount) {
        FactionData data = FactionData.get(level);
        if (data.faction(faction).isEmpty() || amount == 0) {
            return;
        }
        int before = reputation(level, player.getUUID(), faction);
        int after = Math.clamp(before + amount, MIN, MAX);
        data.setReputation(player.getUUID(), faction, after);
        if (Standing.of(before) != Standing.of(after)) {
            data.faction(faction).ifPresent(f -> player.sendSystemMessage(Component.translatable("faction.standing_changed",
                    name(f), Standing.of(after).getName())));
        }
    }

    /**
     * Whom a band's people leave alone: a player it respects, their own and bands not at war with theirs, and the folk
     * of a village it was paid a truce for; unless that one hurt them first.
     */
    public static boolean spares(LivingEntity attacker, LivingEntity target) {
        if (!enabled() || !(attacker.level() instanceof ServerLevel level) || attacker.getLastHurtByMob() == target) {
            return false;
        }
        int faction = factionOf(attacker);
        if (faction < 0) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            return standing(level, player.getUUID(), faction) != Standing.HOSTILE;
        }
        if (target instanceof Raider || Bandits.isBandit(target)) {
            return FactionWars.together(level, attacker, target);
        }
        return FactionDeals.truceSpares(level, faction, target);
    }

    /** Deaths by a player's hand: a band's people cost its respect, villagers and golems win the respect of all bands. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!enabled() || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (source.getEntity() instanceof LivingEntity killer && !(killer instanceof ServerPlayer)
            && (killer instanceof Raider || Bandits.isBandit(killer))) {
            FactionWars.onKill(level, factionOf(killer), factionOf(victim));
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int faction = factionOf(victim);
        if (faction >= 0) {
            changeReputation(level, player, faction, -Config.getServerConfig().factionKillPenalty);
            return;
        }
        int gain = victim instanceof IronGolem ? 5 : victim instanceof AbstractVillager ? 3 : 0;
        if (gain > 0) {
            for (FactionData.Faction band : FactionData.get(level).factions()) {
                changeReputation(level, player, band.id(), gain);
            }
        }
    }
}
