package net.conczin.mca.entity.ai.traits;

import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.expansion.minds.routine.Opportunity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * One gameplay effect of a trait. Every effect describes itself, so the interface can list
 * the buffs and debuffs of a trait with their numbers instead of hand written text.
 */
public sealed interface TraitEffect {
    /** Whether the effect helps the villager; buffs show green, debuffs red. */
    boolean isBuff();

    /** The effect as a line of text, without the buff or debuff marker. */
    Component describe();

    /** The effect as a tooltip line: an arrow and the description, colored by buff or debuff. */
    default MutableComponent line() {
        return Component.literal(isBuff() ? "▲ " : "▼ ").append(describe())
                .withStyle(isBuff() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /** Movement speed, as a multiplier. */
    record Speed(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier >= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.speed", percent(multiplier));
        }
    }

    /** Melee damage dealt, as a multiplier. */
    record AttackDamage(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier >= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.attack_damage", percent(multiplier));
        }
    }

    /** Damage taken, as a multiplier; less is better. */
    record DamageTaken(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier <= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.damage_taken", percent(multiplier));
        }
    }

    /** How fast a need drops, as a multiplier; slower is better. */
    record NeedRate(Needs.Need need, float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier <= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.need_rate", Component.translatable("needs." + need.id()), percent(multiplier));
        }
    }

    /** A lasting change of mood. */
    record Mood(int mood) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return mood >= 0;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.mood", signed(mood));
        }
    }

    /** A change of mood while a condition holds, shown as a thought next to the need it belongs to. */
    record Thought(TraitCondition condition, Needs.Need need, int mood) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return mood >= 0;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.thought", signed(mood), condition.getName());
        }

        /** The translation key of the thought in the needs tooltip. */
        public String key() {
            return "trait.thought." + condition.id() + (mood >= 0 ? ".good" : ".bad");
        }
    }

    /** How fast the villager gets better at its profession, as a multiplier. */
    record SkillGain(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier >= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.skill_gain", percent(multiplier));
        }
    }

    /** Speed of work in the profession, as a multiplier. */
    record WorkSpeed(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier >= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.work_speed", percent(multiplier));
        }
    }

    /** Time to draw a bow, as a multiplier; quicker shoots more often, slower aims better. */
    record DrawTime(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return true;
        }

        @Override
        public Component describe() {
            return Component.translatable(multiplier < 1.0F ? "trait.effect.draw_quick" : "trait.effect.draw_careful", percent(multiplier));
        }
    }

    /** How others like the villager: conversations go better or worse with it, -1 to 1. */
    record Charm(float amount) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return amount >= 0.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable(amount >= 0.0F ? "trait.effect.charm_good" : "trait.effect.charm_bad");
        }
    }

    /** How quickly a zombie infection takes hold, as a multiplier; less is better. */
    record Infection(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier <= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.infection", percent(multiplier));
        }
    }

    /** How low mood goes before a mental break; positive holds out longer. */
    record Willpower(int levels) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return levels >= 0;
        }

        @Override
        public Component describe() {
            return Component.translatable(levels >= 0 ? "trait.effect.willpower_strong" : "trait.effect.willpower_weak");
        }
    }

    /** Hearts won from players, as a multiplier. */
    record HeartGain(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier >= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.heart_gain", percent(multiplier));
        }
    }

    /** Hearts lost to players, as a multiplier; less is better. */
    record HeartLoss(float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return multiplier <= 1.0F;
        }

        @Override
        public Component describe() {
            return Component.translatable("trait.effect.heart_loss", percent(multiplier));
        }
    }

    /** How much a free time activity attracts the villager, as a multiplier. */
    record Appeal(Opportunity.Kind kind, float multiplier) implements TraitEffect {
        @Override
        public boolean isBuff() {
            return true;
        }

        @Override
        public Component describe() {
            String key = multiplier >= 1.0F ? "trait.effect.appeal" : "trait.effect.appeal.less";
            return Component.translatable(key, Component.translatable("trait.activity." + kind.name().toLowerCase(Locale.ROOT)));
        }
    }

    /**
     * An effect implemented where it happens (diet, home, pregnancy, ...), described by its own
     * translation key. The code checks for the trait itself.
     */
    record Note(String key, boolean isBuff) implements TraitEffect {
        @Override
        public Component describe() {
            return Component.translatable(key);
        }
    }

    static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }

    /** A multiplier as a signed percentage: 1.1 is "+10%", 0.75 is "-25%". */
    static String percent(float multiplier) {
        int percent = Math.round((multiplier - 1.0F) * 100.0F);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }
}
