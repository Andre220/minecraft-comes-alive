package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.gson.JsonSyntaxException;
import net.conczin.mca.Config;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recognising and cutting down trees, shared by the chopping chore and lumberjacks.
 */
public final class TreeFelling {
    private TreeFelling() {
    }

    /**
     * Returns trues if origin is bottom point of tree.
     */
    public static boolean isTreeStartLog(ServerLevel world, BlockPos origin) {
        if (!world.getBlockState(origin).is(BlockTags.LOGS)) {
            return false;
        }

        // ensure we're looking at a valid tree before continuing
        if (!isValidTree(world, origin.below())) {
            return false;
        }

        // check upside continues and valid leaves exist.
        BlockPos.MutableBlockPos posUp = origin.mutable(); // copy as mutable for reduce resources
        for (int y = 0; y < Config.getInstance().maxTreeHeight; y++) {
            BlockState up = world.getBlockState(posUp.setY(posUp.getY() + 1)); // use set directly instead of "pos_up.move(Direction.UP)" (set is faster)
            if (up.is(BlockTags.LOGS)) {
                continue;
            } else return up.is(BlockTags.LEAVES);
        }
        return false;
    }

    /** Ticks needed to cut the whole trunk above {@code origin} with {@code tool}. */
    public static int ticksToFell(ServerLevel world, BlockPos origin, ItemStack tool) {
        int ticks = 0;
        BlockPos pos = origin;
        BlockState state;
        while ((state = world.getBlockState(pos)).is(BlockTags.LOGS)) {
            ticks += (int) (getTicksFor(state, 60) / tool.getDestroySpeed(state));
            pos = pos.offset(0, 1, 0);
        }
        return ticks;
    }

    /**
     * Cuts the trunk above {@code origin}, wearing {@code stack}.
     *
     * @return the logs cut
     */
    public static List<ItemStack> fell(ServerLevel world, BlockPos origin, LivingEntity breaker, ItemStack stack) {
        List<ItemStack> logs = new ArrayList<>();
        BlockPos pos = origin;
        BlockState state;

        while ((state = world.getBlockState(pos)).is(BlockTags.LOGS)) {
            if (world.destroyBlock(pos, false, breaker)) {
                pos = pos.offset(0, 1, 0);
                logs.add(new ItemStack(state.getBlock(), 1));
                if (!stack.isEmpty()) {
                    stack.hurtAndBreak(1, breaker, EquipmentSlot.MAINHAND);
                }
            } else {
                break;
            }
        }
        return logs;
    }

    private static boolean isValidTree(ServerLevel world, BlockPos pos) {
        // Similar logic to WanderOrTeleportToTargetTask#isAreaSafe
        final BlockState state = world.getBlockState(pos);
        final Identifier stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String blockId : Config.getInstance().validTreeSources) {
            if (blockId.equals(stateId.toString())) {
                return true;
            } else if (blockId.charAt(0) == '#') {
                Identifier identifier = Identifier.parse(blockId.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (!RegistryHelper.isTagEmpty(tag)) {
                    if (state.is(tag)) {
                        return true;
                    }
                } else {
                    throw new JsonSyntaxException("Unknown block tag in validTreeSources '" + identifier + "'");
                }
            }
        }
        return false;
    }

    private static int getTicksFor(BlockState state, int fallback) {
        // Similar logic to WanderOrTeleportToTargetTask#isAreaSafe
        final Map<String, Integer> sources = Config.getInstance().maxTreeTicks;
        final Identifier stateId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String blockId : sources.keySet()) {
            if (blockId.equals(stateId.toString())) {
                return sources.get(blockId);
            } else if (blockId.charAt(0) == '#') {
                Identifier identifier = Identifier.parse(blockId.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (!RegistryHelper.isTagEmpty(tag)) {
                    if (state.is(tag)) {
                        return sources.get(blockId);
                    }
                } else {
                    throw new JsonSyntaxException("Unknown block tag in maxTreeTicks '" + identifier + "'");
                }
            }
        }
        return fallback;
    }
}
