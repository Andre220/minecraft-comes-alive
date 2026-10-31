package net.conczin.mca.client.gui;

import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.traits.TraitEffect;
import net.conczin.mca.entity.ai.traits.WalkStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltips of traits, shared by the interaction screen and the editor: name, category,
 * description, and every effect as a green buff or red debuff line.
 */
public final class TraitTooltips {
    /** Ages that unlock trait slots, in order. */
    private static final AgeState[] SLOT_AGES = {AgeState.CHILD, AgeState.TEEN, AgeState.ADULT};

    private TraitTooltips() {
    }

    /**
     * The tooltip of a trait. The voice is the villager's current dialogue voice, so the trait
     * that gives it can say so; null leaves that line out.
     */
    public static List<Component> lines(Traits.Trait trait, @Nullable Personality voice) {
        List<Component> lines = new ArrayList<>();
        lines.add(trait.getName().copy().withStyle(ChatFormatting.BOLD).withColor(trait.getCategory().color() & 0xFFFFFF));
        lines.add(trait.getCategory().getName().copy().withStyle(ChatFormatting.DARK_GRAY));
        for (String line : trait.getDescription().getString().split("\n")) {
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        for (TraitEffect effect : trait.getEffects()) {
            lines.add(effect.line());
        }
        if (trait.getWalkStyle() != WalkStyle.NORMAL) {
            lines.add(Component.translatable("trait.walk", trait.getWalkStyle().getName()).withStyle(ChatFormatting.AQUA));
        }
        if (voice != null && trait.getVoice() == voice) {
            lines.add(Component.translatable("trait.voice", voice.getName()).withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    /** The tooltip of a slot not unlocked yet: the age at which it opens. */
    public static Component lockedSlot(int slot) {
        for (AgeState age : SLOT_AGES) {
            if (slot < Traits.getSlots(age)) {
                return Component.translatable("trait.slot.locked", age.getName()).withStyle(ChatFormatting.GRAY);
            }
        }
        return Component.translatable("trait.slot.empty").withStyle(ChatFormatting.GRAY);
    }

    /** The most slots any age has, to show the locked ones too. */
    public static int maxSlots() {
        int max = 0;
        for (AgeState age : SLOT_AGES) {
            max = Math.max(max, Traits.getSlots(age));
        }
        return max;
    }
}
