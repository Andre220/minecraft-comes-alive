package net.conczin.mca.expansion.progression;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The progress page the server sent last, kept for the village screen. Plain data, so receiving
 * it loads no client classes.
 */
public final class ProgressCache {
    private static volatile List<Component> lines = List.of();
    private static volatile boolean waiting;

    private ProgressCache() {
    }

    public static void set(List<Component> page) {
        lines = List.copyOf(page);
        waiting = false;
    }

    public static void clear() {
        lines = List.of();
        waiting = true;
    }

    public static List<Component> lines() {
        return lines;
    }

    public static boolean isWaiting() {
        return waiting;
    }
}
