package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Fishermen fish from the shore near their barrel and roll the vanilla fishing loot; the barrel
 * is their workshop chest.
 */
public class FisherTask extends GathererTask {
    private static final int RADIUS = 12;
    private static final double REACH_SQR = 2.0 * 2.0;
    private static final int MIN_FISHING_TICKS = 200;
    private static final int MAX_FISHING_TICKS = 400;

    private @Nullable BlockPos shore;
    private @Nullable BlockPos water;
    private int progress;
    private int required;

    @Override
    protected int workRadius() {
        return RADIUS;
    }

    @Override
    protected java.util.function.Predicate<ItemStack> tool() {
        return stack -> stack.is(Items.FISHING_ROD);
    }

    @Override
    protected boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center) {
        if (ProductionSink.isSaturated(level, village, Items.COD)) {
            return false;
        }
        List<BlockPos> waters = scan(level, center, RADIUS, 3, 2, villager.blockPosition(), pos ->
                level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource()
                && level.getBlockState(pos.above()).isAir() && shoreOf(level, pos) != null);
        if (waters.isEmpty()) {
            return false;
        }
        water = waters.getFirst();
        shore = shoreOf(level, water);
        progress = 0;
        required = -1;
        return shore != null;
    }

    /** A dry spot next to the water a villager can stand on. */
    private static @Nullable BlockPos shoreOf(ServerLevel level, BlockPos water) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos feet = water.relative(direction).above();
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    @Override
    protected Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (shore == null || water == null) {
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, shore, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.WALKING) {
            return Progress.WORKING;
        }
        villager.lookAt(water);
        if (required < 0) {
            boolean rod = equip(villager, stack -> stack.is(Items.FISHING_ROD));
            required = MIN_FISHING_TICKS + villager.getRandom().nextInt(MAX_FISHING_TICKS - MIN_FISHING_TICKS);
            if (!rod) {
                required *= 2;
            }
            required = Skills.ticks(villager, required);
            level.playSound(null, water, SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
        }
        if (++progress < required) {
            return Progress.WORKING;
        }
        villager.swing(villager.getDominantHand());
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1.0F);
        harvested(level, villager, village, catchFish(level, villager, water));
        ItemStack rod = tool(villager);
        if (rod.is(Items.FISHING_ROD)) {
            rod.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        }
        return Progress.DONE;
    }

    private static List<ItemStack> catchFish(ServerLevel level, VillagerEntityMCA villager, BlockPos water) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        ItemStack rod = villager.getItemInHand(villager.getDominantHand());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, rod, villager))
                .create(LootContextParamSets.FISHING);
        return table.getRandomItems(params);
    }

    @Override
    protected void reset() {
        shore = null;
        water = null;
        progress = 0;
        required = -1;
    }
}
