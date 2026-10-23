package net.conczin.mca.entity.ai;

import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

/**
 * Looping body animations a villager plays while working. The server only picks the clip, the client
 * samples its keyframes from {@code assets/mca/animations/work/<clip>.json} (or {@code <clip>_<profession>.json}).
 */
public enum WorkAnimation {
    NONE,
    HAMMER,
    SHARPEN,
    CHOP,
    STIR,
    WRITE,
    WEAVE,
    CROUCH,
    RUMMAGE,
    /** Arms out with a crate on them, played by haulers while they carry goods. */
    CARRY;

    /** Job-site work by profession path; professions not listed just stand at their station. */
    private static final Map<String, WorkAnimation> BY_PROFESSION = Map.ofEntries(
            Map.entry("armorer", HAMMER),
            Map.entry("toolsmith", HAMMER),
            Map.entry("mason", HAMMER),
            Map.entry("weaponsmith", SHARPEN),
            Map.entry("butcher", CHOP),
            Map.entry("lumberjack", CHOP),
            Map.entry("cleric", STIR),
            Map.entry("leatherworker", STIR),
            Map.entry("librarian", WRITE),
            Map.entry("cartographer", WRITE),
            Map.entry("shepherd", WEAVE),
            Map.entry("fletcher", WEAVE),
            Map.entry("farmer", CROUCH),
            Map.entry("forager", CROUCH),
            Map.entry("fisherman", RUMMAGE),
            Map.entry("hunter", RUMMAGE)
    );

    public String clipName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkAnimation forProfession(Identifier profession) {
        return BY_PROFESSION.getOrDefault(profession.getPath(), NONE);
    }

    public static WorkAnimation byName(String name) {
        for (WorkAnimation animation : values()) {
            if (animation.clipName().equals(name)) {
                return animation;
            }
        }
        return NONE;
    }
}
