package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wells, found by their shape rather than a block of their own: an open mouth ringed by fences or walls (the
 * {@code mca:well_rims} tag), over a shaft that reaches water within a dozen blocks. Villagers draw water at the mouth,
 * so any well a player builds or a village structure brings works, however deep its water sits.
 */
public final class Wells {
    private static final TagKey<Block> RIMS = TagKey.create(Registries.BLOCK, MCA.locate("well_rims"));
    /** How far down the shaft the water may be. */
    private static final int DEPTH = 12;
    /** Of the eight blocks around the mouth, how many must be rim. */
    private static final int RIM_NEEDED = 3;

    private Wells() {
    }

    /** Whether this is the mouth of a well: open, ringed by rim, with water down the shaft. */
    public static boolean isMouth(ServerLevel level, BlockPos pos) {
        BlockState mouth = level.getBlockState(pos);
        if (!mouth.isAir() && !mouth.getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        // cheap first: most open air has no rim beside it
        int rims = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(side)).is(RIMS)) {
                rims++;
            }
        }
        if (rims == 0) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(side).relative(side.getClockWise())).is(RIMS)) {
                rims++;
            }
        }
        return rims >= RIM_NEEDED && hasWaterBelow(level, pos);
    }

    private static boolean hasWaterBelow(ServerLevel level, BlockPos mouth) {
        BlockPos.MutableBlockPos pos = mouth.mutable();
        for (int i = 0; i <= DEPTH; i++) {
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()) {
                return true;
            }
            if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
                return false; // the shaft is blocked
            }
            pos.move(Direction.DOWN);
        }
        return false;
    }
}
