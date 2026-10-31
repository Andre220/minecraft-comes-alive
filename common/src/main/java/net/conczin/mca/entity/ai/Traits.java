package net.conczin.mca.entity.ai;

import net.conczin.mca.CommonConfig;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.entity.ai.traits.TraitCategory;
import net.conczin.mca.entity.ai.traits.TraitCondition;
import net.conczin.mca.entity.ai.traits.TraitEffect;
import net.conczin.mca.entity.ai.traits.WalkStyle;
import net.conczin.mca.expansion.minds.routine.Opportunity;
import net.conczin.mca.util.ExtensibleTypeRegistry;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class Traits {
    private static final ExtensibleTypeRegistry<Trait> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "trait");
    private static final CDataParameter<CompoundTag> TRAITS = CParameter.create("Traits", new CompoundTag());
    /** Personality traits inherited from the parents, favored when the slots of a new age are filled. */
    private static final CDataParameter<CompoundTag> LEANINGS = CParameter.create("TraitLeanings", new CompoundTag());
    private static final float LEANING_WEIGHT = 4.0F;
    /** 2: personality traits with slots, the personality became the voice of a trait. */
    private static final CDataParameter<Integer> VERSION = CParameter.create("TraitsVersion", 0);
    private static final int CURRENT_VERSION = 2;
    /** Chance that a villager turning old trades a trait for one of old age. */
    private static final float ELDER_TRAIT_CHANCE = 0.5F;

    public static final Trait LACTOSE_INTOLERANCE = genetic("lactose_intolerance", 1.0F, 1.0F).icon("milk_bucket")
            .effect(new TraitEffect.Note("trait.effect.lactose_intolerance", false)).register();
    public static final Trait BISEXUAL = genetic("bisexual", 1.0F, 0.0F).icon("purple_dye").register();
    public static final Trait ALBINISM = genetic("albinism", 1.0F, 1.0F).icon("white_dye").register();
    public static final Trait RAINBOW = genetic("rainbow", 0.05F, 0.0F).icon("prismarine_crystals").register();
    public static final Trait RAINBOW_EYES = genetic("rainbow_eyes", 0.05F, 0.0F).icon("ender_eye").register();
    public static final Trait SIRBEN = genetic("sirben", 0.025F, 1.0F).icon("pufferfish").register();
    public static final Trait DWARFISM = genetic("dwarfism", 1.0F, 1.0F).icon("brown_mushroom").register();
    public static final Trait HOMOSEXUAL = genetic("homosexual", 1.0F, 0.0F).icon("red_dye").register();
    public static final Trait HETEROCHROMIA = genetic("heterochromia", 1.0F, 0.5F).icon("spider_eye").register();
    public static final Trait ASEXUAL = genetic("asexual", 1.0F, 0.0F).icon("gray_dye").register();
    public static final Trait COLOR_BLIND = genetic("color_blind", 1.0F, 0.5F).icon("gray_stained_glass").register();
    public static final Trait ATHLETIC = genetic("athletic", 1.0F, 0.5F).notOnPlayer().icon("feather").walk(WalkStyle.BRISK)
            .effect(new TraitEffect.Speed(1.1F)).register();
    public static final Trait LEFT_HANDED = genetic("left_handed", 1.0F, 0.5F).notOnPlayer().icon("shield").register();
    public static final Trait WEAK = genetic("weak", 1.0F, 1.0F).notOnPlayer().icon("bone").conflicts("tough")
            .effect(new TraitEffect.AttackDamage(0.75F)).register();
    public static final Trait TOUGH = genetic("tough", 1.0F, 1.0F).notOnPlayer().icon("iron_chestplate").conflicts("weak")
            .effect(new TraitEffect.AttackDamage(1.5F))
            .effect(new TraitEffect.DamageTaken(0.75F)).register();
    // From RimWorld: how the body takes a zombie bite
    public static final Trait IMMUNE = genetic("immune", 0.5F, 1.0F).notOnPlayer().icon("golden_apple").conflicts("frail")
            .effect(new TraitEffect.Infection(0.5F)).register();
    public static final Trait FRAIL = genetic("frail", 0.5F, 1.0F).notOnPlayer().icon("rotten_flesh").conflicts("immune", "tough")
            .effect(new TraitEffect.Infection(1.5F))
            .effect(new TraitEffect.DamageTaken(1.2F)).register();
    public static final Trait COELIAC_DISEASE = genetic("coeliac_disease", 1.0F, 1.0F).notOnPlayer().icon("wheat")
            .effect(new TraitEffect.Note("trait.effect.coeliac_disease", false)).register();
    public static final Trait DIABETES = genetic("diabetes", 1.0F, 1.0F).notOnPlayer().icon("sugar")
            .effect(new TraitEffect.Note("trait.effect.diabetes", false)).register();
    public static final Trait VEGETARIAN = genetic("vegetarian", 1.0F, 0.5F).notOnPlayer().icon("carrot")
            .category(TraitCategory.LIFESTYLE).minAge(AgeState.CHILD).conflicts("glutton")
            .effect(new TraitEffect.Note("trait.effect.vegetarian", false)).register();
    public static final Trait INFERTILE = genetic("infertile", 1.0F, 0.0F).icon("dead_bush")
            .effect(new TraitEffect.Note("trait.effect.infertile", false)).register();
    public static final Trait ELECTRIFIED = genetic("electrified", 0.0F, 0.0F).notOnPlayer().icon("lightning_rod").register();
    public static final Trait NO_AGING = genetic("no_aging", 0.0F, 0.0F).notOnPlayer().icon("clock").register();
    // public static final Trait UNKNOWN = register(MCA.locate("unknown"), 0.0F, 0.0F, false);

    static {
        PersonalityTraits.bootstrap();
    }

    private final VillagerLike<?> entity;
    private RandomSource random = RandomSource.create();

    public Traits(VillagerLike<?> entity) {
        this.entity = entity;
    }

    /** Registers a genetic trait without effects; kept for addons, {@link #builder(Identifier)} offers more. */
    public static Trait register(Identifier id, float chance, float inherit, boolean usableOnPlayer) {
        Builder builder = builder(id).chance(chance).inherit(inherit);
        return (usableOnPlayer ? builder : builder.notOnPlayer()).register();
    }

    public static Trait register(Identifier id, float chance, float inherit) {
        return register(id, chance, inherit, true);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    private static Builder genetic(String path, float chance, float inherit) {
        return builder(MCA.locate(path)).chance(chance).inherit(inherit);
    }

    public static Optional<Trait> get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static Optional<Trait> get(String id) {
        return REGISTRY.get(id);
    }

    public static List<Trait> all() {
        return REGISTRY.all();
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(TRAITS, LEANINGS, VERSION);
    }

    /** How many personality traits a villager of this age has. */
    public static int getSlots(AgeState age) {
        CommonConfig config = Config.getServerConfig();
        return switch (age) {
            case TODDLER -> config.traitSlotsToddler;
            case CHILD -> config.traitSlotsChild;
            case TEEN -> config.traitSlotsTeen;
            case ADULT, UNASSIGNED -> config.traitSlotsAdult;
            case ELDER -> config.traitSlotsElder;
            default -> 0;
        };
    }

    public Set<Trait> getTraits() {
        Set<Trait> traits = new LinkedHashSet<>();
        for (String id : entity.getTrackedValue(TRAITS).keySet()) {
            get(id).ifPresent(traits::add);
        }
        return traits;
    }

    public Set<Trait> getInheritedTraits() {
        return getTraits().stream().filter(t -> random.nextFloat() < t.inherit * Config.getInstance().traitInheritChance).collect(Collectors.toSet());
    }

    public boolean hasTrait(VillagerLike<?> target, Trait trait) {
        return trait != null && target.getTrackedValue(TRAITS).contains(trait.getId().toString());
    }

    public boolean hasTrait(Trait trait) {
        return hasTrait(entity, trait);
    }

    public boolean hasTrait(String trait) {
        return get(trait).filter(this::hasTrait).isPresent();
    }

    public boolean eitherHaveTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) || hasTrait(other, trait);
    }

    public boolean hasSameTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) && hasTrait(other, trait);
    }

    public void addTrait(Trait trait) {
        if (trait == null) {
            return;
        }

        if (!hasTrait(trait)) {
            CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
            traits.putBoolean(trait.getId().toString(), true);
            entity.setTrackedValue(TRAITS, traits);
        }
        entity.onTraitChanged(trait, true);
    }

    public void removeTrait(Trait trait) {
        if (trait == null) {
            return;
        }

        if (hasTrait(trait)) {
            CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
            traits.remove(trait.getId().toString());
            entity.setTrackedValue(TRAITS, traits);
        }
        entity.onTraitChanged(trait, false);
    }

    /** The traits that take a slot, in the order they were gained. */
    public List<Trait> getPersonalityTraits() {
        return getTraits().stream().filter(t -> t.getCategory().usesSlot()).toList();
    }

    /** Whether the trait could be added now: enabled, fitting the age, not conflicting and with a free slot. */
    public boolean canAdd(Trait trait, AgeState age) {
        if (hasTrait(trait) || !trait.isEnabled() || !trait.isValidFor(age)) {
            return false;
        }
        for (Trait owned : getTraits()) {
            if (owned.conflictsWith(trait)) {
                return false;
            }
        }
        return !trait.getCategory().usesSlot() || getPersonalityTraits().size() < getSlots(age);
    }

    //rolls the genetic traits; personality traits come with the age, see fillSlots
    public void randomize() {
        List<Trait> traits = all().stream().filter(t -> !t.getCategory().usesSlot()).toList();
        float total = (float) traits.stream().mapToDouble(trait -> trait.chance).sum();
        for (Trait trait : traits) {
            float chance = Config.getInstance().traitChance / total * trait.chance;
            if (random.nextFloat() < chance && trait.isEnabled()) {
                addTrait(trait);
            }
        }
    }

    /**
     * Rolls personality traits until the slots of the age are full, favoring the ones inherited
     * from the parents. Traits already owned stay, so growing up only adds new ones.
     */
    public void fillSlots(AgeState age) {
        entity.setTrackedValue(VERSION, CURRENT_VERSION);
        Set<String> leanings = entity.getTrackedValue(LEANINGS).keySet();
        int missing = getSlots(age) - getPersonalityTraits().size();
        if (missing > 0 && getVoiceTraits().isEmpty()) {
            // The first trait gives the dialogue voice; a toddler keeps the voice it already had.
            Optional<Trait> current = forVoice(entity.getVillagerBrain().getPersonality()).filter(t -> canAdd(t, age));
            if (current.isPresent()) {
                addTrait(current.get());
                missing--;
            } else if (pick(age, leanings, true)) {
                missing--;
            }
        }
        for (; missing > 0; missing--) {
            if (!pick(age, leanings, false)) {
                return;
            }
        }
    }

    /**
     * Old saves: the personality becomes the trait with its voice, then the slots of the age are
     * filled. Runs once per villager.
     */
    public void migrate(AgeState age) {
        if (isMigrated()) {
            return;
        }
        forVoice(entity.getVillagerBrain().getPersonality()).filter(t -> canAdd(t, age)).ifPresent(this::addTrait);
        fillSlots(age);
    }

    public boolean isMigrated() {
        return entity.getTrackedValue(VERSION) >= CURRENT_VERSION;
    }

    /** Growing older drops the traits of a younger age, like those only toddlers have. */
    public void dropOutgrown(AgeState age) {
        for (Trait trait : getPersonalityTraits()) {
            if (trait.getMaxAge() != null && !trait.isValidFor(age)) {
                removeTrait(trait);
            }
        }
    }

    /**
     * Turning old, a villager may trade one of its traits for one only elders have. The trait
     * giving the voice stays, so the villager keeps sounding like itself.
     */
    public void growOld() {
        if (random.nextFloat() >= ELDER_TRAIT_CHANCE) {
            return;
        }
        List<Trait> elderTraits = all().stream()
                .filter(t -> t.getMinAge() == AgeState.ELDER && t.isEnabled() && !hasTrait(t)
                             && getTraits().stream().noneMatch(t::conflictsWith))
                .toList();
        if (elderTraits.isEmpty()) {
            return;
        }
        Personality voice = entity.getVillagerBrain().getPersonality();
        List<Trait> replaceable = getPersonalityTraits().stream().filter(t -> t.getVoice() != voice).toList();
        if (getPersonalityTraits().size() >= getSlots(AgeState.ELDER)) {
            if (replaceable.isEmpty()) {
                return;
            }
            removeTrait(replaceable.get(random.nextInt(replaceable.size())));
        }
        addTrait(elderTraits.get(random.nextInt(elderTraits.size())));
    }

    /** Adds one random personality trait the villager can have; false when none fits. */
    private boolean pick(AgeState age, Set<String> leanings, boolean voiceOnly) {
        List<Trait> candidates = all().stream()
                .filter(t -> t.getCategory().usesSlot() && (!voiceOnly || t.getVoice() != null) && canAdd(t, age))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        double total = candidates.stream().mapToDouble(t -> weight(t, leanings)).sum();
        double roll = random.nextDouble() * total;
        Trait picked = candidates.getLast();
        for (Trait candidate : candidates) {
            roll -= weight(candidate, leanings);
            if (roll < 0) {
                picked = candidate;
                break;
            }
        }
        addTrait(picked);
        return true;
    }

    private static double weight(Trait trait, Set<String> leanings) {
        return trait.chance * (leanings.contains(trait.getId().toString()) ? LEANING_WEIGHT : 1.0F);
    }

    /** Genetic traits pass on directly; personality traits become leanings for when the child grows up. */
    public void inherit(Traits from) {
        for (Trait trait : from.getInheritedTraits()) {
            if (trait.getCategory().usesSlot()) {
                CompoundTag leanings = entity.getTrackedValue(LEANINGS).copy();
                leanings.putBoolean(trait.getId().toString(), true);
                entity.setTrackedValue(LEANINGS, leanings);
            } else {
                addTrait(trait);
            }
        }
    }

    public void inherit(Traits from, long seed) {
        RandomSource old = random;
        random = RandomSource.create(seed);
        inherit(from);
        random = old;
    }

    public float getVerticalScaleFactor() {
        return hasTrait(DWARFISM) ? 0.65f : 1.0f;
    }

    public float getHorizontalScaleFactor() {
        return (hasTrait(DWARFISM) ? 0.85f : 1.0f) * (hasTrait(TOUGH) ? 1.2f : 1.0f) * (hasTrait(WEAK) ? 0.85f : 1.0f);
    }

    /** All effects of one type among the traits of this villager. */
    public <E extends TraitEffect> List<E> getEffects(Class<E> type) {
        List<E> effects = new ArrayList<>();
        for (Trait trait : getTraits()) {
            for (TraitEffect effect : trait.getEffects()) {
                if (type.isInstance(effect)) {
                    effects.add(type.cast(effect));
                }
            }
        }
        return effects;
    }

    /** The product of a multiplier over all effects of one type; 1 without any. */
    public <E extends TraitEffect> float multiplier(Class<E> type, ToDoubleFunction<E> value) {
        float multiplier = 1.0F;
        for (E effect : getEffects(type)) {
            multiplier *= (float) value.applyAsDouble(effect);
        }
        return multiplier;
    }

    public float getSpeedMultiplier() {
        return multiplier(TraitEffect.Speed.class, TraitEffect.Speed::multiplier);
    }

    public float getAttackDamageMultiplier() {
        return multiplier(TraitEffect.AttackDamage.class, TraitEffect.AttackDamage::multiplier);
    }

    public float getDamageTakenMultiplier() {
        return multiplier(TraitEffect.DamageTaken.class, TraitEffect.DamageTaken::multiplier);
    }

    public float getNeedRateMultiplier(Needs.Need need) {
        return multiplier(TraitEffect.NeedRate.class, e -> e.need() == need ? e.multiplier() : 1.0F);
    }

    public float getSkillGainMultiplier() {
        return multiplier(TraitEffect.SkillGain.class, TraitEffect.SkillGain::multiplier);
    }

    public float getHeartGainMultiplier() {
        return multiplier(TraitEffect.HeartGain.class, TraitEffect.HeartGain::multiplier);
    }

    public float getHeartLossMultiplier() {
        return multiplier(TraitEffect.HeartLoss.class, TraitEffect.HeartLoss::multiplier);
    }

    public float getWorkSpeedMultiplier() {
        return multiplier(TraitEffect.WorkSpeed.class, TraitEffect.WorkSpeed::multiplier);
    }

    public float getDrawTimeMultiplier() {
        return multiplier(TraitEffect.DrawTime.class, TraitEffect.DrawTime::multiplier);
    }

    public float getInfectionMultiplier() {
        return multiplier(TraitEffect.Infection.class, TraitEffect.Infection::multiplier);
    }

    /** How others like the villager, summed over its traits. */
    public float getCharm() {
        float charm = 0.0F;
        for (Trait trait : getTraits()) {
            for (TraitEffect effect : trait.getEffects()) {
                if (effect instanceof TraitEffect.Charm c) {
                    charm += c.amount();
                }
            }
        }
        return charm;
    }

    /** Mood levels the villager holds out before a mental break, summed over its traits. */
    public int getWillpower() {
        int willpower = 0;
        for (Trait trait : getTraits()) {
            for (TraitEffect effect : trait.getEffects()) {
                if (effect instanceof TraitEffect.Willpower w) {
                    willpower += w.levels();
                }
            }
        }
        return willpower;
    }

    public float getAppealMultiplier(Opportunity.Kind kind) {
        return multiplier(TraitEffect.Appeal.class, e -> e.kind() == kind ? e.multiplier() : 1.0F);
    }

    /** Scales a change of hearts with a player: gains and losses have their own multipliers. */
    public int scaleHearts(int hearts) {
        float multiplier = hearts > 0 ? getHeartGainMultiplier() : getHeartLossMultiplier();
        return Math.round(hearts * multiplier);
    }

    /** The thoughts the traits cause right now: lasting moods and moods of the current situation. */
    public List<Needs.Thought> thoughts(VillagerEntityMCA villager) {
        List<Needs.Thought> thoughts = new ArrayList<>();
        TraitCondition.Context context = null;
        for (Trait trait : getTraits()) {
            for (TraitEffect effect : trait.getEffects()) {
                if (effect instanceof TraitEffect.Mood mood && mood.mood() != 0) {
                    thoughts.add(new Needs.Thought(null, "trait.thought.mood." + REGISTRY.translationSuffix(trait.getId()), mood.mood()));
                } else if (effect instanceof TraitEffect.Thought thought) {
                    if (context == null) {
                        context = new TraitCondition.Context(villager);
                    }
                    if (thought.condition().test(context)) {
                        thoughts.add(new Needs.Thought(thought.need(), thought.key(), thought.mood()));
                    }
                }
            }
        }
        return thoughts;
    }

    /** How the villager walks: elders shuffle, else the strongest style among the traits. */
    public WalkStyle getWalkStyle(AgeState age) {
        if (age == AgeState.ELDER) {
            return WalkStyle.SHUFFLE;
        }
        WalkStyle style = WalkStyle.NORMAL;
        for (Trait trait : getTraits()) {
            style = WalkStyle.stronger(style, trait.getWalkStyle());
        }
        return style;
    }

    /** The traits that give a dialogue voice, in registration order. */
    public List<Trait> getVoiceTraits() {
        Set<Trait> owned = getTraits();
        return all().stream().filter(t -> t.getVoice() != null && owned.contains(t)).toList();
    }

    /** The trait that gives this voice, if any. */
    public static Optional<Trait> forVoice(Personality personality) {
        return all().stream().filter(t -> t.getVoice() == personality).findFirst();
    }

    public static final class Trait {
        private final Identifier id;
        private final float chance;
        private final float inherit;
        private final boolean usableOnPlayer;
        private final TraitCategory category;
        private final @Nullable AgeState minAge;
        private final @Nullable AgeState maxAge;
        private final Set<Identifier> conflicts;
        private final @Nullable Identifier icon;
        private final List<TraitEffect> effects;
        private final @Nullable Personality voice;
        private final WalkStyle walk;

        private Trait(Identifier id, Builder builder) {
            this.voice = builder.voice;
            this.walk = builder.walk;
            this.id = id;
            this.chance = builder.chance;
            this.inherit = builder.inherit;
            this.usableOnPlayer = builder.usableOnPlayer;
            this.category = builder.category;
            this.minAge = builder.minAge;
            this.maxAge = builder.maxAge;
            this.conflicts = Set.copyOf(builder.conflicts);
            this.icon = builder.icon;
            this.effects = List.copyOf(builder.effects);
        }

        public Identifier getId() {
            return id;
        }

        public TraitCategory getCategory() {
            return category;
        }

        public List<TraitEffect> getEffects() {
            return effects;
        }

        /** The dialogue voice (the former personality) this trait gives, if any. */
        public @Nullable Personality getVoice() {
            return voice;
        }

        public WalkStyle getWalkStyle() {
            return walk;
        }

        /** Whether a villager of this age can have the trait; genetic traits fit any age. */
        public boolean isValidFor(AgeState age) {
            return (minAge == null || age.ordinal() >= minAge.ordinal()) && (maxAge == null || age.ordinal() <= maxAge.ordinal());
        }

        public @Nullable AgeState getMinAge() {
            return minAge;
        }

        public @Nullable AgeState getMaxAge() {
            return maxAge;
        }

        /** Whether the two traits exclude each other; it is enough that one of them says so. */
        public boolean conflictsWith(Trait other) {
            return conflicts.contains(other.id) || other.conflicts.contains(id);
        }

        /** The item shown for the trait in the interface. */
        public ItemStack getIcon() {
            Item item = icon == null ? Items.PAPER : BuiltInRegistries.ITEM.getValue(icon);
            return new ItemStack(item == Items.AIR ? Items.PAPER : item);
        }

        public Component getName() {
            return Component.translatable("trait." + REGISTRY.translationSuffix(id));
        }

        public Component getDescription() {
            return Component.translatable("traitDescription." + REGISTRY.translationSuffix(id));
        }

        public boolean isUsableOnPlayer() {
            return usableOnPlayer;
        }

        public boolean isEnabled() {
            Map<String, Boolean> enabledTraits = Config.getServerConfig().enabledTraits;
            return enabledTraits.getOrDefault(id.toString(), true);
        }
    }

    public static final class Builder {
        private final Identifier id;
        private float chance = 1.0F;
        private float inherit = 0.0F;
        private boolean usableOnPlayer = true;
        private TraitCategory category = TraitCategory.GENETIC;
        private @Nullable AgeState minAge;
        private @Nullable AgeState maxAge;
        private final Set<Identifier> conflicts = new LinkedHashSet<>();
        private @Nullable Identifier icon;
        private final List<TraitEffect> effects = new ArrayList<>();
        private @Nullable Personality voice;
        private WalkStyle walk = WalkStyle.NORMAL;

        private Builder(Identifier id) {
            this.id = id;
        }

        /** The dialogue voice the trait gives; a villager speaks with the voice of one of its traits. */
        public Builder voice(Personality voice) {
            this.voice = voice;
            return this;
        }

        /** How a villager with the trait walks. */
        public Builder walk(WalkStyle walk) {
            this.walk = walk;
            return this;
        }

        /** Relative chance to be rolled for a villager. */
        public Builder chance(float chance) {
            this.chance = chance;
            return this;
        }

        /** Chance to pass on to a child, scaled by the config. */
        public Builder inherit(float inherit) {
            this.inherit = inherit;
            return this;
        }

        public Builder notOnPlayer() {
            this.usableOnPlayer = false;
            return this;
        }

        public Builder category(TraitCategory category) {
            this.category = category;
            return this;
        }

        public Builder minAge(AgeState minAge) {
            this.minAge = minAge;
            return this;
        }

        /** The last age with the trait; growing older drops it, like the traits of toddlers. */
        public Builder maxAge(AgeState maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /** Traits this one excludes; a bare path means an MCA trait. */
        public Builder conflicts(String... ids) {
            for (String other : ids) {
                REGISTRY.parse(other).ifPresent(conflicts::add);
            }
            return this;
        }

        /** The item shown for the trait; a bare path means a vanilla item. */
        public Builder icon(String item) {
            this.icon = item.indexOf(':') >= 0 ? Identifier.parse(item) : Identifier.withDefaultNamespace(item);
            return this;
        }

        public Builder effect(TraitEffect effect) {
            this.effects.add(effect);
            return this;
        }

        public Trait register() {
            return REGISTRY.register(id, registeredId -> new Trait(registeredId, this));
        }
    }
}
