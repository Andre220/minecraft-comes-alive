package net.conczin.mca.expansion.speech;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** A villager speaking in a bubble stays where it is, facing whoever it talks to, until it has said it all. */
public final class SpeechPause {
    private SpeechPause() {
    }

    public static void onSpoke(Entity speaker, Entity receiver, Component message) {
        if (Config.getInstance().villagerSpeechBubbles && speaker instanceof VillagerEntityMCA villager && receiver instanceof ServerPlayer player) {
            villager.getInteractions().speakTo(player, ticksToSay(message.getString()));
        }
    }

    /** Typing at the configured speed, plus a moment to read it. */
    public static int ticksToSay(String text) {
        int lettersPerSecond = Math.max(5, Config.getInstance().speechBubbleLettersPerSecond);
        return Mth.clamp(40 + text.length() * 20 / lettersPerSecond + text.length() / 2, 60, 400);
    }
}
