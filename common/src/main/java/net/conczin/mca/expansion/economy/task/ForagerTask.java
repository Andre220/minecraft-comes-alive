package net.conczin.mca.expansion.economy.task;

import net.minecraft.world.phys.Vec3;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Foragers gather what grows on its own around their basket: berries (the bush stays), the tops
 * of sugar cane and bamboo, mushrooms and flowers where there are plenty, and wild pumpkins and
 * melons. Farmland and everything near the village's buildings stay the farmers' and owners'.
 */
public class ForagerTask extends GathererTask {
    private static final double REACH_SQR = 3.5 * 3.5;
    private static final int WORK_TICKS = 20;
    /** Mushrooms and flowers are only picked where at least this many of a kind grow close by. */
    private static final int MIN_PATCH = 3;
    private static final int PATCH_RADIUS = 3;

    private @Nullable BlockPos target;
    private int progress;

    @Override
    protected boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center) {
        List<BlockPos> found = scan(level, center, workRadius(), 6, 8, villager.blockPosition(), pos -> {
            BlockState state = level.getBlockState(pos);
            return isRipe(level, pos, state)
                   && !isNearBuilding(village, pos, 3)
                   && !ProductionSink.isSaturated(level, village, produce(state).getItem());
        });
        if (found.isEmpty()) {
            return false;
        }
        target = found.getFirst();
        progress = 0;
        return true;
    }

    private static boolean isRipe(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (state.getBlock() instanceof CaveVines) {
            return state.getValue(CaveVines.BERRIES);
        }
        if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)) {
            // the second segment: cutting it takes everything above and leaves the root
            return level.getBlockState(pos.below()).is(state.getBlock())
                   && !level.getBlockState(pos.below(2)).is(state.getBlock());
        }
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            return isWild(level, pos);
        }
        if (state.getBlock() instanceof MushroomBlock || state.is(BlockTags.SMALL_FLOWERS)) {
            return countNearby(level, pos, state.getBlock()) >= MIN_PATCH;
        }
        return false;
    }

    /** Pumpkins and melons no stem grew, on natural ground. */
    private static boolean isWild(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).getBlock() instanceof AttachedStemBlock) {
                return false;
            }
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) && !below.is(Blocks.FARMLAND);
    }

    private static int countNearby(ServerLevel level, BlockPos pos, Block block) {
        int count = 0;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-PATCH_RADIUS, -1, -PATCH_RADIUS), pos.offset(PATCH_RADIUS, 1, PATCH_RADIUS))) {
            if (level.getBlockState(near).is(block)) {
                count++;
            }
        }
        return count;
    }

    /** What a ripe spot mainly gives, for the storage limit. */
    private static ItemStack produce(BlockState state) {
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            return new ItemStack(Items.SWEET_BERRIES);
        }
        if (state.getBlock() instanceof CaveVines) {
            return new ItemStack(Items.GLOW_BERRIES);
        }
        if (state.is(Blocks.MELON)) {
            return new ItemStack(Items.MELON_SLICE);
        }
        return new ItemStack(state.getBlock());
    }

    @Override
    protected Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (target == null) {
            return Progress.DONE;
        }
        BlockState state = level.getBlockState(target);
        if (!isRipe(level, target, state)) {
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, target, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.WALKING) {
            return Progress.WORKING;
        }
        // crouched over the plant, see crouch_forager.json
        villager.getVillagerBrain().setWorkAnimation(WorkAnimation.CROUCH, 10);
        villager.getLookControl().setLookAt(Vec3.atCenterOf(target));
        progress++;
        if (progress < Skills.ticks(villager, WORK_TICKS)) {
            return Progress.WORKING;
        }
        harvested(level, villager, village, harvest(level, villager, target, state));
        return Progress.DONE;
    }

    private static List<ItemStack> harvest(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, BlockState state) {
        List<ItemStack> drops = new ArrayList<>();
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            drops.add(new ItemStack(Items.SWEET_BERRIES, 1 + level.getRandom().nextInt(2) + (age == 3 ? 1 : 0)));
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
        } else if (state.getBlock() instanceof CaveVines) {
            drops.add(new ItemStack(Items.GLOW_BERRIES));
            level.setBlock(pos, state.setValue(CaveVines.BERRIES, false), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)) {
            // top down, so nothing above breaks off and scatters
            BlockPos top = pos;
            while (level.getBlockState(top.above()).is(state.getBlock())) {
                top = top.above();
            }
            for (BlockPos at = top; at.getY() >= pos.getY(); at = at.below()) {
                drops.addAll(breakBlock(level, villager, at));
            }
        } else {
            drops.addAll(breakBlock(level, villager, pos));
        }
        return drops;
    }

    private static List<ItemStack> breakBlock(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, villager.getMainHandItem());
        level.destroyBlock(pos, false, villager);
        return drops;
    }

    @Override
    protected void reset() {
        target = null;
        progress = 0;
    }
}
