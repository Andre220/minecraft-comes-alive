package net.conczin.mca.expansion.speech;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.InteractTask;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Speech bubbles: a speaking villager holds still for the player until it has said it all. */
@PrefixGameTestTemplate(false)
public final class SpeechGameTests {
    private SpeechGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void villagerStaysWhileSpeaking(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(at))
                .withName("Speech Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setVillagerXp(1);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(at.east(2)));

        helper.assertTrue(!InteractTask.shouldRun(villager), "nobody holds its attention yet");
        int ticks = SpeechPause.ticksToSay("Hello there, traveller! Nice weather today.");
        helper.assertTrue(ticks >= 60 && ticks <= 400, "speaking takes a sensible time: " + ticks);
        villager.getInteractions().speakTo(player, 40);
        helper.assertTrue(InteractTask.shouldRun(villager), "it stays with the player while it speaks");

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(!InteractTask.shouldRun(villager), "it goes on once it has said it all");
            villager.discard();
            helper.succeed();
        });
    }
}
