package net.conczin.mca.expansion.minds;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Archery practice: an archer looses arrows at a target block in the daytime. */
@PrefixGameTestTemplate(false)
public final class ArcheryGameTests {
    private ArcheryGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void archerShootsAtTheTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(17336, 2, 400));
        prepareFlatArea(helper, center, 12, 6);
        boolean previous = Config.getInstance().villagerArcheryPractice;
        Config.getInstance().villagerArcheryPractice = true;
        BlockPos target = center.offset(8, 1, 0);
        level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(target, Blocks.TARGET.defaultBlockState());

        VillagerEntityMCA archer = VillagerFactory.newVillager(level)
                .withAge(0)
                // already at a shooting spot: walking there is the brain's job, removed below
                .withPosition(Vec3.atBottomCenterOf(java.util.Objects.requireNonNull(ArcheryPracticeTask.findSpot(level, target), "no shooting spot")))
                .withName("Archery Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        archer.setVillagerXp(1);
        archer.setProfession(ProfessionsMCA.ARCHER);
        archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        ArcheryPracticeTask practice = new ArcheryPracticeTask();
        archer.setNoAi(false);

        // driven by hand: changing the profession rebuilds the brain, which would drop a task added to it
        String[] diagnosis = {""};
        boolean[] started = {false};
        int[] ran = {0};
        String[] status = {""};
        helper.onEachTick(() -> {
            if (!archer.isAlive()) {
                return;
            }
            archer.getMCABrain().removeAllBehaviors();
            if (!started[0]) {
                BlockPos found = ArcheryPracticeTask.findTarget(level, archer);
                started[0] = practice.tryStart(level, archer, level.getGameTime());
                diagnosis[0] = "target " + found + " (placed " + target + "), spot " + (found == null ? null : ArcheryPracticeTask.findSpot(level, found))
                        + ", starts " + started[0];
            } else if (practice.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING) {
                practice.tickOrStop(level, archer, level.getGameTime());
                ran[0]++;
                status[0] = ", ran " + ran[0] + " ticks, status " + practice.getStatus()
                        + ", attack " + archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET).isPresent()
                        + ", using " + archer.isUsingItem();
            }
        });

        helper.succeedWhen(() -> {
            boolean arrows = !level.getEntitiesOfClass(AbstractArrow.class, new AABB(target).inflate(3)).isEmpty();
            helper.assertTrue(arrows, "the archer should shoot at the target; practising: " + ArcheryPracticeTask.isPractising(archer)
                    + " at " + archer.blockPosition() + ", archer: " + (archer.getProfession() == ProfessionsMCA.ARCHER)
                    + ", hand: " + archer.getMainHandItem() + ", day time: " + Math.floorMod(level.getOverworldClockTime(), 24000L)
                    + ", " + diagnosis[0] + status[0] + ", activity: " + archer.getBrain().getActiveNonCoreActivity() + ", running: " + archer.getBrain().getRunningBehaviors());
            level.getEntitiesOfClass(AbstractArrow.class, new AABB(target).inflate(6)).forEach(net.minecraft.world.entity.Entity::discard);
            archer.discard();
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            Config.getInstance().villagerArcheryPractice = previous;
        });
    }
}
