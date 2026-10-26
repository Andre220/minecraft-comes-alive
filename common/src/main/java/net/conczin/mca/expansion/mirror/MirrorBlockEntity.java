package net.conczin.mca.expansion.mirror;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The block holding a mirror's glass: it keeps nothing, but its renderer draws the reflection. */
public class MirrorBlockEntity extends BlockEntity {
    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(Mirrors.MIRROR, pos, state);
    }
}
