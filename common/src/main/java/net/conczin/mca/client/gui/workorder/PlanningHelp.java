package net.conczin.mca.client.gui.workorder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * The help lines of the planning screen, made of hints: a key or mouse button in yellow brackets, then what it does in
 * light grey, so the eye finds the button first.
 */
final class PlanningHelp {
    static final int KEY_COLOR = 0xFFE07A;
    static final int ACTION_COLOR = 0xD8D8D8;
    private static final int SEPARATOR_COLOR = 0x808080;

    private PlanningHelp() {
    }

    /** {@code key} and {@code action} name {@code gui.workorder.key.<key>} and {@code gui.workorder.do.<action>}. */
    record Hint(String key, String action, Object... args) {
    }

    static Hint hint(String key, String action, Object... args) {
        return new Hint(key, action, args);
    }

    static Component line(List<Hint> hints) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < hints.size(); i++) {
            Hint hint = hints.get(i);
            if (i > 0) {
                line.append(Component.literal("  ·  ").withColor(SEPARATOR_COLOR));
            }
            line.append(Component.literal("[").append(Component.translatable("gui.workorder.key." + hint.key())).append("] ")
                    .withColor(KEY_COLOR));
            line.append(Component.translatable("gui.workorder.do." + hint.action(), hint.args()).withColor(ACTION_COLOR));
        }
        return line;
    }

    /** Flying the camera around, the same for every tool. */
    static List<Hint> movement() {
        return List.of(
                hint("wasd", "fly"),
                hint("space_shift", "up_down"),
                hint("sprint", "faster"),
                hint("right_drag", "look"),
                hint("help", "hide_help")
        );
    }
}
