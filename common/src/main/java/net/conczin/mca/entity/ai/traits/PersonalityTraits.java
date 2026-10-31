package net.conczin.mca.entity.ai.traits;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.Needs.Need;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.Traits.Trait;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.expansion.minds.routine.Opportunity.Kind;

import static net.conczin.mca.entity.ai.traits.TraitCategory.EMOTIONAL;
import static net.conczin.mca.entity.ai.traits.TraitCategory.HOBBY;
import static net.conczin.mca.entity.ai.traits.TraitCategory.LIFESTYLE;
import static net.conczin.mca.entity.ai.traits.TraitCategory.SOCIAL;
import static net.conczin.mca.entity.ai.traits.TraitCondition.*;

/**
 * Personality traits in the style of The Sims 4, picked for what makes sense in a Minecraft
 * village. They take the slots of an age (see {@link Traits#getSlots}). The first fourteen
 * carry the former personalities as their dialogue voice.
 */
public final class PersonalityTraits {
    // Emotional, with the voices of the former personalities
    public static final Trait CHEERFUL = trait("cheerful", EMOTIONAL).icon("sunflower").voice(Personality.UPBEAT).conflicts("gloomy")
            .effect(new TraitEffect.Mood(2)).register();
    public static final Trait GLOOMY = trait("gloomy", EMOTIONAL).walk(WalkStyle.MOPEY).icon("gray_dye").voice(Personality.GLOOMY).conflicts("cheerful")
            .effect(new TraitEffect.Mood(-2))
            .effect(new TraitEffect.Thought(RAINING, Need.FUN, 2)).register();
    public static final Trait ROMANTIC = trait("romantic", EMOTIONAL).icon("poppy").voice(Personality.FLIRTY).minAge(AgeState.TEEN)
            .effect(new TraitEffect.HeartGain(1.2F))
            .effect(new TraitEffect.Thought(NEAR_SPOUSE, Need.SOCIAL, 3)).register();
    public static final Trait GOOFBALL = trait("goofball", EMOTIONAL).walk(WalkStyle.BOUNCY).icon("slime_ball").voice(Personality.PLAYFUL)
            .effect(new TraitEffect.NeedRate(Need.FUN, 0.75F))
            .effect(new TraitEffect.Appeal(Kind.PLAY, 2.0F)).register();
    public static final Trait ERRATIC = trait("erratic", EMOTIONAL).icon("chorus_fruit").voice(Personality.ODD)
            .effect(new TraitEffect.Thought(GOOD_DAY, Need.FUN, 3))
            .effect(new TraitEffect.Thought(BAD_DAY, Need.FUN, -3)).register();
    public static final Trait SENSITIVE = trait("sensitive", EMOTIONAL).icon("pink_petals").voice(Personality.SENSITIVE)
            .effect(new TraitEffect.HeartGain(1.2F))
            .effect(new TraitEffect.HeartLoss(2.0F)).register();
    public static final Trait ANXIOUS = trait("anxious", EMOTIONAL).walk(WalkStyle.TIMID).icon("cobweb").voice(Personality.ANXIOUS).conflicts("relaxed", "self_assured")
            .effect(new TraitEffect.NeedRate(Need.SAFETY, 1.5F))
            .effect(new TraitEffect.Thought(AT_HOME, Need.SAFETY, 2)).register();
    public static final Trait RELAXED = trait("relaxed", EMOTIONAL).icon("lily_pad").voice(Personality.RELAXED).conflicts("anxious", "hot_headed")
            .effect(new TraitEffect.NeedRate(Need.SAFETY, 0.7F))
            .effect(new TraitEffect.NeedRate(Need.ENERGY, 0.9F)).register();
    public static final Trait PEACEFUL = trait("peaceful", EMOTIONAL).icon("white_tulip").voice(Personality.PEACEFUL).conflicts("hot_headed", "mean")
            .effect(new TraitEffect.AttackDamage(0.8F))
            .effect(new TraitEffect.HeartLoss(0.75F)).register();
    public static final Trait FRIENDLY = trait("friendly", SOCIAL).icon("cake").voice(Personality.FRIENDLY).conflicts("mean")
            .effect(new TraitEffect.HeartGain(1.25F)).register();
    public static final Trait OUTGOING = trait("outgoing", SOCIAL).walk(WalkStyle.SWAGGER).icon("bell").voice(Personality.EXTROVERTED).conflicts("loner", "socially_awkward")
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 1.5F))
            .effect(new TraitEffect.Thought(IN_COMPANY, Need.SOCIAL, 2))
            .effect(new TraitEffect.Thought(LONELY, Need.SOCIAL, -2))
            .effect(new TraitEffect.Appeal(Kind.CHAT, 2.0F)).register();
    public static final Trait LONER = trait("loner", SOCIAL).icon("spyglass").voice(Personality.INTROVERTED).conflicts("outgoing", "party_animal")
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 0.5F))
            .effect(new TraitEffect.Thought(ALONE, Need.SOCIAL, 2))
            .effect(new TraitEffect.Thought(IN_COMPANY, Need.SOCIAL, -1)).register();
    public static final Trait MEAN = trait("mean", SOCIAL).walk(WalkStyle.STOMP).icon("wither_rose").voice(Personality.CRABBY).conflicts("good", "friendly", "peaceful")
            .effect(new TraitEffect.HeartGain(0.8F))
            .effect(new TraitEffect.AttackDamage(1.1F)).register();
    public static final Trait MATERIALISTIC = trait("materialistic", LIFESTYLE).icon("emerald").voice(Personality.GREEDY).conflicts("generous")
            .effect(new TraitEffect.Thought(WEALTHY, Need.FUN, 3)).register();

    // Emotional
    public static final Trait HOT_HEADED = trait("hot_headed", EMOTIONAL).walk(WalkStyle.STOMP).icon("blaze_powder").conflicts("relaxed", "peaceful")
            .effect(new TraitEffect.AttackDamage(1.15F))
            .effect(new TraitEffect.HeartLoss(1.5F)).register();
    public static final Trait SELF_ASSURED = trait("self_assured", EMOTIONAL).walk(WalkStyle.SWAGGER).icon("golden_helmet").conflicts("anxious")
            .effect(new TraitEffect.NeedRate(Need.SAFETY, 0.7F))
            .effect(new TraitEffect.HeartGain(1.1F)).register();
    public static final Trait CREATIVE = trait("creative", EMOTIONAL).icon("painting")
            .effect(new TraitEffect.SkillGain(1.15F))
            .effect(new TraitEffect.Appeal(Kind.INSPECT, 2.0F)).register();
    public static final Trait GENIUS = trait("genius", EMOTIONAL).icon("enchanted_book")
            .effect(new TraitEffect.SkillGain(1.25F))
            .effect(new TraitEffect.Appeal(Kind.READ, 2.0F)).register();

    // Hobbies
    public static final Trait BOOKWORM = trait("bookworm", HOBBY).icon("book")
            .effect(new TraitEffect.Appeal(Kind.READ, 3.0F)).register();
    public static final Trait MUSIC_LOVER = trait("music_lover", HOBBY).icon("music_disc_cat")
            .effect(new TraitEffect.Appeal(Kind.LISTEN, 3.0F))
            .effect(new TraitEffect.Appeal(Kind.PLAY, 2.0F)).register();
    public static final Trait ART_LOVER = trait("art_lover", HOBBY).icon("item_frame")
            .effect(new TraitEffect.Appeal(Kind.ADMIRE, 3.0F))
            .effect(new TraitEffect.Appeal(Kind.INSPECT, 2.0F)).register();
    public static final Trait FOODIE = trait("foodie", HOBBY).icon("rabbit_stew")
            .effect(new TraitEffect.Appeal(Kind.FEAST, 2.0F))
            .effect(new TraitEffect.Note("trait.effect.foodie", true)).register();
    public static final Trait LOVES_OUTDOORS = trait("loves_outdoors", HOBBY).icon("oak_sapling")
            .effect(new TraitEffect.Thought(OUTDOORS, Need.FUN, 2))
            .effect(new TraitEffect.Appeal(Kind.WATER, 2.0F)).register();
    public static final Trait ANIMAL_LOVER = trait("animal_lover", HOBBY).icon("lead")
            .effect(new TraitEffect.Thought(NEAR_ANIMALS, Need.FUN, 2))
            .effect(new TraitEffect.Appeal(Kind.PET, 3.0F)).register();
    public static final Trait PLANT_LOVER = trait("plant_lover", HOBBY).icon("flower_pot")
            .effect(new TraitEffect.Thought(NEAR_PLANTS, Need.FUN, 2)).register();
    public static final Trait PERFECTIONIST = trait("perfectionist", HOBBY).icon("diamond").minAge(AgeState.TEEN)
            .effect(new TraitEffect.SkillGain(1.3F))
            .effect(new TraitEffect.NeedRate(Need.FUN, 1.2F)).register();
    public static final Trait MACABRE = trait("macabre", HOBBY).icon("skeleton_skull").minAge(AgeState.TEEN)
            .effect(new TraitEffect.Thought(NEAR_GRAVES, Need.FUN, 3)).register();

    // Lifestyle
    public static final Trait ACTIVE = trait("active", LIFESTYLE).walk(WalkStyle.BRISK).icon("leather_boots").conflicts("lazy")
            .effect(new TraitEffect.Speed(1.1F))
            .effect(new TraitEffect.NeedRate(Need.ENERGY, 0.85F)).register();
    public static final Trait LAZY = trait("lazy", LIFESTYLE).walk(WalkStyle.SLUGGISH).icon("white_bed").conflicts("active", "hard_worker", "industrious")
            .effect(new TraitEffect.WorkSpeed(0.85F))
            .effect(new TraitEffect.Speed(0.9F))
            .effect(new TraitEffect.NeedRate(Need.ENERGY, 1.2F))
            .effect(new TraitEffect.Appeal(Kind.SIT, 3.0F)).register();
    public static final Trait GLUTTON = trait("glutton", LIFESTYLE).icon("cooked_beef").conflicts("vegetarian")
            .effect(new TraitEffect.NeedRate(Need.HUNGER, 1.4F))
            .effect(new TraitEffect.Thought(HUNGRY, Need.HUNGER, -3))
            .effect(new TraitEffect.Appeal(Kind.FEAST, 3.0F)).register();
    public static final Trait NEAT = trait("neat", LIFESTYLE).icon("brush").conflicts("slob")
            .effect(new TraitEffect.Note("trait.effect.neat", true)).register();
    public static final Trait SLOB = trait("slob", LIFESTYLE).icon("rotten_flesh").conflicts("neat")
            .effect(new TraitEffect.Note("trait.effect.slob", true)).register();
    public static final Trait ADVENTUROUS = trait("adventurous", LIFESTYLE).walk(WalkStyle.BRISK).icon("compass")
            .effect(new TraitEffect.NeedRate(Need.SAFETY, 0.8F))
            .effect(new TraitEffect.Thought(OUTDOORS, Need.FUN, 1))
            .effect(new TraitEffect.Thought(AT_HOME, Need.FUN, -1)).register();

    public static final Trait KLEPTOMANIAC = trait("kleptomaniac", LIFESTYLE).icon("gold_nugget").minAge(AgeState.TEEN)
            .effect(new TraitEffect.Thought(SWIPED_LATELY, Need.FUN, 2))
            .effect(new TraitEffect.Thought(ITCHING_TO_SWIPE, Need.FUN, -2))
            .effect(new TraitEffect.Note("trait.effect.kleptomaniac", false)).register();
    public static final Trait CLUMSY = trait("clumsy", LIFESTYLE).icon("slime_block")
            .effect(new TraitEffect.Note("trait.effect.clumsy", false)).register();

    // Social
    public static final Trait GOOD = trait("good", SOCIAL).icon("golden_apple").conflicts("mean")
            .effect(new TraitEffect.HeartGain(1.2F))
            .effect(new TraitEffect.Mood(1)).register();
    public static final Trait GENEROUS = trait("generous", SOCIAL).icon("bread").conflicts("materialistic")
            .effect(new TraitEffect.HeartGain(1.15F))
            .effect(new TraitEffect.Thought(IN_COMPANY, Need.SOCIAL, 1)).register();
    public static final Trait FAMILY_ORIENTED = trait("family_oriented", SOCIAL).icon("red_bed").minAge(AgeState.TEEN).conflicts("hates_children")
            .effect(new TraitEffect.Thought(NEAR_SPOUSE, Need.SOCIAL, 2))
            .effect(new TraitEffect.Thought(NEAR_CHILDREN, Need.FUN, 2)).register();
    public static final Trait HATES_CHILDREN = trait("hates_children", SOCIAL).icon("barrier").minAge(AgeState.TEEN).conflicts("family_oriented")
            .effect(new TraitEffect.Thought(NEAR_CHILDREN, Need.FUN, -2)).register();
    public static final Trait NOSY = trait("nosy", SOCIAL).icon("ender_eye").minAge(AgeState.TEEN)
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 1.2F))
            .effect(new TraitEffect.Appeal(Kind.CHAT, 2.0F))
            .effect(new TraitEffect.Appeal(Kind.GATHER, 2.0F)).register();
    public static final Trait SOCIALLY_AWKWARD = trait("socially_awkward", SOCIAL).walk(WalkStyle.TIMID).icon("turtle_helmet").conflicts("outgoing")
            .effect(new TraitEffect.HeartGain(0.8F))
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 0.8F)).register();
    public static final Trait PARTY_ANIMAL = trait("party_animal", SOCIAL).walk(WalkStyle.BOUNCY).icon("firework_rocket").minAge(AgeState.TEEN).conflicts("loner")
            .effect(new TraitEffect.Appeal(Kind.FEAST, 3.0F))
            .effect(new TraitEffect.Appeal(Kind.GATHER, 2.0F))
            .effect(new TraitEffect.Thought(IN_COMPANY, Need.FUN, 2)).register();

    // Social, about the opinions villagers have of each other (see Opinions and Conversations)
    public static final Trait LOYAL = trait("loyal", SOCIAL).icon("lodestone")
            .effect(new TraitEffect.Note("trait.effect.loyal", true)).register();
    public static final Trait JEALOUS = trait("jealous", SOCIAL).icon("fermented_spider_eye").minAge(AgeState.TEEN)
            .effect(new TraitEffect.Thought(NEAR_SPOUSE, Need.SOCIAL, 2))
            .effect(new TraitEffect.Note("trait.effect.jealous", false)).register();

    // From RimWorld
    public static final Trait BRAWLER = trait("brawler", LIFESTYLE).walk(WalkStyle.STOMP).icon("iron_sword").conflicts("peaceful", "careful_shooter")
            .effect(new TraitEffect.AttackDamage(1.3F))
            .effect(new TraitEffect.DamageTaken(0.9F)).register();
    public static final Trait CAREFUL_SHOOTER = trait("careful_shooter", LIFESTYLE).icon("spectral_arrow").minAge(AgeState.TEEN).conflicts("trigger_happy", "brawler")
            .effect(new TraitEffect.DrawTime(1.4F)).register();
    public static final Trait TRIGGER_HAPPY = trait("trigger_happy", LIFESTYLE).icon("arrow").minAge(AgeState.TEEN).conflicts("careful_shooter")
            .effect(new TraitEffect.DrawTime(0.6F)).register();
    public static final Trait UNDERGROUNDER = trait("undergrounder", HOBBY).icon("lantern")
            .effect(new TraitEffect.Thought(UNDERGROUND, Need.FUN, 3)).register();
    public static final Trait BEAUTIFUL = trait("beautiful", SOCIAL).icon("poppy").conflicts("ugly")
            .effect(new TraitEffect.Charm(0.15F))
            .effect(new TraitEffect.HeartGain(1.1F)).register();
    public static final Trait UGLY = trait("ugly", SOCIAL).icon("poisonous_potato").conflicts("beautiful")
            .effect(new TraitEffect.Charm(-0.1F))
            .effect(new TraitEffect.HeartGain(0.9F)).register();
    public static final Trait ANNOYING_VOICE = trait("annoying_voice", SOCIAL).icon("goat_horn")
            .effect(new TraitEffect.Charm(-0.15F)).register();
    public static final Trait PSYCHOPATH = trait("psychopath", EMOTIONAL).icon("skeleton_skull").minAge(AgeState.TEEN).conflicts("good", "sensitive", "family_oriented")
            .effect(new TraitEffect.Note("trait.effect.no_grief", true))
            .effect(new TraitEffect.Charm(-0.1F))
            .effect(new TraitEffect.HeartGain(0.85F)).register();
    public static final Trait HARD_WORKER = trait("hard_worker", LIFESTYLE).icon("iron_pickaxe").conflicts("lazy", "industrious")
            .effect(new TraitEffect.WorkSpeed(1.15F)).register();
    public static final Trait INDUSTRIOUS = trait("industrious", LIFESTYLE).icon("diamond_pickaxe").minAge(AgeState.TEEN).conflicts("lazy", "hard_worker")
            .effect(new TraitEffect.WorkSpeed(1.3F))
            .effect(new TraitEffect.NeedRate(Need.FUN, 1.2F)).register();
    public static final Trait NIGHT_OWL = trait("night_owl", LIFESTYLE).icon("ender_eye").minAge(AgeState.TEEN)
            .effect(new TraitEffect.Note("trait.effect.night_owl", true)).register();
    public static final Trait PYROMANIAC = trait("pyromaniac", EMOTIONAL).icon("flint_and_steel").minAge(AgeState.TEEN)
            .effect(new TraitEffect.Thought(NEAR_FIRE, Need.FUN, 3))
            .effect(new TraitEffect.Note("trait.effect.pyromaniac", false)).register();
    public static final Trait BLOODLUST = trait("bloodlust", EMOTIONAL).icon("redstone").minAge(AgeState.TEEN).conflicts("peaceful", "good")
            .effect(new TraitEffect.Thought(FIGHTING, Need.FUN, 3))
            .effect(new TraitEffect.AttackDamage(1.1F))
            .effect(new TraitEffect.Note("trait.effect.no_grief", true)).register();
    public static final Trait IRON_WILLED = trait("iron_willed", EMOTIONAL).icon("anvil").conflicts("volatile")
            .effect(new TraitEffect.Willpower(4)).register();
    public static final Trait VOLATILE = trait("volatile", EMOTIONAL).icon("tnt").conflicts("iron_willed", "relaxed")
            .effect(new TraitEffect.Willpower(-4)).register();

    // Old age only, gained when turning old (see Traits#growOld)
    public static final Trait WISE = trait("wise", EMOTIONAL).icon("written_book").minAge(AgeState.ELDER).inherit(0.0F).chance(0.5F)
            .effect(new TraitEffect.SkillGain(1.25F))
            .effect(new TraitEffect.Note("trait.effect.teaches", true))
            .effect(new TraitEffect.NeedRate(Need.SAFETY, 0.7F))
            .effect(new TraitEffect.HeartGain(1.1F)).register();
    public static final Trait STORYTELLER = trait("storyteller", SOCIAL).icon("lantern").minAge(AgeState.ELDER).inherit(0.0F).chance(0.5F)
            .effect(new TraitEffect.Appeal(Kind.CHAT, 2.0F))
            .effect(new TraitEffect.Thought(NEAR_CHILDREN, Need.SOCIAL, 2))
            .effect(new TraitEffect.HeartGain(1.1F)).register();

    // Toddlers only, left behind when growing into a child (slots: traitSlotsToddler, none by default)
    public static final Trait WILD = toddler("wild").walk(WalkStyle.BOUNCY).icon("rabbit_foot")
            .effect(new TraitEffect.Speed(1.1F))
            .effect(new TraitEffect.Thought(OUTDOORS, Need.FUN, 2)).register();
    public static final Trait CLINGY = toddler("clingy").icon("pink_wool")
            .effect(new TraitEffect.Thought(AT_HOME, Need.SAFETY, 2))
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 1.3F)).register();
    public static final Trait INQUISITIVE = toddler("inquisitive").icon("glow_berries")
            .effect(new TraitEffect.SkillGain(1.2F)).register();
    public static final Trait SILLY = toddler("silly").walk(WalkStyle.BOUNCY).icon("cookie")
            .effect(new TraitEffect.NeedRate(Need.FUN, 0.8F))
            .effect(new TraitEffect.Mood(1)).register();
    public static final Trait INDEPENDENT = toddler("independent").icon("oak_boat")
            .effect(new TraitEffect.NeedRate(Need.SOCIAL, 0.6F)).register();
    public static final Trait ANGELIC = toddler("angelic").icon("feather")
            .effect(new TraitEffect.HeartGain(1.3F))
            .effect(new TraitEffect.HeartLoss(0.7F)).register();

    private PersonalityTraits() {
    }

    private static Traits.Builder toddler(String path) {
        return trait(path, EMOTIONAL).minAge(AgeState.TODDLER).maxAge(AgeState.TODDLER).inherit(0.0F);
    }

    /** Loads the class, so the traits are registered along with the genetic ones. */
    public static void bootstrap() {
    }

    private static Traits.Builder trait(String path, TraitCategory category) {
        return Traits.builder(MCA.locate(path)).category(category).minAge(AgeState.CHILD).inherit(0.5F);
    }
}
