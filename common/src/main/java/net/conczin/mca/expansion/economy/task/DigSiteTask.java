package net.conczin.mca.expansion.economy.task;

import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.WorkAnimation;
import net.conczin.mca.expansion.economy.EconomyRegistry;
import net.conczin.mca.expansion.economy.ProductionSink;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Miners without dig orders work a dig site: around a sifting sieve they take sand, gravel and
 * clay off the surface, down to two blocks below the sieve, and bring the village's share to the
 * storage. Moving the sieve opens a new site.
 */
public class DigSiteTask extends GathererTask {
    /** What miners take at a dig site; a data pack can change the list. */
    public static final TagKey<Block> DIGGABLE = TagKey.create(Registries.BLOCK, MCA.locate("dig_site_diggable"));

    private static final int SITE_SEARCH_RADIUS = 64;
    private static final int SITE_RADIUS = 12;
    private static final int MAX_DEPTH = 2;
    private static final double REACH_SQR = 4.5 * 4.5;

    private @Nullable BlockPos target;
    private int progress;
    private int required;

    @Override
    protected Optional<BlockPos> workCenter(ServerLevel level, VillagerEntityMCA villager) {
        return level.getPoiManager().findClosest(holder -> holder.is(EconomyRegistry.SIFTING_SIEVE_POI),
                villager.blockPosition(), SITE_SEARCH_RADIUS, PoiManager.Occupancy.ANY);
    }

    @Override
    protected int workRadius() {
        return SITE_RADIUS;
    }

    @Override
    protected List<BlockPos> depositContainers(ServerLevel level, VillagerEntityMCA villager, Village village) {
        return VillageStorage.containers(level, village, villager.blockPosition());
    }

    @Override
    protected java.util.function.Predicate<ItemStack> tool() {
        return stack -> stack.is(ItemTags.SHOVELS);
    }

    @Override
    protected boolean findWork(ServerLevel level, VillagerEntityMCA villager, Village village, BlockPos center) {
        List<BlockPos> found = scan(level, center, SITE_RADIUS, MAX_DEPTH, 4, villager.blockPosition(), pos -> {
            BlockState state = level.getBlockState(pos);
            return state.is(DIGGABLE)
                   && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                   && !pos.equals(center.below())
                   && !isNearBuilding(village, pos, 1)
                   && !ProductionSink.isSaturated(level, village, state.getBlock().asItem());
        });
        if (found.isEmpty()) {
            return false;
        }
        target = found.getFirst();
        progress = 0;
        required = -1;
        return true;
    }

    @Override
    protected Progress tickWork(ServerLevel level, VillagerEntityMCA villager, Village village) {
        if (target == null) {
            return Progress.DONE;
        }
        BlockState state = level.getBlockState(target);
        if (!state.is(DIGGABLE)) {
            level.destroyBlockProgress(villager.getId(), target, -1);
            return Progress.DONE;
        }
        ApproachTracker.Approach approach = approach(villager, target, REACH_SQR);
        if (approach == ApproachTracker.Approach.STUCK) {
            return Progress.FAILED;
        }
        if (approach == ApproachTracker.Approach.WALKING) {
            return Progress.WORKING;
        }
        if (required < 0) {
            equip(villager, stack -> stack.is(ItemTags.SHOVELS));
            float speed = Math.max(1.0F, tool(villager).getDestroySpeed(state));
            required = Skills.ticks(villager, Math.max(10, (int) (state.getDestroySpeed(level, target) * 40 / speed)));
        }
        villager.getVillagerBrain().setWorkAnimation(WorkAnimation.CHOP, 10);
        villager.getLookControl().setLookAt(Vec3.atCenterOf(target));
        progress++;
        level.destroyBlockProgress(villager.getId(), target, Math.min(9, progress * 10 / required));
        if (progress < required) {
            return Progress.WORKING;
        }
        ItemStack held = tool(villager);
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), villager, held);
        level.destroyBlockProgress(villager.getId(), target, -1);
        level.destroyBlock(target, false, villager);
        if (!held.isEmpty()) {
            held.hurtAndBreak(1, villager, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        harvested(level, villager, village, drops);
        return Progress.DONE;
    }

    @Override
    protected void reset() {
        target = null;
        progress = 0;
        required = -1;
    }
}
