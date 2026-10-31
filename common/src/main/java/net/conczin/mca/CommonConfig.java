package net.conczin.mca;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonConfig {
    /**
     * With squidward models, keeps the genetic and age based size of MCA villagers for their hitbox,
     * as without squidward models, instead of the vanilla villager size. Pairs with human-like
     * squidward villagers, which already draw that size.
     */
    public boolean squidwardGeneticHitbox = false;

    /**
     * Villager needs (hunger, energy, social, fun, safety): meters that drop over time, pull the
     * mood when low and, when critical, make villagers eat, sleep, socialize or hide.
     */
    public boolean villagerNeeds = false;
    /**
     * The Sims style action queue: villagers queue what their schedule, needs and players' orders
     * ask for and do the most urgent first; shown over their heads and on the interaction screen.
     */
    public boolean villagerActionQueue = false;
    /** Hunger lost per in-game day (meters go from 0 to 100); working uses 1.5 times as much. */
    public float needsHungerPerDay = 60.0f;
    /** In-game hours awake until energy runs out; sleeping refills it in about 8 hours. */
    public float needsEnergyAwakeHours = 16.0f;
    public float needsSocialPerDay = 50.0f;
    /** Fun lost per in-game day while working (a third of it otherwise). */
    public float needsFunWorkPerDay = 60.0f;
    /** Safety lost for each new monster seen nearby. */
    public float needsSafetyPerMonster = 15.0f;
    /** Whether starving villagers lose health, down to half a heart (they never die of it). */
    public boolean needsStarvation = true;
    /** Thirst lost per in-game day; villagers drink by standing next to a cauldron with water. */
    public float needsThirstPerDay = 70.0f;

    /**
     * Property: beds, job sites, chests, doors and trapdoors belong to someone. Villagers own their
     * bed, job site and home (with what is inside it); players own what they place, with a lock.
     */
    public boolean villagerProperty = false;
    /** Hearts with an owner that let a friend use their doors, chests and job site (not the bed). */
    public int propertyFriendHearts = 50;
    /** Whether operators ignore property like creative players do (turn off to test it in singleplayer). */
    public boolean propertyOpsBypass = true;
    /** How far villagers can see a crime (halved when sneaking, halved again in the dark). */
    public float crimeSightRange = 24.0f;
    /** Hearts lost with a villager who witnesses a crime, and with the owner of what was taken. */
    public int crimeWitnessHearts = 10;
    public int crimeOwnerHearts = 25;
    /** Seconds to force a lock open by sneaking and holding use, and with a lockpick. */
    public float forceOpenSeconds = 3.0f;
    public float lockpickSeconds = 5.0f;

    /**
     * If true, arrows fired by MCA archers pass through villagers instead of hitting them.
     */
    public boolean archerArrowsIgnoreVillagers = true;

    /**
     * If true, MCA villagers may path through and open or close fence gates.
     */
    public boolean villagersInteractWithFenceGates = true;

    /**
     * If true, MCA villagers may open or close any DoorBlock. Otherwise,
     * DoorBlocks must be in #minecraft:mob_interactable_doors.
     * Dramatic Doors follows its own interaction rules.
     */
    public boolean villagersInteractWithAnyDoor = false;

    /**
     * If true, binding a villager to a work station of another profession changes
     * its profession, resets its level and re-rolls its trades. If false, an
     * existing profession is kept. Villagers without a profession still adopt one.
     */
    public boolean villagersChangeProfession = true;

    /**
     * If true, a villager called with the whistle also starts following the player.
     */
    public boolean whistleCallMakesVillagersFollow = false;

    /**
     * If true, players can plan dig and build work orders for their village (Blueprint book,
     * Villager Editor or /mca workorder), and builders and miners carry them out.
     */
    public boolean villagerWorkOrders = false;

    /**
     * Village economy expansion: lumberjacks, hunters and foragers gather base resources, workshops
     * really produce, and haulers carry goods between workshop chests and the village storage.
     * The village's share of everything produced follows the tax slider and replaces item taxes.
     */
    public boolean villagerEconomy = false;
    /** Blocks around their work station in which gatherers look for work. */
    public int economyWorkRadius = 24;
    /** Gatherers and workshops stop making an item once the village storage holds this many of it. */
    public int economyStorageCap = 128;
    /** A village with a storage gets one hauler for every this many workshops. */
    public int economyWorkshopsPerHauler = 5;

    /**
     * Smarter villagers: they get better at their profession by working (faster, extra and rarer
     * output), learn according to their intelligence gene, want varied food, care about their home
     * and pick their job by aptitude.
     */
    public boolean villagerSkills = false;

    /**
     * Villager requests: villagers ask for deliveries and set the reward aside from their home
     * chests; delivering earns hearts, letting it run out costs some.
     */
    public boolean villagerRequests = false;

    /**
     * Information behind conversation: the interaction screen shows a villager's feelings, work, traits and friends
     * only once asked in the conversation ("How are you?", "Where do you work?"...).
     */
    public boolean villagerInfoInTalk = false;

    /**
     * Villager attributes: strength, perception, vigor, dexterity, wisdom and social, 1 to 10. Work gives experience
     * and levels; the village chief spends the points. How well they fit a profession speeds up its work.
     */
    public boolean villagerAttributes = false;

    /**
     * Mental breaks (from RimWorld): a villager whose mood stays very low for long snaps, throws a tantrum, sulks,
     * binges or picks a fist fight with someone it dislikes. Iron-willed and volatile traits move the line.
     */
    public boolean villagerMentalBreaks = false;
    /** Whether a fight of a mental break may go on until someone dies; otherwise it stops before. */
    public boolean villagerMentalBreakLethal = false;

    /** Whether each village gets its City Records next to its bell when first seen, so the town hall is there from the start. */
    public boolean villageTownHallAtStart = true;

    /**
     * Caravans: three to five villagers with pack animals roam between the villages players know, trading on the way.
     * With them on, the wandering trader comes only one time in {@link #wanderingTraderRarity}.
     */
    public boolean villagerCaravans = false;
    /** How many caravans can be on the roads of a world at once. */
    public int caravanMaxActive = 2;
    /** Chance per minute, at a known village with a player in it, that a caravan turns up. */
    public float caravanChancePerMinute = 0.05F;
    /** How long a caravan stays at a village, in ticks (24000 = one day). */
    public int caravanStayTime = 24000;
    /** How far a caravan travels to its next village, in blocks. */
    public int caravanMaxDistance = 2000;
    /** With caravans on, the wandering trader comes only one time in this many. */
    public int wanderingTraderRarity = 4;

    /**
     * Factions: the pillagers come in bands with a name, a banner and outposts; raiding villages wins their respect,
     * killing their people loses it, and a band that respects a player leaves them alone.
     */
    public boolean villagerFactions = false;
    /** How many factions a world starts with. */
    public int factionCount = 5;
    /** The most factions there can be; outposts far from every band start new ones up to this. */
    public int factionMax = 8;
    /** How far around an outpost its faction's land reaches, in blocks. */
    public int factionTerritoryRadius = 96;
    /** What every player starts at with every faction (below 0 is hostile). */
    public int factionStartReputation = -15;
    /** Reputation lost with a faction for killing one of its people. */
    public int factionKillPenalty = 15;
    /** Whether pillagers outside raids become MCA people of their band (bandits), to look and talk like villagers. */
    public boolean factionPillagersAsPeople = true;
    /** Whether factions go to war with each other, take outposts and send war parties. */
    public boolean factionWars = true;
    /** Emeralds a band asks to leave a village alone, and for how many days. */
    public int factionTruceCost = 24;
    public int factionTruceDays = 7;

    /**
     * Haggling: any villager sells what it carries and buys what it needs, whatever the
     * friendship (friends get better prices); anyone may ask what it carries.
     */
    public boolean villagerBarter = false;

    /**
     * Village progression: buildings grow in tiers (small to huge houses, workshops with more
     * stations) and villages follow a guided path of stages, shown in the village screen.
     */
    public boolean villageProgression = false;

    /**
     * Free time with a purpose: villagers use what their surroundings offer (benches to sit on,
     * friends to chat with, lecterns, music, fires, pets, flowers, water, home) instead of walking
     * to random spots; when nothing is around they at least stand with the others.
     */
    public boolean villagerRoutines = false;

    /**
     * Time (in ticks) until a baby grows up when held as an item.
     */
    public int babyItemGrowUpTime = 24000;

    /**
     * Maximum villager lifetime in ticks (Time to grow fully up).
     */
    public int villagerMaxAgeTime = 384000;

    /**
     * Time (in ticks) a villager lives as an adult before it becomes an elder: slower, a little
     * shorter, no more children. 0 turns elders off.
     */
    public int villagerElderAfterTime = 480000;

    /**
     * Whether very old villagers may die of old age, peacefully in their sleep. After
     * villagerOldAgeDeathAfterTime ticks as an elder, each night has villagerOldAgeDeathChance.
     */
    public boolean villagerOldAgeDeath = false;
    public int villagerOldAgeDeathAfterTime = 480000;
    public float villagerOldAgeDeathChance = 0.1f;

    /**
     * Permission level required to add or remove skins from the server wide pool.
     * On singleplayer the world owner always has access.
     * Set to 0 to allow everyone.
     */
    public int addContentGloballyPermissionLevel = 3;

    /**
     * Allow players to modify their size.
     */
    public boolean allowPlayerSizeAdjustment = true;

    /**
     * If true, player hitboxes use MCA size and width genetics.
     * Disable this to keep full vanilla player hitboxes for PvP.
     */
    public boolean scalePlayerHitboxWithSizeAndWidth = false;

    /**
     * Whether body customization (e.g., height, size) is available in the Destiny editor.
     */
    public boolean allowBodyCustomizationInDestiny = true;

    /**
     * Whether trait customization is available in the Destiny editor.
     */
    public boolean allowTraitCustomizationInDestiny = true;

    /**
     * Locations where the Destiny feature can teleport the player.
     * <a href="https://github.com/Luke100000/minecraft-comes-alive/wiki/Custom-Rumors-and-Destiny-Structures">Wiki</a>
     */
    public List<String> destinySpawnLocations = List.of(
            "somewhere",
            "minecraft:shipwreck_beached",
            "minecraft:village_desert",
            "minecraft:village_taiga",
            "minecraft:village_snowy",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:ancient_city"
    );

    /**
     * Automatically adds registered village structures to the Destiny screen.
     * Disable this to use {@link #destinySpawnLocations} as the complete manual list.
     */
    public boolean autoDiscoverDestinyLocations = true;

    /**
     * Removes matching locations from the Destiny screen after manual and automatic locations are combined.
     * Supports simple '*' wildcards, including whole namespaces such as "ctov:*".
     */
    public List<String> destinySpawnLocationBlacklist = List.of();

    /**
     * Restricts dimension-bound Destiny destinations to the Overworld.
     * Dimensionless choices such as "somewhere" remain available.
     */
    public boolean destinyOverworldOnly = false;

    /**
     * Removes Destiny destinations from matching dimensions after Minecraft determines where they can generate.
     * Supports simple '*' wildcards, including whole namespaces such as "some_mod:*".
     */
    public List<String> destinyDimensionBlacklist = List.of();

    /**
     * Maps Destiny locations to translation keys for UI text.
     */
    public Map<String, String> destinyLocationsToTranslationMap = Map.of(
            "default", "destiny.story.travelling",
            "minecraft:shipwreck_beached", "destiny.story.sailing"
    );

    /**
     * Map of enabled traits. Keys are trait IDs, values are true/false.
     */
    public Map<String, Boolean> enabledTraits = new HashMap<>();

    /**
     * Personality traits (in the style of The Sims) a villager has per age. Genetic traits
     * such as albinism or left handedness do not count. Babies and toddlers have none.
     */
    /** Toddler traits (wild, clingy, ...) are left behind when growing up; none by default. */
    public int traitSlotsToddler = 0;
    public int traitSlotsChild = 1;
    public int traitSlotsTeen = 3;
    public int traitSlotsAdult = 5;
    public int traitSlotsElder = 5;

}
