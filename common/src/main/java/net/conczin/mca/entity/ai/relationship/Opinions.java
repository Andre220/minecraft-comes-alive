package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What a villager thinks of the other villagers, from -100 (enemy) to 100 (best friend), in the
 * style of the friendship bars of The Sims. Opinions grow and sink with every conversation (see
 * {@link Conversations}) and fade a little each day. They are tracked data, so the interaction
 * screen can show friends and rivals; only the strongest few are kept.
 */
public class Opinions {
    public static final int MIN = -100;
    public static final int MAX = 100;
    public static final int BEST_FRIEND = 70;
    public static final int FRIEND = 30;
    public static final int DISLIKED = -15;
    public static final int ENEMY = -40;
    /** How many villagers one remembers; the weakest opinions are forgotten first. */
    private static final int REMEMBERED = 16;
    /** How much each opinion fades per day, towards indifference. */
    private static final int DAILY_FADE = 1;

    private static final CDataParameter<CompoundTag> OPINIONS = CParameter.create("Opinions", new CompoundTag());

    private final VillagerEntityMCA entity;
    private long lastDay = -1;

    public Opinions(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(OPINIONS);
    }

    /** One remembered villager: its name (so clients can show it) and the opinion of it. */
    public record Entry(UUID id, String name, int value) {
        public Component label() {
            return Component.translatable("opinion." + level(value)).withStyle(color(value));
        }
    }

    public int get(UUID other) {
        return entity.getTrackedValue(OPINIONS).getCompound(other.toString())
                .flatMap(tag -> tag.getInt("v")).orElse(0);
    }

    /** Changes the opinion of another villager, remembering its current name. */
    public void change(VillagerEntityMCA other, int amount) {
        if (amount == 0 || other == entity) {
            return;
        }
        CompoundTag opinions = entity.getTrackedValue(OPINIONS).copy();
        String key = other.getUUID().toString();
        int value = Mth.clamp(get(other.getUUID()) + amount, MIN, MAX);
        CompoundTag entry = new CompoundTag();
        entry.putInt("v", value);
        entry.putString("n", other.getName().getString());
        opinions.put(key, entry);
        forgetWeakest(opinions);
        entity.setTrackedValue(OPINIONS, opinions);
    }

    /** All remembered opinions, the best first. */
    public List<Entry> all() {
        CompoundTag opinions = entity.getTrackedValue(OPINIONS);
        List<Entry> entries = new ArrayList<>();
        for (String key : opinions.keySet()) {
            opinions.getCompound(key).ifPresent(tag -> {
                try {
                    entries.add(new Entry(UUID.fromString(key), tag.getStringOr("n", "?"), tag.getIntOr("v", 0)));
                } catch (IllegalArgumentException ignored) {
                    // not a villager id
                }
            });
        }
        entries.sort(Comparator.comparingInt(Entry::value).reversed());
        return entries;
    }

    public List<Entry> friends() {
        return all().stream().filter(e -> e.value() >= FRIEND).toList();
    }

    public List<Entry> rivals() {
        return all().stream().filter(e -> e.value() <= ENEMY).toList().reversed();
    }

    /** Once a day every opinion fades a bit; loyal villagers hold on to theirs twice as long. */
    public void tick() {
        long day = entity.level().getOverworldClockTime() / 24000L;
        if (lastDay < 0 || day <= lastDay) {
            lastDay = Math.max(lastDay, day);
            return;
        }
        boolean loyal = entity.getTraits().hasTrait(PersonalityTraits.LOYAL);
        int fade = (int) (day - lastDay) * DAILY_FADE;
        lastDay = day;
        if (loyal && day % 2 == 0) {
            return;
        }
        CompoundTag opinions = entity.getTrackedValue(OPINIONS).copy();
        for (String key : List.copyOf(opinions.keySet())) {
            opinions.getCompound(key).ifPresent(tag -> {
                int value = tag.getIntOr("v", 0);
                int faded = value > 0 ? Math.max(0, value - fade) : Math.min(0, value + fade);
                if (faded == 0) {
                    opinions.remove(key);
                } else {
                    tag.putInt("v", faded);
                }
            });
        }
        entity.setTrackedValue(OPINIONS, opinions);
    }

    /** Friends nearby cheer up, enemies nearby annoy. */
    public List<Needs.Thought> thoughts() {
        List<Needs.Thought> thoughts = new ArrayList<>();
        if (entity.getTrackedValue(OPINIONS).isEmpty()) {
            return thoughts;
        }
        boolean friend = false;
        boolean enemy = false;
        for (VillagerEntityMCA other : entity.level().getEntitiesOfClass(VillagerEntityMCA.class, entity.getBoundingBox().inflate(8.0),
                v -> v != entity && v.isAlive())) {
            int value = get(other.getUUID());
            friend |= value >= FRIEND;
            enemy |= value <= ENEMY;
        }
        if (friend) {
            thoughts.add(new Needs.Thought(Needs.Need.SOCIAL, "opinion.thought.friend", 1));
        }
        if (enemy) {
            thoughts.add(new Needs.Thought(Needs.Need.SOCIAL, "opinion.thought.enemy", -2));
        }
        return thoughts;
    }

    private static void forgetWeakest(CompoundTag opinions) {
        while (opinions.size() > REMEMBERED) {
            String weakest = null;
            int weakestValue = Integer.MAX_VALUE;
            for (String key : opinions.keySet()) {
                int value = Math.abs(opinions.getCompound(key).flatMap(tag -> tag.getInt("v")).orElse(0));
                if (value < weakestValue) {
                    weakest = key;
                    weakestValue = value;
                }
            }
            opinions.remove(weakest);
        }
    }

    public static String level(int value) {
        if (value >= BEST_FRIEND) {
            return "best_friend";
        } else if (value >= FRIEND) {
            return "friend";
        } else if (value <= ENEMY) {
            return "enemy";
        } else if (value <= DISLIKED) {
            return "disliked";
        }
        return "acquaintance";
    }

    public static ChatFormatting color(int value) {
        if (value >= FRIEND) {
            return ChatFormatting.GREEN;
        } else if (value <= ENEMY) {
            return ChatFormatting.RED;
        } else if (value <= DISLIKED) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.GRAY;
    }
}
