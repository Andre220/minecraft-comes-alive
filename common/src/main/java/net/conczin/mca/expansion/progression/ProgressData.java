package net.conczin.mca.expansion.progression;

import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * What each village had reached last time it was looked at: its stage and the tier of each of its
 * buildings, so growing gets announced once and villages found as they are start right there.
 */
public class ProgressData extends SavedData {
    private final Map<Integer, Integer> stages = new HashMap<>();
    /** village id -> building id -> tier rank. */
    private final Map<Integer, Map<Integer, Integer>> tiers = new HashMap<>();

    ProgressData() {
    }

    ProgressData(CompoundTag nbt) {
        CompoundTag stageTag = nbt.getCompoundOrEmpty("stages");
        for (String key : stageTag.keySet()) {
            stages.put(Integer.parseInt(key), stageTag.getIntOr(key, -1));
        }
        CompoundTag tierTag = nbt.getCompoundOrEmpty("tiers");
        for (String village : tierTag.keySet()) {
            CompoundTag buildings = tierTag.getCompoundOrEmpty(village);
            Map<Integer, Integer> map = new HashMap<>();
            for (String building : buildings.keySet()) {
                map.put(Integer.parseInt(building), buildings.getIntOr(building, -1));
            }
            tiers.put(Integer.parseInt(village), map);
        }
    }

    public static ProgressData get(ServerLevel level) {
        return WorldUtils.loadData(level, (nbt, provider) -> new ProgressData(nbt), ignored -> new ProgressData(), "mca_village_progress");
    }

    /** The stage last seen, -1 for a village not looked at yet. */
    public int stage(int village) {
        return stages.getOrDefault(village, -1);
    }

    public void setStage(int village, int stage) {
        stages.put(village, stage);
        setDirty();
    }

    public int tier(int village, int building) {
        return tiers.getOrDefault(village, Map.of()).getOrDefault(building, -1);
    }

    public void setTier(int village, int building, int rank) {
        tiers.computeIfAbsent(village, ignored -> new HashMap<>()).put(building, rank);
        setDirty();
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        CompoundTag stageTag = new CompoundTag();
        stages.forEach((village, stage) -> stageTag.putInt(String.valueOf(village), stage));
        nbt.put("stages", stageTag);
        CompoundTag tierTag = new CompoundTag();
        tiers.forEach((village, buildings) -> {
            CompoundTag map = new CompoundTag();
            buildings.forEach((building, rank) -> map.putInt(String.valueOf(building), rank));
            tierTag.put(String.valueOf(village), map);
        });
        nbt.put("tiers", tierTag);
        return nbt;
    }
}
