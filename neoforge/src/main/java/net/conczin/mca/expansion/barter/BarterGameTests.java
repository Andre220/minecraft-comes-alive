package net.conczin.mca.expansion.barter;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Haggling: offers come from what the villager carries, and a trade moves the goods. */
@PrefixGameTestTemplate(false)
public final class BarterGameTests {
    private BarterGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void villagerSellsWhatItCarries(GameTestHelper helper) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))))
                .withName("Barter Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setVillagerXp(1);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT, 40));
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 6));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.runAfterDelay(2, () -> {
            MerchantOffers offers = Barter.offers(player, villager);
            MerchantOffer wheat = offers.stream().filter(o -> o.getResult().is(Items.WHEAT)).findFirst().orElse(null);
            helper.assertTrue(wheat != null, "the villager should sell its wheat, offers: " + offers.size());
            MerchantOffer bread = offers.stream().filter(o -> o.getResult().is(Items.BREAD)).findFirst().orElse(null);
            helper.assertTrue(bread != null && bread.getMaxUses() * bread.getResult().getCount() <= 2,
                    "it keeps 4 of its 6 bread for itself");

            int before = villager.getInventory().countItem(Items.WHEAT);
            new BarterMerchant(villager, offers).notifyTrade(wheat);
            helper.assertTrue(villager.getInventory().countItem(Items.WHEAT) == before - wheat.getResult().getCount(),
                    "the sold wheat should leave its inventory");
            helper.assertTrue(villager.getInventory().countItem(Items.EMERALD) == wheat.getCostA().getCount(),
                    "the emeralds paid should go in");
            villager.discard();
            helper.succeed();
        });
    }
}
