package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Hunters go after wild game around their hunting rack: never pets, named or leashed animals,
 * nothing kept behind fences, and never the last few of a kind nearby.
 */
public class HunterTask extends GathererTask {
    /** Animals hunters may go after; a data pack can change the list. */
    public static final TagKey<EntityType<?>> HUNTABLE = TagKey.create(Registries.ENTITY_TYPE, MCA.locate("huntable"));

    private static final double ATTACK_REACH_SQR = 2.5 * 2.5;
    private static final int ATTACK_COOLDOWN = 20;
    /** How many others of a kind must live nearby before one may be hunted. */
    private static final int MIN_HERD = 3;
    private static final int HERD_RADIUS = 16;
    private static final int PEN_RADIUS = 5;
    private static final int COLLECT_TICKS = 60;
    /** Hunters with a bow shoot from this far, drawing for a second like a player would. */
    private static final double BOW_RANGE_SQR = 12 * 12;
    private static final int DRAW_TICKS = 20;
    private static final int SHOT_COOLDOWN = 20;

    private @Nullable Animal target;
    private @Nullable Vec3 kill;
    private int cooldown;
    private int collectTicks;

    @Override
    protected java.util.function.Predicate<ItemStack> tool() {
        return stack -> isBow(stack) || isMeleeWeapon(stack);
    }

    private static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    }

    @Override
    protected boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center) {
        int radius = workRadius();
        AABB area = new AABB(center).inflate(radius, 6, radius);
        Optional<Animal> prey = level.getEntitiesOfClass(Animal.class, area, animal -> isHuntable(level, animal)).stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr));
        if (prey.isEmpty()) {
            return false;
        }
        target = prey.get();
        kill = null;
        cooldown = 0;
        collectTicks = 0;
        return true;
    }

    private static boolean isHuntable(ServerLevel level, Animal animal) {
        if (!animal.is(HUNTABLE) || animal.isBaby() || animal.hasCustomName() || animal.isLeashed()
            || animal instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        int herd = level.getEntitiesOfClass(Animal.class, animal.getBoundingBox().inflate(HERD_RADIUS),
                other -> other != animal && other.getType() == animal.getType()).size();
        return herd >= MIN_HERD && !isPenned(level, animal.blockPosition());
    }

    /** Fences or walls around an animal mean someone keeps it. */
    private static boolean isPenned(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-PEN_RADIUS, -1, -PEN_RADIUS), pos.offset(PEN_RADIUS, 1, PEN_RADIUS))) {
            var state = level.getBlockState(near);
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (kill != null) {
            return tickCollect(level, villager, village);
        }
        if (target != null && target.isDeadOrDying()) {
            // brought down by an arrow (checked first: a dying animal is no longer alive)
            kill = target.position();
            Skills.gain(villager, Skills.GAIN_GATHER);
            return Progress.WORKING;
        }
        if (target == null || !target.isAlive()) {
            return Progress.DONE;
        }

        if (equip(villager, HunterTask::isBow)) {
            return tickShoot(villager);
        }
        equip(villager, HunterTask::isMeleeWeapon);
        if (villager.distanceToSqr(target) > ATTACK_REACH_SQR) {
            return approach(villager, target.blockPosition(), ATTACK_REACH_SQR) == ApproachTracker.Approach.STUCK
                    ? Progress.FAILED : Progress.WORKING;
        }
        resetApproach();
        villager.lookAt(target.blockPosition());
        if (cooldown-- <= 0) {
            cooldown = Skills.ticks(villager, ATTACK_COOLDOWN);
            villager.swing(villager.getDominantHand());
            ItemStack weapon = tool(villager);
            target.hurtServer(level, level.damageSources().mobAttack(villager), weapon.isEmpty() ? 3.0F : 6.0F);
            if (!weapon.isEmpty()) {
                weapon.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
            }
            if (target.isDeadOrDying()) {
                kill = target.position();
                Skills.gain(villager, Skills.GAIN_GATHER);
            }
        }
        return Progress.WORKING;
    }

    /** Keeps its distance, draws the bow while aiming and lets go once fully drawn. */
    private Progress tickShoot(VillagerEntityMCA villager) {
        if (target == null) {
            return Progress.DONE;
        }
        boolean visible = villager.getSensing().hasLineOfSight(target);
        if (villager.distanceToSqr(target) > BOW_RANGE_SQR || !visible) {
            if (villager.isUsingItem() && !visible) {
                villager.stopUsingItem();
            }
            return approach(villager, target.blockPosition(), BOW_RANGE_SQR / 2) == ApproachTracker.Approach.STUCK
                    ? Progress.FAILED : Progress.WORKING;
        }

        resetApproach();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (villager.isUsingItem()) {
            int drawn = villager.getTicksUsingItem();
            if (drawn >= Skills.ticks(villager, DRAW_TICKS)) {
                villager.stopUsingItem();
                villager.performRangedAttack(target, BowItem.getPowerForTime(drawn));
                cooldown = Skills.ticks(villager, SHOT_COOLDOWN);
            }
        } else if (cooldown-- <= 0) {
            villager.startUsingItem(villager.getDominantHand());
        }
        return Progress.WORKING;
    }

    /** Picks up what the kill dropped. */
    private Progress tickCollect(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (kill == null || collectTicks++ > COLLECT_TICKS) {
            return Progress.DONE;
        }
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(kill)).inflate(3));
        if (drops.isEmpty()) {
            return collectTicks > 10 ? Progress.DONE : Progress.WORKING;
        }
        ItemEntity nearest = drops.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).get();
        if (approach(villager, nearest.blockPosition(), 2.5) == ApproachTracker.Approach.ARRIVED || villager.distanceToSqr(nearest) < 2.0) {
            for (ItemEntity drop : drops) {
                if (villager.distanceToSqr(drop) < 4.0) {
                    collect(level, villager, village, List.of(drop.getItem().copy()));
                    drop.discard();
                }
            }
        }
        return Progress.WORKING;
    }

    @Override
    protected void reset() {
        target = null;
        kill = null;
        cooldown = 0;
        collectTicks = 0;
    }
}
