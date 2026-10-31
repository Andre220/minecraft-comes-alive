package net.conczin.mca.addon.roads.faction;

import com.mojang.serialization.DynamicOps;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The factions of a world: their names and banners, the outposts each holds, and what every player is to each of
 * them. Made once when factions are first needed, then grown as outposts are found.
 */
public class FactionData extends SavedData {
    public static final class Faction {
        final int id;
        /** A place name and which of the name templates frames it ("Raiders of %s"...). */
        final String place;
        final int template;
        final DyeColor base;
        final BannerPatternLayers patterns;
        final String type;

        Faction(int id, String place, int template, DyeColor base, BannerPatternLayers patterns, String type) {
            this.id = id;
            this.place = place;
            this.template = template;
            this.base = base;
            this.patterns = patterns;
            this.type = type;
        }

        public int id() {
            return id;
        }

        public DyeColor base() {
            return base;
        }

        public BannerPatternLayers patterns() {
            return patterns;
        }
    }

    /** A pillager outpost a faction holds. */
    public record Outpost(BlockPos pos, int faction) {
    }

    /** A band paid to leave a village alone until a game time. */
    public record Truce(int faction, int village, long until) {
    }

    private final Map<Integer, Faction> factions = new LinkedHashMap<>();
    private final List<Outpost> outposts = new ArrayList<>();
    /** Player to faction to reputation. */
    private final Map<UUID, Map<Integer, Integer>> reputation = new HashMap<>();
    /** Pair of factions (lower id in the high bits) to how they stand, from -100 (war) to 100. */
    private final Map<Long, Integer> relations = new HashMap<>();
    private final List<Truce> truces = new ArrayList<>();
    /** The outposts each player has seen, as packed positions; only these show on their map. */
    private final Map<UUID, Set<Long>> known = new HashMap<>();
    /** Outposts that changed hands while nobody was near; their banners are swapped once someone comes. */
    private final Set<Long> repaint = new HashSet<>();
    private int nextId = 1;

    FactionData() {
    }

    FactionData(CompoundTag nbt, HolderLookup.Provider registries) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (Tag entry : nbt.getListOrEmpty("factions")) {
            if (!(entry instanceof CompoundTag tag)) {
                continue;
            }
            BannerPatternLayers patterns = tag.get("patterns") == null ? BannerPatternLayers.EMPTY
                    : BannerPatternLayers.CODEC.parse(ops, tag.get("patterns")).result().orElse(BannerPatternLayers.EMPTY);
            Faction faction = new Faction(tag.getIntOr("id", 0), tag.getStringOr("place", "?"), tag.getIntOr("template", 0),
                    DyeColor.byName(tag.getStringOr("base", "black"), DyeColor.BLACK), patterns, tag.getStringOr("type", "raiders"));
            factions.put(faction.id, faction);
        }
        for (Tag entry : nbt.getListOrEmpty("outposts")) {
            if (entry instanceof CompoundTag tag) {
                outposts.add(new Outpost(BlockPos.of(tag.getLongOr("pos", 0L)), tag.getIntOr("faction", 0)));
            }
        }
        CompoundTag players = nbt.getCompoundOrEmpty("reputation");
        for (String player : players.keySet()) {
            CompoundTag values = players.getCompoundOrEmpty(player);
            Map<Integer, Integer> map = new HashMap<>();
            for (String faction : values.keySet()) {
                map.put(Integer.parseInt(faction), values.getIntOr(faction, 0));
            }
            reputation.put(UUID.fromString(player), map);
        }
        CompoundTag rel = nbt.getCompoundOrEmpty("relations");
        for (String key : rel.keySet()) {
            relations.put(Long.parseLong(key), rel.getIntOr(key, 0));
        }
        for (Tag entry : nbt.getListOrEmpty("truces")) {
            if (entry instanceof CompoundTag tag) {
                truces.add(new Truce(tag.getIntOr("faction", 0), tag.getIntOr("village", 0), tag.getLongOr("until", 0L)));
            }
        }
        CompoundTag seen = nbt.getCompoundOrEmpty("known");
        for (String player : seen.keySet()) {
            Set<Long> set = new HashSet<>();
            for (long pos : seen.getLongArray(player).orElse(new long[0])) {
                set.add(pos);
            }
            known.put(UUID.fromString(player), set);
        }
        for (long pos : nbt.getLongArray("repaint").orElse(new long[0])) {
            repaint.add(pos);
        }
        nextId = nbt.getIntOr("nextId", 1);
    }

    public static FactionData get(ServerLevel level) {
        return WorldUtils.loadData(level, FactionData::new, ignored -> new FactionData(), "mca_factions");
    }

    /** Called by name through WorldUtils when the data is saved. */
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, provider);
        ListTag list = new ListTag();
        for (Faction faction : factions.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", faction.id);
            tag.putString("place", faction.place);
            tag.putInt("template", faction.template);
            tag.putString("base", faction.base.getSerializedName());
            tag.putString("type", faction.type);
            BannerPatternLayers.CODEC.encodeStart(ops, faction.patterns).result().ifPresent(patterns -> tag.put("patterns", patterns));
            list.add(tag);
        }
        nbt.put("factions", list);
        ListTag posts = new ListTag();
        for (Outpost outpost : outposts) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", outpost.pos.asLong());
            tag.putInt("faction", outpost.faction);
            posts.add(tag);
        }
        nbt.put("outposts", posts);
        CompoundTag players = new CompoundTag();
        reputation.forEach((player, values) -> {
            CompoundTag tag = new CompoundTag();
            values.forEach((faction, value) -> tag.putInt(String.valueOf(faction), value));
            players.put(player.toString(), tag);
        });
        nbt.put("reputation", players);
        CompoundTag rel = new CompoundTag();
        relations.forEach((pair, value) -> rel.putInt(String.valueOf(pair), value));
        nbt.put("relations", rel);
        ListTag truceList = new ListTag();
        for (Truce truce : truces) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("faction", truce.faction);
            tag.putInt("village", truce.village);
            tag.putLong("until", truce.until);
            truceList.add(tag);
        }
        nbt.put("truces", truceList);
        CompoundTag seen = new CompoundTag();
        known.forEach((player, set) -> seen.putLongArray(player.toString(), set.stream().mapToLong(Long::longValue).toArray()));
        nbt.put("known", seen);
        nbt.putLongArray("repaint", repaint.stream().mapToLong(Long::longValue).toArray());
        nbt.putInt("nextId", nextId);
        return nbt;
    }

    public List<Faction> factions() {
        return new ArrayList<>(factions.values());
    }

    public Optional<Faction> faction(int id) {
        return Optional.ofNullable(factions.get(id));
    }

    Faction create(String place, int template, DyeColor base, BannerPatternLayers patterns) {
        Faction faction = new Faction(nextId++, place, template, base, patterns, "raiders");
        factions.put(faction.id, faction);
        setDirty();
        return faction;
    }

    public List<Outpost> outposts() {
        return List.copyOf(outposts);
    }

    void addOutpost(Outpost outpost) {
        outposts.add(outpost);
        setDirty();
    }

    public long outpostCount(int faction) {
        return outposts.stream().filter(o -> o.faction == faction).count();
    }

    /** Hands an outpost to another faction; its banners change when someone is near. */
    void setOwner(Outpost outpost, int faction) {
        int index = outposts.indexOf(outpost);
        if (index >= 0) {
            outposts.set(index, new Outpost(outpost.pos, faction));
            repaint.add(outpost.pos.asLong());
            setDirty();
        }
    }

    Set<Long> repaint() {
        return repaint;
    }

    void repainted(BlockPos pos) {
        if (repaint.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean knows(UUID player, BlockPos outpost) {
        return known.getOrDefault(player, Set.of()).contains(outpost.asLong());
    }

    void learn(UUID player, BlockPos outpost) {
        if (known.computeIfAbsent(player, ignored -> new HashSet<>()).add(outpost.asLong())) {
            setDirty();
        }
    }

    private static long pair(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
    }

    /** How two factions stand; a pair never set starts wary. */
    public int relation(int a, int b) {
        return a == b ? 100 : relations.getOrDefault(pair(a, b), -10);
    }

    void setRelation(int a, int b, int value) {
        if (a != b) {
            relations.put(pair(a, b), value);
            setDirty();
        }
    }

    public List<Truce> truces() {
        return List.copyOf(truces);
    }

    void addTruce(Truce truce) {
        truces.removeIf(t -> t.faction == truce.faction && t.village == truce.village);
        truces.add(truce);
        setDirty();
    }

    void dropExpiredTruces(long now) {
        if (truces.removeIf(t -> t.until < now)) {
            setDirty();
        }
    }

    public int reputation(UUID player, int faction, int start) {
        return reputation.getOrDefault(player, Map.of()).getOrDefault(faction, start);
    }

    void setReputation(UUID player, int faction, int value) {
        reputation.computeIfAbsent(player, ignored -> new HashMap<>()).put(faction, value);
        setDirty();
    }
}
