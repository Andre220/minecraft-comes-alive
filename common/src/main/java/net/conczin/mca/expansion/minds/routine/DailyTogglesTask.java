package net.conczin.mca.expansion.minds.routine;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Villagers passing by open the shutters in the morning, close them in the evening and light the
 * lamps at dusk (see {@link DailyToggles}), in the village's buildings only.
 */
public class DailyTogglesTask extends Behavior<VillagerEntityMCA> {
    private static final int INTERVAL = 40;
    private static final int REACH = 3;

    public DailyTogglesTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return (villager.tickCount + villager.getId()) % INTERVAL == 0
               && IdleRoutineTask.enabled() && !DailyToggles.isEmpty()
               && !villager.isSleeping() && !villager.isBaby();
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        Village village = Village.findNearest(villager).orElse(null);
        if (village == null) {
            return;
        }
        long dayTime = level.getOverworldClockTime();
        BlockPos origin = villager.blockPosition();
        boolean switched = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-REACH, -1, -REACH), origin.offset(REACH, 3, REACH))) {
            BlockState state = level.getBlockState(pos);
            DailyToggles.Toggle toggle = DailyToggles.forBlock(state);
            if (toggle == null) {
                continue;
            }
            BlockState wanted = toggle.wanted(state, dayTime);
            if (wanted != null && village.getBuildingAt(pos).isPresent()) {
                level.setBlockAndUpdate(pos, wanted);
                SoundType sound = wanted.getSoundType();
                level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume() * 0.5F, sound.getPitch());
                switched = true;
            }
        }
        if (switched) {
            villager.swing(InteractionHand.MAIN_HAND);
        }
    }
}
