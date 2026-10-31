package net.conczin.mca.expansion.townhall;

import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the town halls keep per village: the villager leading it, the villagers the chief put on a job site by hand
 * (the leader leaves them there) and whether the City Records were set up at the village's start.
 */
public class TownHallData extends SavedData {
    private final Map<Integer, UUID> leaders = new HashMap<>();
    private final Map<Integer, String> leaderNames = new HashMap<>();
    private final Map<Integer, Set<UUID>> pinned = new HashMap<>();
    private final Set<Integer> recordsPlaced = new HashSet<>();

    TownHallData() {
    }

    TownHallData(CompoundTag nbt) {
        CompoundTag villages = nbt.getCompoundOrEmpty("villages");
        for (String key : villages.keySet()) {
            int village = Integer.parseInt(key);
            CompoundTag tag = villages.getCompoundOrEmpty(key);
            tag.getString("leader").map(UUID::fromString).ifPresent(id -> leaders.put(village, id));
            tag.getString("leaderName").ifPresent(name -> leaderNames.put(village, name));
            Set<UUID> ids = new HashSet<>();
            for (Tag id : tag.getListOrEmpty("pinned")) {
                id.asString().map(UUID::fromString).ifPresent(ids::add);
            }
            if (!ids.isEmpty()) {
                pinned.put(village, ids);
            }
            if (tag.getBooleanOr("recordsPlaced", false)) {
                recordsPlaced.add(village);
            }
        }
    }

    public static TownHallData get(ServerLevel level) {
        return WorldUtils.loadData(level, (nbt, provider) -> new TownHallData(nbt), ignored -> new TownHallData(), "mca_town_halls");
    }

    /** Called by name through WorldUtils when the data is saved. */
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        CompoundTag villages = new CompoundTag();
        Set<Integer> ids = new HashSet<>(leaders.keySet());
        ids.addAll(pinned.keySet());
        ids.addAll(recordsPlaced);
        for (int village : ids) {
            CompoundTag tag = new CompoundTag();
            UUID leader = leaders.get(village);
            if (leader != null) {
                tag.putString("leader", leader.toString());
                tag.putString("leaderName", leaderNames.getOrDefault(village, ""));
            }
            ListTag list = new ListTag();
            for (UUID id : pinned.getOrDefault(village, Set.of())) {
                list.add(StringTag.valueOf(id.toString()));
            }
            tag.put("pinned", list);
            tag.putBoolean("recordsPlaced", recordsPlaced.contains(village));
            villages.put(String.valueOf(village), tag);
        }
        nbt.put("villages", villages);
        return nbt;
    }

    public Optional<UUID> leader(int village) {
        return Optional.ofNullable(leaders.get(village));
    }

    public String leaderName(int village) {
        return leaderNames.getOrDefault(village, "");
    }

    public void setLeader(int village, @Nullable UUID leader, String name) {
        if (leader == null) {
            leaders.remove(village);
            leaderNames.remove(village);
        } else {
            leaders.put(village, leader);
            leaderNames.put(village, name);
        }
        setDirty();
    }

    public boolean isPinned(int village, UUID villager) {
        return pinned.getOrDefault(village, Set.of()).contains(villager);
    }

    public void setPinned(int village, UUID villager, boolean pin) {
        Set<UUID> ids = pinned.computeIfAbsent(village, ignored -> new HashSet<>());
        if (pin ? ids.add(villager) : ids.remove(villager)) {
            setDirty();
        }
    }

    public boolean recordsPlaced(int village) {
        return recordsPlaced.contains(village);
    }

    public void markRecordsPlaced(int village) {
        if (recordsPlaced.add(village)) {
            setDirty();
        }
    }
}
