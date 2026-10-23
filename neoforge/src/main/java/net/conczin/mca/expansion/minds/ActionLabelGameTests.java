package net.conczin.mca.expansion.minds;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/** Action labels: what a villager is doing, as a translation key. */
@PrefixGameTestTemplate(false)
public final class ActionLabelGameTests {
    private ActionLabelGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void labelsSayWhatTheVillagerDoes(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(at))
                .withName("Label Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setVillagerXp(1);

        helper.runAfterDelay(5, () -> {
            String awake = ActionLabels.compute(villager);
            helper.assertTrue(!awake.isEmpty() && !awake.equals("action.mca.sleeping"), "an awake villager says what it does: " + awake);

            BlockPos bed = at.east(2);
            helper.getLevel().setBlockAndUpdate(bed, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST));
            helper.getLevel().setBlockAndUpdate(bed.west(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST));
            villager.startSleeping(bed);
            helper.assertTrue(ActionLabels.compute(villager).equals("action.mca.sleeping"), "a sleeping villager is sleeping: " + ActionLabels.compute(villager));
            villager.discard();
            helper.succeed();
        });
    }
}
