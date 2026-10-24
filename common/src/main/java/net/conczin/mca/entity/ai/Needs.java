package net.conczin.mca.entity.ai;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.conczin.mca.expansion.minds.Diet;
import net.conczin.mca.expansion.minds.HomeQuality;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.property.PropertyRules;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Villager needs, RimWorld style: meters from 0 to 100 that drop over time and refill through
 * eating, drinking (next to a cauldron with water), sleeping, company, free time and the absence of monsters. Low needs become thoughts that
 * hold part of the mood, slow the villager down and, when critical, override its schedule (sleep
 * when exhausted, hide when scared). The meters are tracked data, so clients can show them.
 */
public class Needs {
    private static final TagKey<Block> DRINKING_WATER = TagKey.create(Registries.BLOCK, MCA.locate("drinking_water"));
    public enum Need {
        HUNGER, THIRST, ENERGY, SOCIAL, FUN, SAFETY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A low (or high) need pulling the mood, with its translation key. */
    public record Thought(Need need, String key, int mood) {
    }

    public static final float MAX = 100.0F;
    private static final float START = 80.0F;
    private static final int TICK_INTERVAL = 20;
    /** Fraction of an in-game day (24000 ticks) per need tick. */
    private static final float DAY_FRACTION = TICK_INTERVAL / 24000.0F;
    private static final float HOUR_FRACTION = TICK_INTERVAL / 1000.0F;

    private static final float HUNGRY = 40.0F;
    private static final float EXHAUSTED = 15.0F;
    private static final float RESTED = 60.0F;
    private static final float SCARED = 20.0F;
    private static final float CALM = 45.0F;

    private static final double MONSTER_RANGE = 16.0;
    private static final long MONSTER_MEMORY_TICKS = 1200;
    private static final int STARVE_INTERVAL = 600;
    private static final int SOCIAL_INTERACTIONS_PER_DAY = 5;
    /** Safety regained per day without monsters around; twice as fast at home or near protection. */
    private static final float SAFETY_RECOVERY_PER_DAY = 120.0F;

    private static final Identifier TIRED_MODIFIER = MCA.locate("needs_tired");
    private static final Identifier HUNGRY_MODIFIER = MCA.locate("needs_hungry");
    private static final Identifier THIRSTY_MODIFIER = MCA.locate("needs_thirsty");
    /** Thirst regained per second next to a cauldron with water. */
    private static final float DRINK_PER_SECOND = 10.0F;
    /** Below this thirst a villager drinks what it carries. */
    private static final float DRINK_FROM_INVENTORY = 40.0F;

    private static final Map<Need, CDataParameter<Float>> METERS = new HashMap<>();
    private static final CDataParameter<Float> HUNGER = meter(Need.HUNGER, "NeedHunger");
    private static final CDataParameter<Float> THIRST = meter(Need.THIRST, "NeedThirst");
    private static final CDataParameter<Float> ENERGY = meter(Need.ENERGY, "NeedEnergy");
    private static final CDataParameter<Float> SOCIAL = meter(Need.SOCIAL, "NeedSocial");
    private static final CDataParameter<Float> FUN = meter(Need.FUN, "NeedFun");
    private static final CDataParameter<Float> SAFETY = meter(Need.SAFETY, "NeedSafety");
    /** Mood currently applied by the thoughts, so changes can be applied as a difference. */
    private static final CDataParameter<Integer> MOOD_OFFSET = CParameter.create("NeedsMood", 0);

    private final VillagerEntityMCA entity;
    private final Map<UUID, Long> seenMonsters = new HashMap<>();
    private boolean exhausted;
    private boolean scared;
    private boolean napping;
    private int lastInFavoredBuilding = -1000;
    /** Game time until which the villager remembers sleeping next to its spouse. */
    private long cuddledUntil = -1;
    private long interactionDay = -1;
    private int interactionsToday;

    public Needs(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    private static CDataParameter<Float> meter(Need need, String id) {
        CDataParameter<Float> parameter = CParameter.create(id, START);
        METERS.put(need, parameter);
        return parameter;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(HUNGER, THIRST, ENERGY, SOCIAL, FUN, SAFETY, MOOD_OFFSET);
    }

    /** Whether needs are simulated; on clients this follows the server's setting. */
    public static boolean isEnabled() {
        return Config.getServerConfig().villagerNeeds;
    }

    public float get(Need need) {
        return entity.getTrackedValue(METERS.get(need));
    }

    public void set(Need need, float value) {
        entity.setTrackedValue(METERS.get(need), Mth.clamp(value, 0.0F, MAX));
    }

    public void add(Need need, float amount) {
        set(need, get(need) + amount);
    }

    /** Babies are cared for and do not have needs. */
    public boolean hasNeeds() {
        return isEnabled() && entity.getAgeState() != AgeState.BABY;
    }

    public boolean isHungry() {
        return hasNeeds() && get(Need.HUNGER) < HUNGRY;
    }

    public boolean isNapping() {
        return napping;
    }

    public void onAte(int nutrition) {
        if (hasNeeds()) {
            add(Need.HUNGER, nutrition * 6.0F);
        }
    }

    /** A conversation, gift or other interaction with a player. */
    public void onPlayerInteraction() {
        if (!hasNeeds()) {
            return;
        }
        long day = entity.level().getGameTime() / 24000;
        if (day != interactionDay) {
            interactionDay = day;
            interactionsToday = 0;
        }
        if (interactionsToday++ < SOCIAL_INTERACTIONS_PER_DAY) {
            add(Need.SOCIAL, 10.0F);
        }
    }

    public void onHurtByMob() {
        if (hasNeeds()) {
            add(Need.SAFETY, -20.0F);
        }
    }

    /** Called while the villager spends its free time in a favored building. */
    public void onInFavoredBuilding() {
        lastInFavoredBuilding = entity.tickCount;
    }

    /**
     * What the needs make the villager do instead of its schedule, or null: hide when scared,
     * sleep when exhausted. A villager already sleeping at the scheduled time stays in bed.
     */
    public @Nullable Activity overrideActivity(Activity scheduled) {
        if (!hasNeeds()) {
            return null;
        }
        if (scared && scheduled != Activity.REST) {
            return Activity.HIDE;
        }
        if (exhausted) {
            return Activity.REST;
        }
        return null;
    }

    public boolean isOverridingSchedule() {
        return hasNeeds() && (scared || exhausted);
    }

    /** The active thoughts and their effect on the mood. */
    public List<Thought> thoughts() {
        List<Thought> thoughts = new ArrayList<>();
        if (!hasNeeds()) {
            return thoughts;
        }
        thought(thoughts, Need.HUNGER, -2, -5);
        thought(thoughts, Need.THIRST, -2, -5);
        thought(thoughts, Need.ENERGY, -2, -4);
        thought(thoughts, Need.SOCIAL, -1, -3);
        thought(thoughts, Need.FUN, -1, -3);
        thought(thoughts, Need.SAFETY, -2, -6);
        if (entity.level().getGameTime() < cuddledUntil) {
            thoughts.add(new Thought(Need.SOCIAL, "needs.thought.cuddle", 2));
        }
        thoughts.addAll(Diet.thoughts(entity)); // expansion: minds
        thoughts.addAll(HomeQuality.thoughts(entity)); // expansion: minds
        return thoughts;
    }

    /** Whether the spouse sleeps in the bed right beside this one, both facing the same way (a double bed). */
    private boolean sleepsNextToSpouse(ServerLevel level) {
        Optional<BlockPos> mine = entity.getSleepingPos();
        if (mine.isEmpty() || !(entity.getRelationships().getPartner().orElse(null) instanceof net.minecraft.world.entity.LivingEntity spouse)
            || !spouse.isSleeping()) {
            return false;
        }
        Optional<BlockPos> theirs = spouse.getSleepingPos();
        if (theirs.isEmpty() || mine.get().getY() != theirs.get().getY() || mine.get().distManhattan(theirs.get()) != 1) {
            return false;
        }
        BlockState myBed = level.getBlockState(mine.get());
        BlockState theirBed = level.getBlockState(theirs.get());
        if (!(myBed.getBlock() instanceof BedBlock) || !(theirBed.getBlock() instanceof BedBlock)) {
            return false;
        }
        Direction facing = myBed.getValue(BedBlock.FACING);
        // Side by side: same facing, and the neighbour lies across the bed, not at its head or foot.
        return facing == theirBed.getValue(BedBlock.FACING) && mine.get().relative(facing.getClockWise()).equals(theirs.get())
               || facing == theirBed.getValue(BedBlock.FACING) && mine.get().relative(facing.getCounterClockWise()).equals(theirs.get());
    }

    private void thought(List<Thought> thoughts, Need need, int low, int critical) {
        float value = get(need);
        if (value < 10.0F) {
            thoughts.add(new Thought(need, "needs.thought." + need.id() + ".critical", critical));
        } else if (value < 30.0F) {
            thoughts.add(new Thought(need, "needs.thought." + need.id() + ".low", low));
        } else if (value > 85.0F) {
            thoughts.add(new Thought(need, "needs.thought." + need.id() + ".high", 1));
        }
    }

    public void tick() {
        if (entity.tickCount % TICK_INTERVAL != 0 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        NeedIndicators.tick(entity);
        if (!hasNeeds() || entity.isNoAi()) {
            if (!hasNeeds()) {
                // Turned off: give back the mood the thoughts were holding, and the speed.
                updateMood(0);
                updateSpeed(false, false, false);
                exhausted = scared = napping = false;
            }
            return;
        }

        var config = Config.getServerConfig();
        Activity activity = entity.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        boolean working = activity == Activity.WORK;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(12.0), e -> e != entity && e.isAlive());

        // Hunger
        add(Need.HUNGER, -config.needsHungerPerDay * DAY_FRACTION * (working ? 1.5F : 1.0F));

        // Thirst: drinking means standing next to a cauldron with water, or a drink from the inventory.
        if (isNextToWater(level)) {
            add(Need.THIRST, DRINK_PER_SECOND * TICK_INTERVAL / 20.0F);
        } else {
            add(Need.THIRST, -config.needsThirstPerDay * DAY_FRACTION * (working ? 1.3F : 1.0F));
            if (get(Need.THIRST) < DRINK_FROM_INVENTORY) {
                drinkFromInventory(level);
            }
        }

        // Energy: sleeping in a bed refills it in about 8 hours, a shelter bed or a nap at half speed.
        if (entity.isSleeping()) {
            boolean shelter = memory(MemoryModuleType.HOME).isEmpty();
            // Sleeping next to the spouse, in beds side by side: more rest, some company.
            boolean together = sleepsNextToSpouse(level);
            if (together) {
                cuddledUntil = level.getGameTime() + 12000;
                add(Need.SOCIAL, 30.0F * DAY_FRACTION);
            }
            add(Need.ENERGY, MAX / 8.0F * HOUR_FRACTION * (shelter ? 0.5F : 1.0F) * (together ? 1.25F : 1.0F));
        } else if (napping) {
            add(Need.ENERGY, MAX / 16.0F * HOUR_FRACTION);
        } else {
            add(Need.ENERGY, -MAX / Math.max(1.0F, config.needsEnergyAwakeHours) * HOUR_FRACTION);
        }

        // Social: company at the meeting point.
        float social = -config.needsSocialPerDay;
        if (activity == Activity.MEET) {
            long neighbours = nearby.stream().filter(e -> e instanceof VillagerEntityMCA && e.distanceToSqr(entity) < 36.0).limit(3).count();
            social += neighbours * 60.0F;
        }
        add(Need.SOCIAL, social * DAY_FRACTION);

        // Fun: work drains it, free time and favored buildings refill it.
        float fun = working ? -config.needsFunWorkPerDay : -config.needsFunWorkPerDay / 3.0F;
        if (activity == Activity.IDLE || activity == Activity.PLAY) {
            fun += 40.0F;
        }
        if (entity.tickCount - lastInFavoredBuilding < 100) {
            fun += 80.0F;
        }
        add(Need.FUN, fun * DAY_FRACTION);

        tickSafety(level, nearby, config.needsSafetyPerMonster);

        // Critical needs override the schedule, with some slack so it does not flip back and forth.
        float energy = get(Need.ENERGY);
        exhausted = exhausted ? energy < RESTED : energy < EXHAUSTED;
        float safety = get(Need.SAFETY);
        scared = scared ? safety < CALM : safety < SCARED;
        tickNap(energy);

        // Medium consequences: starving costs health down to half a heart, low needs slow down.
        float hunger = get(Need.HUNGER);
        if (config.needsStarvation && hunger <= 0.0F && entity.tickCount % STARVE_INTERVAL < TICK_INTERVAL && entity.getHealth() > 1.0F) {
            entity.hurtServer(level, level.damageSources().starve(), Math.min(1.0F, entity.getHealth() - 1.0F));
            // Hunger pangs are no reason to panic; the crossed out bread shows why it hurts.
            entity.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
            NeedIndicators.showLack(entity, Items.BREAD);
        }
        updateSpeed(energy < 10.0F, hunger < 10.0F, get(Need.THIRST) <= 0.0F);

        updateMood(thoughts().stream().mapToInt(Thought::mood).sum());
    }

    private void tickSafety(ServerLevel level, List<LivingEntity> nearby, float perMonster) {
        long time = level.getGameTime();
        seenMonsters.values().removeIf(seen -> time - seen > MONSTER_MEMORY_TICKS);

        boolean threatened = false;
        Optional<NearestVisibleLivingEntities> visible = memory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visible.isPresent()) {
            for (LivingEntity monster : visible.get().findAll(e -> e instanceof Enemy && e.distanceToSqr(entity) < MONSTER_RANGE * MONSTER_RANGE)) {
                threatened = true;
                if (seenMonsters.put(monster.getUUID(), time) == null) {
                    add(Need.SAFETY, -perMonster);
                }
            }
        }
        if (!threatened) {
            boolean protectedHere = isAtHome()
                                    || nearby.stream().anyMatch(e -> e instanceof IronGolem || e instanceof VillagerEntityMCA v && v.isGuard());
            add(Need.SAFETY, SAFETY_RECOVERY_PER_DAY * DAY_FRACTION * (protectedHere ? 2.0F : 1.0F));
        }
    }

    /** Without a bed to go to, an exhausted villager stops where it is and naps standing. */
    private void tickNap(float energy) {
        boolean canReachBed = memory(MemoryModuleType.HOME).isPresent();
        if (!napping && energy <= 0.5F && !canReachBed && !entity.isSleeping() && !entity.getVillagerBrain().isPanicking()) {
            napping = true;
        } else if (napping && (energy >= 30.0F || entity.getVillagerBrain().isPanicking() || entity.isSleeping())) {
            napping = false;
        }
        if (napping) {
            entity.getNavigation().stop();
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    public boolean isAtHome() {
        Optional<GlobalPos> home = memory(MemoryModuleType.HOME);
        if (home.isEmpty() || home.get().dimension() != entity.level().dimension()) {
            return false;
        }
        Optional<VillageBuilding> building = entity.getResidency().getHomeVillage().flatMap(village -> PropertyRules.homeOf(village, home.get().pos()));
        return building.map(b -> PropertyRules.isInside(b, entity.blockPosition(), 0))
                .orElseGet(() -> home.get().pos().closerThan(entity.blockPosition(), 6.0));
    }

    /** A brain memory; empty also when the brain does not register it. */
    private <U> Optional<U> memory(MemoryModuleType<U> type) {
        Optional<U> value = entity.getBrain().getMemoryInternal(type);
        return value == null ? Optional.empty() : value;
    }

    private void updateSpeed(boolean tired, boolean hungry, boolean thirsty) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        setModifier(speed, TIRED_MODIFIER, tired, -0.2);
        setModifier(speed, HUNGRY_MODIFIER, hungry, -0.1);
        setModifier(speed, THIRSTY_MODIFIER, thirsty, -0.15);
    }

    /**
     * Drinks something carried: water or juice first, dirty water only when nothing else is left.
     * The empty bottle goes back into the inventory.
     */
    private void drinkFromInventory(ServerLevel level) {
        SimpleContainer inventory = entity.getInventory();
        int slot = InventoryUtils.getFirstSlotContainingItem(inventory, VillagerDrinks::isPreferred);
        if (slot < 0) {
            slot = InventoryUtils.getFirstSlotContainingItem(inventory, VillagerDrinks::isDrink);
        }
        if (slot < 0) {
            return;
        }
        ItemStack stack = inventory.getItem(slot);
        VillagerDrinks.Drink drink = VillagerDrinks.drinkOf(stack);
        if (drink == null) {
            return;
        }
        ItemStack remainder = VillagerDrinks.remainderOf(stack);
        stack.shrink(1);
        inventory.setChanged();
        if (!remainder.isEmpty()) {
            ItemStack rest = inventory.addItem(remainder);
            if (!rest.isEmpty()) {
                entity.spawnAtLocation(level, rest);
            }
        }
        add(Need.THIRST, drink.thirst());
        int mood = drink.mood();
        if (drink.poisonChance() > 0.0F && level.getRandom().nextFloat() < drink.poisonChance()) {
            // unclean water upsets the stomach: grumpy, with angry particles to show it
            mood += VillagerDrinks.SICK_MOOD;
            level.broadcastEntityEvent(entity, EntityEvent.VILLAGER_ANGRY);
        }
        if (mood != 0) {
            entity.getVillagerBrain().modifyMoodValue(mood);
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL,
                0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /** Whether a cauldron with water is within reach (2 blocks). */
    public boolean isNextToWater(ServerLevel level) {
        BlockPos feet = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            if (isDrinkingWater(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    /** A cauldron with water, or anything else in the {@code mca:drinking_water} tag with water in it (its "level" above 0). */
    public static boolean isDrinkingWater(BlockState state) {
        if (!state.is(DRINKING_WATER)) {
            return false;
        }
        Property<?> level = state.getBlock().getStateDefinition().getProperty("level");
        return !(level instanceof IntegerProperty water) || state.getValue(water) > 0;
    }

    private static void setModifier(AttributeInstance attribute, Identifier id, boolean active, double amount) {
        if (active) {
            if (!attribute.hasModifier(id)) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (attribute.hasModifier(id)) {
            attribute.removeModifier(id);
        }
    }

    private void updateMood(int target) {
        int current = entity.getTrackedValue(MOOD_OFFSET);
        if (current != target) {
            entity.getVillagerBrain().modifyMoodValue(target - current);
            entity.setTrackedValue(MOOD_OFFSET, target);
        }
    }
}
