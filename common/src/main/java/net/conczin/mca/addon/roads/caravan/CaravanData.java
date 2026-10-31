package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The caravans on the roads of a world, and the villages players know (caravans only travel between those). A
 * caravan keeps a snapshot of its people, so it can show up at its destination after travelling unseen.
 */
public class CaravanData extends SavedData {
    public enum State {
        /** Walking into the village it is at. */
        ARRIVING,
        /** Trading at the village it is at, until {@code until}. */
        STAYING,
        /** Walking from the village it is at to {@code to}. */
        TRAVELING,
        /** On the road out of sight, reaching {@code to} at {@code until}. */
        ON_ROAD,
        /** Walking away from the known villages, to vanish once out of sight. */
        LEAVING
    }

    public static final class Caravan {
        final int id;
        String name;
        @Nullable UUID leader;
        final List<UUID> members = new ArrayList<>();
        final List<UUID> animals = new ArrayList<>();
        int at = -1;
        int to = -1;
        State state = State.ARRIVING;
        long until;
        int generation;
        long lastSeen;
        /** The members as they were when last seen, each with its entity type under "mca_type". */
        final List<CompoundTag> snapshot = new ArrayList<>();
        /** The entity types of the pack animals. */
        final List<String> animalTypes = new ArrayList<>();
        /** The village it last left, for "where do you come from". */
        int from = -1;
        /** Whether it was ambushed on this trip already. */
        boolean ambushed;
        /** Whether it traded with the storage of the village it is at. */
        boolean traded;
        /** The band that cost it people on its last unseen road, to tell on arrival; empty for none. */
        String lostOnRoad = "";
        /** Seconds each player walked along with it on this trip, for escorting it. */
        final Map<UUID, Integer> escort = new HashMap<>();

        Caravan(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String name() {
            return name;
        }

        /** The tag every member and animal of this generation of the caravan carries. */
        String tag() {
            return Caravans.TAG_PREFIX + id + ":" + generation;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putString("name", name);
            if (leader != null) {
                tag.putString("leader", leader.toString());
            }
            tag.put("members", ids(members));
            tag.put("animals", ids(animals));
            tag.putInt("at", at);
            tag.putInt("to", to);
            tag.putString("state", state.name());
            tag.putLong("until", until);
            tag.putInt("generation", generation);
            tag.putLong("lastSeen", lastSeen);
            ListTag people = new ListTag();
            people.addAll(snapshot);
            tag.put("snapshot", people);
            ListTag types = new ListTag();
            animalTypes.forEach(type -> types.add(StringTag.valueOf(type)));
            tag.put("animalTypes", types);
            tag.putInt("from", from);
            tag.putBoolean("ambushed", ambushed);
            tag.putBoolean("traded", traded);
            tag.putString("lostOnRoad", lostOnRoad);
            CompoundTag escorts = new CompoundTag();
            escort.forEach((player, seconds) -> escorts.putInt(player.toString(), seconds));
            tag.put("escort", escorts);
            return tag;
        }

        static Caravan load(CompoundTag tag) {
            Caravan caravan = new Caravan(tag.getIntOr("id", 0), tag.getStringOr("name", ""));
            tag.getString("leader").map(UUID::fromString).ifPresent(id -> caravan.leader = id);
            readIds(tag.getListOrEmpty("members"), caravan.members);
            readIds(tag.getListOrEmpty("animals"), caravan.animals);
            caravan.at = tag.getIntOr("at", -1);
            caravan.to = tag.getIntOr("to", -1);
            try {
                caravan.state = State.valueOf(tag.getStringOr("state", State.ARRIVING.name()));
            } catch (IllegalArgumentException e) {
                caravan.state = State.ARRIVING;
            }
            caravan.until = tag.getLongOr("until", 0L);
            caravan.generation = tag.getIntOr("generation", 0);
            caravan.lastSeen = tag.getLongOr("lastSeen", 0L);
            for (Tag person : tag.getListOrEmpty("snapshot")) {
                if (person instanceof CompoundTag compound) {
                    caravan.snapshot.add(compound);
                }
            }
            for (Tag type : tag.getListOrEmpty("animalTypes")) {
                type.asString().ifPresent(caravan.animalTypes::add);
            }
            caravan.from = tag.getIntOr("from", -1);
            caravan.ambushed = tag.getBooleanOr("ambushed", false);
            caravan.traded = tag.getBooleanOr("traded", false);
            caravan.lostOnRoad = tag.getStringOr("lostOnRoad", "");
            CompoundTag escorts = tag.getCompoundOrEmpty("escort");
            for (String player : escorts.keySet()) {
                caravan.escort.put(UUID.fromString(player), escorts.getIntOr(player, 0));
            }
            return caravan;
        }

        private static ListTag ids(List<UUID> ids) {
            ListTag list = new ListTag();
            ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
            return list;
        }

        private static void readIds(ListTag list, List<UUID> into) {
            for (Tag id : list) {
                id.asString().map(UUID::fromString).ifPresent(into::add);
            }
        }
    }

    private final Map<Integer, Caravan> caravans = new LinkedHashMap<>();
    private final Set<Integer> known = new HashSet<>();
    private int nextId = 1;

    CaravanData() {
    }

    CaravanData(CompoundTag nbt) {
        for (Tag tag : nbt.getListOrEmpty("caravans")) {
            if (tag instanceof CompoundTag compound) {
                Caravan caravan = Caravan.load(compound);
                caravans.put(caravan.id, caravan);
            }
        }
        for (int village : nbt.getIntArray("known").orElse(new int[0])) {
            known.add(village);
        }
        nextId = nbt.getIntOr("nextId", 1);
    }

    public static CaravanData get(ServerLevel level) {
        return WorldUtils.loadData(level, (nbt, provider) -> new CaravanData(nbt), ignored -> new CaravanData(), "mca_caravans");
    }

    /** Called by name through WorldUtils when the data is saved. */
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        caravans.values().forEach(caravan -> list.add(caravan.save()));
        nbt.put("caravans", list);
        nbt.putIntArray("known", known.stream().mapToInt(Integer::intValue).toArray());
        nbt.putInt("nextId", nextId);
        return nbt;
    }

    public List<Caravan> all() {
        return new ArrayList<>(caravans.values());
    }

    public @Nullable Caravan get(int id) {
        return caravans.get(id);
    }

    Caravan create(String name) {
        Caravan caravan = new Caravan(nextId++, name);
        caravans.put(caravan.id, caravan);
        setDirty();
        return caravan;
    }

    void remove(Caravan caravan) {
        caravans.remove(caravan.id);
        setDirty();
    }

    public boolean isKnown(int village) {
        return known.contains(village);
    }

    public Set<Integer> known() {
        return Set.copyOf(known);
    }

    void markKnown(int village) {
        if (known.add(village)) {
            setDirty();
        }
    }
}
