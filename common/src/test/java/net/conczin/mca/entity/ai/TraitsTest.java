package net.conczin.mca.entity.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Opinions;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.entity.ai.traits.TraitCategory;
import net.conczin.mca.entity.ai.traits.TraitCompatibility;
import net.conczin.mca.entity.ai.traits.TraitCondition;
import net.conczin.mca.entity.ai.traits.TraitEffect;
import net.conczin.mca.entity.ai.traits.WalkStyle;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraitsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PersonalityTraits.bootstrap();
    }

    @Test
    void effectsDescribeMultipliersAsSignedPercentages() {
        assertEquals("+10%", args(new TraitEffect.Speed(1.1F))[0]);
        assertEquals("-25%", args(new TraitEffect.DamageTaken(0.75F))[0]);
        assertEquals("+0%", args(new TraitEffect.SkillGain(1.0F))[0]);
    }

    @Test
    void lessDamageTakenAndSlowerNeedsAreBuffs() {
        assertTrue(new TraitEffect.DamageTaken(0.75F).isBuff());
        assertFalse(new TraitEffect.DamageTaken(1.25F).isBuff());
        assertTrue(new TraitEffect.NeedRate(Needs.Need.SOCIAL, 0.5F).isBuff());
        assertFalse(new TraitEffect.NeedRate(Needs.Need.HUNGER, 1.4F).isBuff());
        assertFalse(new TraitEffect.HeartLoss(2.0F).isBuff());
        assertTrue(new TraitEffect.Mood(2).isBuff());
    }

    @Test
    void everyFormerPersonalityIsTheVoiceOfExactlyOneTrait() {
        for (Personality personality : Personality.all()) {
            if (personality == Personality.UNASSIGNED) {
                continue;
            }
            long traits = Traits.all().stream().filter(t -> t.getVoice() == personality).count();
            assertEquals(1, traits, "voice " + personality);
        }
    }

    @Test
    void voicesForChildrenExist() {
        // the first slot of a child is a trait with a voice, so some must fit a child
        assertTrue(Traits.all().stream().anyMatch(t -> t.getVoice() != null && t.isValidFor(AgeState.CHILD)));
        assertFalse(PersonalityTraits.ROMANTIC.isValidFor(AgeState.CHILD));
        assertTrue(PersonalityTraits.ROMANTIC.isValidFor(AgeState.ELDER));
        assertFalse(PersonalityTraits.WISE.isValidFor(AgeState.ADULT));
    }

    @Test
    void conflictsAreSymmetricAndPointAtExistingTraits() {
        assertTrue(PersonalityTraits.CHEERFUL.conflictsWith(PersonalityTraits.GLOOMY));
        assertTrue(PersonalityTraits.GLOOMY.conflictsWith(PersonalityTraits.CHEERFUL));
        assertTrue(Traits.VEGETARIAN.conflictsWith(PersonalityTraits.GLUTTON));
        assertFalse(PersonalityTraits.BOOKWORM.conflictsWith(PersonalityTraits.GENIUS));
        for (Traits.Trait trait : Traits.all()) {
            for (Traits.Trait other : Traits.all()) {
                if (trait.conflictsWith(other)) {
                    assertTrue(Traits.get(other.getId()).isPresent());
                }
            }
        }
    }

    @Test
    void geneticTraitsTakeNoSlot() {
        assertEquals(TraitCategory.GENETIC, Traits.ALBINISM.getCategory());
        assertFalse(Traits.ALBINISM.getCategory().usesSlot());
        assertEquals(TraitCategory.LIFESTYLE, Traits.VEGETARIAN.getCategory());
        assertTrue(Traits.VEGETARIAN.getCategory().usesSlot());
    }

    @Test
    void eldersAreAdultsThatDoNotGrowUp() {
        assertTrue(AgeState.ELDER.isAdult());
        assertTrue(AgeState.ADULT.isAdult());
        assertFalse(AgeState.TEEN.isAdult());
        assertEquals(AgeState.ADULT, AgeState.ADULT.getNext());
        assertEquals(AgeState.ELDER, AgeState.ELDER.getNext());
        assertEquals(0, AgeState.ELDER.toAge());
        // elders have lines of their own and fall back to those of adults
        assertEquals(DialogueType.ELDER, DialogueType.fromAge(AgeState.ELDER));
        assertEquals(DialogueType.ADULT, DialogueType.ELDER.fallback);
    }

    @Test
    void traitsDecideHowVillagersGetAlong() {
        float clash = TraitCompatibility.between(List.of(PersonalityTraits.GOOD), List.of(PersonalityTraits.MEAN));
        float alike = TraitCompatibility.between(List.of(PersonalityTraits.BOOKWORM), List.of(PersonalityTraits.BOOKWORM));
        float hotHeads = TraitCompatibility.between(List.of(PersonalityTraits.HOT_HEADED), List.of(PersonalityTraits.HOT_HEADED));
        float neutral = TraitCompatibility.between(List.of(PersonalityTraits.BOOKWORM), List.of(PersonalityTraits.ACTIVE));
        assertTrue(clash < -0.3F, "good and mean clash: " + clash);
        assertTrue(alike > 0.0F, "shared traits help: " + alike);
        assertTrue(hotHeads < -0.3F, "two hot heads clash: " + hotHeads);
        assertEquals(0.0F, neutral);
        assertEquals(TraitCompatibility.between(List.of(PersonalityTraits.CHEERFUL), List.of(PersonalityTraits.GLOOMY)),
                TraitCompatibility.between(List.of(PersonalityTraits.GLOOMY), List.of(PersonalityTraits.CHEERFUL)));
    }

    @Test
    void opinionLevels() {
        assertEquals("best_friend", Opinions.level(Opinions.BEST_FRIEND));
        assertEquals("friend", Opinions.level(Opinions.FRIEND));
        assertEquals("acquaintance", Opinions.level(0));
        assertEquals("disliked", Opinions.level(Opinions.DISLIKED));
        assertEquals("enemy", Opinions.level(Opinions.MIN));
    }

    @Test
    void theStrongerWalkStyleWinsAndMoodyStylesBeatHappyOnes() {
        assertEquals(WalkStyle.STOMP, WalkStyle.stronger(WalkStyle.BRISK, WalkStyle.STOMP));
        assertEquals(WalkStyle.BOUNCY, WalkStyle.stronger(WalkStyle.NORMAL, WalkStyle.BOUNCY));
        assertEquals(WalkStyle.SHUFFLE, WalkStyle.stronger(WalkStyle.SHUFFLE, WalkStyle.MOPEY));
        assertEquals(WalkStyle.BRISK, Traits.ATHLETIC.getWalkStyle());
        assertEquals(WalkStyle.SLUGGISH, PersonalityTraits.LAZY.getWalkStyle());
    }

    @Test
    void toddlerTraitsAreOutgrown() {
        assertTrue(PersonalityTraits.WILD.isValidFor(AgeState.TODDLER));
        assertFalse(PersonalityTraits.WILD.isValidFor(AgeState.CHILD));
        assertFalse(PersonalityTraits.WILD.isValidFor(AgeState.BABY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en_us", "pt_br"})
    void everyTraitTextIsTranslated(String language) throws Exception {
        JsonObject lang;
        try (InputStream stream = TraitsTest.class.getResourceAsStream("/assets/mca/lang/" + language + ".json")) {
            assertNotNull(stream, language);
            lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> keys = new ArrayList<>();
        for (Traits.Trait trait : Traits.all()) {
            keys.add(key(trait.getName()));
            keys.add(key(trait.getDescription()));
            for (TraitEffect effect : trait.getEffects()) {
                keys.add(key(effect.describe()));
                if (effect instanceof TraitEffect.Thought thought) {
                    keys.add(thought.key());
                } else if (effect instanceof TraitEffect.Mood) {
                    keys.add("trait.thought.mood." + trait.getId().getPath());
                }
            }
        }
        for (TraitCategory category : TraitCategory.values()) {
            keys.add(key(category.getName()));
        }
        for (TraitCondition condition : TraitCondition.values()) {
            keys.add(key(condition.getName()));
        }
        keys.add(key(AgeState.ELDER.getName()));
        keys.add("trait.walk");
        for (WalkStyle style : WalkStyle.values()) {
            keys.add(key(style.getName()));
        }
        for (String level : new String[]{"best_friend", "friend", "acquaintance", "disliked", "enemy"}) {
            keys.add("opinion." + level);
        }
        keys.addAll(List.of("opinion.title", "opinion.friends", "opinion.rivals", "opinion.thought.friend", "opinion.thought.enemy",
                "notify.parent.elder", "trait.max_age", "trait.min_age", "trait.conflicts", "trait.full"));
        for (String key : keys) {
            assertTrue(lang.has(key), language + " misses " + key);
        }
    }

    private static Object[] args(TraitEffect effect) {
        return ((TranslatableContents) effect.describe().getContents()).getArgs();
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
