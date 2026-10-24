package net.conczin.mca.expansion.minds;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Archers practise at a target block in the daytime: they find one, step back to a few blocks away with a clear
 * shot, draw the bow and loose arrows at it, then go back to their patrol for a while. Never at night.
 */
public class ArcheryPracticeTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 24;
    private static final int MIN_DISTANCE = 6;
    private static final int MAX_DISTANCE = 12;
    private static final int DRAW_TICKS = 20;
    private static final int BETWEEN_SHOTS = 30;
    private static final int SHOTS = 10;
    private static final int WALK_TIMEOUT = 400;
    /** A day ends at dusk, in ticks of the day clock. */
    private static final long DUSK = 12000;
    private static final Map<UUID, Long> RESTING_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PRACTISING = new ConcurrentHashMap<>();

    private @Nullable BlockPos target;
    private @Nullable BlockPos spot;
    private int shots;
    private int ticks;
    private int pause;

    public ArcheryPracticeTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), 2400);
    }

    public static boolean enabled() {
        return Config.getInstance().villagerArcheryPractice;
    }

    /** Whether an archer is practising now (its patrol waits meanwhile). */
    public static boolean isPractising(VillagerEntityMCA villager) {
        return PRACTISING.getOrDefault(villager.getUUID(), false);
    }

    private static boolean daytime(ServerLevel level) {
        return Math.floorMod(level.getOverworldClockTime(), 24000L) < DUSK;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!enabled() || villager.getProfession() != ProfessionsMCA.ARCHER || !daytime(level)
            || level.getGameTime() < RESTING_UNTIL.getOrDefault(villager.getUUID(), 0L)
            || RangedWeaponHelper.getBowHoldingHand(villager) == null) {
            return false;
        }
        RESTING_UNTIL.put(villager.getUUID(), level.getGameTime() + 200); // look again in a while if nothing is found
        target = findTarget(level, villager);
        spot = target == null ? null : findSpot(level, target);
        return spot != null;
    }

    static @Nullable BlockPos findTarget(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS), origin.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(Blocks.TARGET) && (best == null || pos.distSqr(origin) < best.distSqr(origin))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    /** A place a few blocks away from the target, on the ground, with a clear shot at it. */
    static @Nullable BlockPos findSpot(ServerLevel level, BlockPos target) {
        for (int distance = MIN_DISTANCE; distance <= MAX_DISTANCE; distance += 2) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos feet = target.relative(side, distance).above(dy);
                    if (canStand(level, feet) && clearShot(level, Vec3.atBottomCenterOf(feet).add(0, 1.5, 0), target)) {
                        return feet;
                    }
                }
            }
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
               && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
               && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static boolean clearShot(ServerLevel level, Vec3 eyes, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(eyes, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        shots = 0;
        ticks = 0;
        pause = 0;
        PRACTISING.put(villager.getUUID(), true);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return target != null && spot != null && shots < SHOTS && daytime(level)
               && level.getBlockState(target).is(Blocks.TARGET)
               && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
               && RangedWeaponHelper.getBowHoldingHand(villager) != null;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (target == null || spot == null) {
            return;
        }
        ticks++;
        if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(spot)) > 1.5 * 1.5) {
            if (ticks > WALK_TIMEOUT) {
                shots = SHOTS; // can't get there: give up for now
            } else {
                villager.moveTowardsPersistent(spot, 0.5F, 0);
            }
            return;
        }
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        villager.getLookControl().setLookAt(Vec3.atCenterOf(target));

        InteractionHand hand = RangedWeaponHelper.getBowHoldingHand(villager);
        if (hand == null) {
            return;
        }
        if (villager.isUsingItem()) {
            int drawn = villager.getTicksUsingItem();
            if (drawn >= DRAW_TICKS) {
                villager.stopUsingItem();
                shoot(level, villager, villager.getItemInHand(hand), BowItem.getPowerForTime(drawn));
                shots++;
                pause = BETWEEN_SHOTS + villager.getRandom().nextInt(20);
            }
        } else if (pause-- <= 0) {
            villager.startUsingItem(hand);
        }
    }

    /** Looses an arrow at the target's middle; it can't be picked up and goes away like any stuck arrow. */
    private void shoot(ServerLevel level, VillagerEntityMCA villager, ItemStack bow, float power) {
        ItemStack projectile = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(villager, projectile, power, bow);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 shot = RangedWeaponHelper.calculateBowShotVector(arrow.position(), Vec3.atCenterOf(target), 0.0);
        Projectile.spawnProjectileUsingShoot(arrow, level, projectile, shot.x, shot.y, shot.z, 1.6F, 3.0F);
        villager.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (villager.isUsingItem()) {
            villager.stopUsingItem();
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        PRACTISING.remove(villager.getUUID());
        // a few minutes of patrol before the next round
        RESTING_UNTIL.put(villager.getUUID(), level.getGameTime() + 2400 + villager.getRandom().nextInt(2400));
        target = null;
        spot = null;
    }
}
