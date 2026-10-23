package net.conczin.mca.entity.ai.queue;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * An entry of a villager's action queue: what, why (schedule, a need or a player's order), how
 * urgent, and for orders who gave it and until when it stands.
 */
public record QueuedAction(ActionType type, Source source, float score, @Nullable UUID player, long until) {
    public enum Source {
        SCHEDULE("S"), NEED("N"), PLAYER("P");

        private final String code;

        Source(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Source byCode(String code) {
            for (Source source : values()) {
                if (source.code.equals(code)) {
                    return source;
                }
            }
            return SCHEDULE;
        }
    }

    public QueuedAction withScore(float score) {
        return new QueuedAction(type, source, score, player, until);
    }
}
