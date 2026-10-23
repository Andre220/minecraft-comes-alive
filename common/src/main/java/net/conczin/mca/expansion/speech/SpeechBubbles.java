package net.conczin.mca.expansion.speech;

import net.minecraft.util.FormattedCharSequence;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speech bubbles (client): what a villager says shows in a box above its head, letter by letter like in old
 * RPGs, with a beep per couple of letters or MCA's own voice. It is still copied to the chat.
 */
public final class SpeechBubbles {
    /** Width of a bubble line in font pixels. */
    private static final int WIDTH = 110;
    public static final int MAX_LINES = 3;
    private static final long FULL_STOP_PAUSE = 300;
    private static final long COMMA_PAUSE = 150;

    private static final Map<UUID, Bubble> BUBBLES = new ConcurrentHashMap<>();

    private static final class Bubble {
        final List<String> lines;
        /** When each letter shows, in ms after the start. */
        final long[] revealAt;
        final long start;
        final long end;
        int entityId;
        int beeped;

        Bubble(List<String> lines, long[] revealAt, long start) {
            this.lines = lines;
            this.revealAt = revealAt;
            this.start = start;
            long typed = revealAt.length == 0 ? 0 : revealAt[revealAt.length - 1];
            this.end = start + typed + 2000 + 50L * revealAt.length;
        }

        int visible(long now) {
            long elapsed = now - start;
            int low = 0;
            int high = revealAt.length;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (revealAt[mid] <= elapsed) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low;
        }
    }

    private SpeechBubbles() {
    }

    public static boolean enabled() {
        return Config.getInstance().villagerSpeechBubbles;
    }

    /**
     * Shows what a villager said. Returns whether MCA's voice should still speak it (it does when bubbles are off,
     * or when they are set to use it).
     */
    public static boolean show(Component message, UUID speaker) {
        if (!enabled()) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        for (FormattedText line : client.font.getSplitter().splitLines(FormattedText.of(message.getString()), WIDTH, Style.EMPTY)) {
            String string = line.getString().strip();
            if (!string.isEmpty()) {
                lines.add(string);
            }
        }
        if (lines.isEmpty()) {
            return Config.getInstance().speechBubbleSound == SpeechSound.MCA_VOICE;
        }
        Bubble bubble = new Bubble(lines, revealTimes(lines), Util.getMillis());
        Entity entity = findEntity(client, speaker);
        bubble.entityId = entity == null ? -1 : entity.getId();
        BUBBLES.put(speaker, bubble);
        return Config.getInstance().speechBubbleSound == SpeechSound.MCA_VOICE;
    }

    /** When each letter appears, in ms: steady typing, longer after the end of a sentence or a comma. */
    static long[] revealTimes(List<String> lines) {
        int lettersPerSecond = Math.max(5, Config.getInstance().speechBubbleLettersPerSecond);
        long step = 1000L / lettersPerSecond;
        int total = lines.stream().mapToInt(String::length).sum();
        long[] times = new long[total];
        long time = 0;
        int i = 0;
        for (String line : lines) {
            for (int c = 0; c < line.length(); c++) {
                times[i++] = time;
                char ch = line.charAt(c);
                time += step + (ch == '.' || ch == '!' || ch == '?' ? FULL_STOP_PAUSE : ch == ',' ? COMMA_PAUSE : 0);
            }
            time += step; // a line break reads like a space
        }
        return times;
    }

    /** The text shown above a villager right now, line by line (empty when it says nothing). */
    public static List<Component> visibleLines(UUID speaker) {
        // talking to it in its screen: the screen shows the text, typed out the same way
        if (Minecraft.getInstance().gui.screen() instanceof InteractScreen screen && speaker.equals(screen.getVillagerUUID())) {
            return List.of();
        }
        Bubble bubble = BUBBLES.get(speaker);
        long now = Util.getMillis();
        if (bubble == null || now > bubble.end) {
            return List.of();
        }
        int left = bubble.visible(now);
        List<Component> shown = new ArrayList<>();
        for (String line : bubble.lines) {
            if (left <= 0) {
                break;
            }
            shown.add(Component.literal(line.substring(0, Math.min(line.length(), left))));
            left -= line.length();
        }
        return shown.size() > MAX_LINES ? shown.subList(shown.size() - MAX_LINES, shown.size()) : shown;
    }

    /** How many letters of what a villager says have appeared so far (all of them without a bubble). */
    public static int visibleLetters(UUID speaker) {
        Bubble bubble = BUBBLES.get(speaker);
        return enabled() && bubble != null ? bubble.visible(Util.getMillis()) : Integer.MAX_VALUE;
    }

    /** Lines of text typed out in time with the villager's bubble (for its interaction screen). */
    public static List<FormattedCharSequence> typed(UUID speaker, List<FormattedCharSequence> lines) {
        int left = visibleLetters(speaker);
        if (left == Integer.MAX_VALUE) {
            return lines;
        }
        List<FormattedCharSequence> shown = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            StringBuilder text = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                text.appendCodePoint(codePoint);
                return true;
            });
            String plain = text.toString().strip();
            if (left <= 0) {
                shown.add(FormattedCharSequence.EMPTY);
                continue;
            }
            shown.add(Component.literal(plain.substring(0, Math.min(plain.length(), left))).getVisualOrderText());
            left -= plain.length();
        }
        return shown;
    }

    /** The letter being spoken now, or 0 when the villager is not speaking (for the mouth). */
    public static char currentLetter(UUID speaker) {
        Bubble bubble = BUBBLES.get(speaker);
        if (bubble == null) {
            return 0;
        }
        int visible = bubble.visible(Util.getMillis());
        if (visible <= 0 || visible >= bubble.revealAt.length) {
            return 0;
        }
        String flat = String.join("", bubble.lines);
        return flat.charAt(visible - 1);
    }

    public static void tick(Minecraft client) {
        if (BUBBLES.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        BUBBLES.values().removeIf(bubble -> now > bubble.end);
        if (client.level == null || Config.getInstance().speechBubbleSound != SpeechSound.BEEP) {
            return;
        }
        for (Bubble bubble : BUBBLES.values()) {
            int visible = bubble.visible(now);
            if (visible <= bubble.beeped) {
                continue;
            }
            String flat = String.join("", bubble.lines);
            boolean letter = false;
            for (int i = bubble.beeped; i < visible; i++) {
                letter |= Character.isLetterOrDigit(flat.charAt(i)) && i % 2 == 0;
            }
            bubble.beeped = visible;
            Entity entity = client.level.getEntity(bubble.entityId);
            if (letter && entity != null) {
                client.level.playLocalSound(entity.getX(), entity.getEyeY(), entity.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(),
                        SoundSource.NEUTRAL, 0.25F, pitch(entity), false);
            }
        }
    }

    /** Children squeak, women are higher than men, everyone a little different. */
    private static float pitch(Entity entity) {
        float pitch = 1.0F;
        if (entity instanceof LivingEntity living && living.isBaby()) {
            pitch = 1.7F;
        } else if (entity instanceof VillagerLike<?> villager && villager.getGenetics().getGender() == Gender.FEMALE) {
            pitch = 1.35F;
        }
        return pitch + ((entity.getUUID().hashCode() & 15) - 7.5F) * 0.012F;
    }

    private static @Nullable Entity findEntity(Minecraft client, UUID uuid) {
        if (client.level == null) {
            return null;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }
}
