package net.conczin.mca.server.world.data;

import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What players own in a dimension: blocks they placed, by canonical position (see
 * PropertyRules#canonical), and whether a padlock locks them. Villager property is not stored here;
 * it is derived from their bed, job site and home. Only the villager locks broken with a crowbar
 * are, until their owners replace them.
 */
public class PropertyManager extends SavedData {
    /** A player's block; locked means it carries a padlock: only the owner (and exceptions) may use it. */
    public record Entry(UUID owner, boolean locked) {
    }

    private final Map<Long, Entry> entries = new HashMap<>();
    /** Villager locks broken open, by position, with the game time their owners replace them. */
    private final Map<Long, Long> brokenLocks = new HashMap<>();

    PropertyManager() {
    }

    PropertyManager(CompoundTag nbt) {
        ListTag list = nbt.getList("entries").orElseGet(ListTag::new);
        for (int i = 0; i < list.size(); i++) {
            list.getCompound(i).ifPresent(tag -> {
                long pos = tag.getLong("pos").orElse(0L);
                String owner = tag.getString("owner").orElse("");
                if (!owner.isEmpty()) {
                    entries.put(pos, new Entry(UUID.fromString(owner), tag.getBoolean("locked").orElse(false)));
                }
            });
        }
        ListTag broken = nbt.getList("brokenLocks").orElseGet(ListTag::new);
        for (int i = 0; i < broken.size(); i++) {
            broken.getCompound(i).ifPresent(tag -> brokenLocks.put(tag.getLong("pos").orElse(0L), tag.getLong("until").orElse(0L)));
        }
    }

    public static PropertyManager get(ServerLevel level) {
        return WorldUtils.loadData(level, (nbt, provider) -> new PropertyManager(nbt), ignored -> new PropertyManager(), "mca_property");
    }

    public @Nullable Entry get(BlockPos pos) {
        return entries.get(pos.asLong());
    }

    public Map<Long, Entry> entries() {
        return entries;
    }

    public void set(BlockPos pos, UUID owner, boolean locked) {
        entries.put(pos.asLong(), new Entry(owner, locked));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (entries.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void breakLock(BlockPos pos, long until) {
        brokenLocks.put(pos.asLong(), until);
        setDirty();
    }

    public boolean isLockBroken(BlockPos pos, long time) {
        Long until = brokenLocks.get(pos.asLong());
        if (until == null) {
            return false;
        }
        if (time >= until) {
            brokenLocks.remove(pos.asLong());
            setDirty();
            return false;
        }
        return true;
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach((pos, entry) -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos);
            tag.putString("owner", entry.owner().toString());
            tag.putBoolean("locked", entry.locked());
            list.add(tag);
        });
        nbt.put("entries", list);
        ListTag broken = new ListTag();
        brokenLocks.forEach((pos, until) -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos);
            tag.putLong("until", until);
            broken.add(tag);
        });
        nbt.put("brokenLocks", broken);
        return nbt;
    }
}
