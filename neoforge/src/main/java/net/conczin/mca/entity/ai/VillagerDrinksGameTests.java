package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.TagsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * Thirsty villagers drink what they carry. The drink tags list Tough As Nails items as optional
 * entries, which must not break the tags when the mod is missing (as it is here).
 */
@PrefixGameTestTemplate(false)
public final class VillagerDrinksGameTests {
    private VillagerDrinksGameTests() {
    }

    @GameTest(batch = "mca_villager_drinks", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void thirstyVillagerDrinksWaterAndKeepsTheBottle(GameTestHelper helper) {
        boolean previous = Config.getServerConfig().villagerNeeds;
        Config.getServerConfig().villagerNeeds = true;
        try {
            // optional Tough As Nails entries are dropped, the vanilla ones stay
            helper.assertTrue(new ItemStack(Items.HONEY_BOTTLE).is(TagsMCA.Items.VILLAGER_REFRESHING_DRINKS),
                    "honey must be a refreshing drink even without Tough As Nails");
            helper.assertTrue(VillagerDrinks.drinkOf(PotionContents.createItemStack(Items.POTION, Potions.WATER)) != null,
                    "a water bottle is clean water");
            helper.assertTrue(VillagerDrinks.drinkOf(PotionContents.createItemStack(Items.POTION, Potions.STRONG_POISON)) == null,
                    "villagers must never drink brewed potions");
        } catch (RuntimeException | AssertionError e) {
            Config.getServerConfig().villagerNeeds = previous;
            throw e;
        }

        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Thirsty Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.getInventory().addItem(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        villager.getNeeds().set(Needs.Need.THIRST, 10.0F);

        helper.runAfterDelay(60, () -> {
            try {
                float thirst = villager.getNeeds().get(Needs.Need.THIRST);
                helper.assertTrue(thirst > 40.0F, "the villager should have drunk, thirst is still " + thirst);
                helper.assertTrue(!villager.getInventory().hasAnyMatching(stack -> stack.is(Items.POTION)), "the water bottle should be drunk");
                helper.assertTrue(villager.getInventory().hasAnyMatching(stack -> stack.is(Items.GLASS_BOTTLE)), "the empty bottle should be kept");
                villager.discard();
                helper.succeed();
            } finally {
                Config.getServerConfig().villagerNeeds = previous;
            }
        });
    }
}
