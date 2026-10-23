package net.conczin.mca.expansion.minds.routine;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.minds.Diet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Food served on the table: feasts with servings (a roast chicken, a shepherd's pie) and pies or
 * cakes eaten bite by bite. Works on any block that counts its servings down or its bites up, so
 * other mods' feasts only need a data file.
 */
public final class Feasts {
    private static final int SERVING_NUTRITION = 6;

    private Feasts() {
    }

    public static boolean hasServing(BlockState state) {
        if (property(state, "servings") instanceof IntegerProperty servings) {
            return state.getValue(servings) > 0;
        }
        return property(state, "bites") instanceof IntegerProperty;
    }

    /** Takes a serving from the block and eats it; false if nothing was left. */
    public static boolean takeServing(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack food = new ItemStack(state.getBlock().asItem());
        if (property(state, "servings") instanceof IntegerProperty servings) {
            int left = state.getValue(servings);
            if (left <= 0) {
                return false;
            }
            level.setBlockAndUpdate(pos, state.setValue(servings, left - 1));
        } else if (property(state, "bites") instanceof IntegerProperty bites) {
            int eaten = state.getValue(bites);
            int max = bites.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            if (eaten >= max) {
                level.removeBlock(pos, false); // the last piece
            } else {
                level.setBlockAndUpdate(pos, state.setValue(bites, eaten + 1));
            }
        } else {
            return false;
        }
        villager.getNeeds().onAte(Math.round(SERVING_NUTRITION * Diet.onAte(villager, food)));
        level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), villager.getSoundSource(), 0.6F, 1.0F);
        return true;
    }

    private static @Nullable Property<?> property(BlockState state, String name) {
        return state.getBlock().getStateDefinition().getProperty(name);
    }
}
