package net.conczin.mca.entity.ai.traits;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.HomeChests;
import net.conczin.mca.server.property.PropertyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What some traits make a villager do by itself: kleptomaniacs swipe things from other people's
 * containers they pass by, clumsy villagers stumble now and then. Runs on the server, once a second.
 */
public final class TraitBehaviors {
    /** Chance per second of free time next to someone else's container to swipe something. */
    private static final float SWIPE_CHANCE = 1.0F / 60.0F;
    private static final double SWIPE_REACH = 2.5;
    private static final int SWIPE_WITNESS_OPINION = -10;
    /** Ticks a swipe keeps a kleptomaniac content, and until it gets itchy again. */
    public static final long SWIPE_CONTENT = 12000;
    public static final long SWIPE_ITCH = 48000;
    /** Chance per second of walking to stumble. */
    private static final float STUMBLE_CHANCE = 1.0F / 300.0F;
    private static final float STUMBLE_DROP_CHANCE = 0.3F;

    /** When each kleptomaniac last swiped something; only the server knows, and it forgets on restarts. */
    private static final Map<VillagerEntityMCA, Long> LAST_SWIPE = new WeakHashMap<>();

    private TraitBehaviors() {
    }

    public static void tick(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getTraits().hasTrait(PersonalityTraits.KLEPTOMANIAC)) {
            LAST_SWIPE.putIfAbsent(villager, level.getGameTime());
            tickKleptomania(level, villager);
        }
        if (villager.getTraits().hasTrait(PersonalityTraits.CLUMSY)) {
            tickClumsiness(level, villager);
        }
        if (villager.getTraits().hasTrait(PersonalityTraits.PYROMANIAC)) {
            tickPyromania(level, villager);
        }
    }

    /** Chance per second of free time for a pyromaniac to light an unlit campfire or candle nearby. */
    private static final float LIGHT_CHANCE = 1.0F / 120.0F;

    private static void tickPyromania(ServerLevel level, VillagerEntityMCA villager) {
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity == Activity.WORK || villager.isSleeping() || level.getRandom().nextFloat() >= LIGHT_CHANCE) {
            return;
        }
        BlockPos feet = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -1, -3), feet.offset(3, 2, 3))) {
            BlockState state = level.getBlockState(pos);
            if ((state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES)) && state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT) && !state.getValueOrElse(BlockStateProperties.WATERLOGGED, false)) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                villager.getVillagerBrain().modifyMoodValue(1);
                return;
            }
        }
    }

    /**
     * A pyromaniac's mental break: a fire on bare ground a few blocks away, with nothing that burns around it, so it
     * dies out by itself. False when there is no such spot.
     */
    public static boolean setSafeFire(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos feet = villager.blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = feet.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4);
            if (pos.distManhattan(feet) < 3 || !level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                continue;
            }
            boolean flammable = false;
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
                if (level.getBlockState(near).ignitedByLava()) {
                    flammable = true;
                    break;
                }
            }
            if (!flammable) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /** Ticks since the villager last swiped something, or -1 when it is not known (e.g. on clients). */
    public static long sinceLastSwipe(VillagerEntityMCA villager) {
        Long last = LAST_SWIPE.get(villager);
        return last == null ? -1 : villager.level().getGameTime() - last;
    }

    private static void tickKleptomania(ServerLevel level, VillagerEntityMCA villager) {
        Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        if (activity != Activity.IDLE && activity != Activity.MEET || villager.isSleeping() || level.getRandom().nextFloat() >= SWIPE_CHANCE) {
            return;
        }
        BlockPos feet = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            if (pos.distToCenterSqr(villager.position()) > SWIPE_REACH * SWIPE_REACH) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof Container container && isSomeoneElses(level, villager, pos)
                && swipe(level, villager, container, pos.immutable())) {
                return;
            }
        }
    }

    /**
     * A container of someone else: owned by others and not padlocked, or, without the property
     * rules, any container outside the villager's own home.
     */
    private static boolean isSomeoneElses(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (!PropertyRules.isEnabled()) {
            return !HomeChests.of(level, villager).contains(pos);
        }
        PropertyRules.Owners owners = PropertyRules.ownersAt(level, pos);
        return owners != null && !owners.villagers().contains(villager.getUUID()) && (owners.player() == null || !owners.locked());
    }

    private static boolean swipe(ServerLevel level, VillagerEntityMCA villager, Container container, BlockPos pos) {
        int size = container.getContainerSize();
        int start = level.getRandom().nextInt(Math.max(1, size));
        for (int i = 0; i < size; i++) {
            int slot = (start + i) % size;
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack taken = stack.copyWithCount(1);
            if (!villager.getInventory().canAddItem(taken)) {
                return false;
            }
            stack.shrink(1);
            container.setChanged();
            villager.getInventory().addItem(taken);
            LAST_SWIPE.put(villager, level.getGameTime());
            villager.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.4F);
            // villagers seeing it think less of the thief
            level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8.0),
                            v -> v != villager && v.isAlive() && !v.isSleeping() && v.hasLineOfSight(villager))
                    .forEach(witness -> witness.getOpinions().change(villager, SWIPE_WITNESS_OPINION));
            return true;
        }
        return false;
    }

    private static void tickClumsiness(ServerLevel level, VillagerEntityMCA villager) {
        boolean walking = villager.onGround() && villager.getNavigation().isInProgress() && !villager.isPassenger();
        if (!walking || level.getRandom().nextFloat() >= STUMBLE_CHANCE) {
            return;
        }
        villager.getNavigation().stop();
        level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.1, villager.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
        level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_SMALL_FALL, SoundSource.NEUTRAL, 0.6F, 1.2F);
        if (level.getRandom().nextFloat() < STUMBLE_DROP_CHANCE) {
            Container inventory = villager.getInventory();
            int slot = level.getRandom().nextInt(inventory.getContainerSize());
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                villager.spawnAtLocation(level, stack.split(1));
                inventory.setChanged();
            }
        }
    }
}
