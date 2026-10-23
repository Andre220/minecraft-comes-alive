package net.conczin.mca.expansion.minds.routine;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.minds.Diet;
import net.conczin.mca.expansion.tabletop.PlacedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * What is lying on a block with contents (items put down with Tabletop, a data file's choice): an instrument is
 * picked up, played and put back; food is eaten; anything else is picked up, looked at and put back.
 */
final class ContainerOpportunities {
    private ContainerOpportunities() {
    }

    static void offer(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, BlockPos spot, OpportunityTypes.Type type, float appeal,
                      Consumer<Opportunity> offer) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            OpportunityTypes.Type played = OpportunityTypes.forItem(stack);
            if (played != null && played.kind() == Opportunity.Kind.PLAY) {
                offer.accept(new Opportunity(Opportunity.Kind.PLAY, spot, pos, null,
                        Math.max(appeal, played.appealFor(null, villager.getProfession())), null, played.particle(), new Borrow(pos, slot, stack, true)));
            } else if (VillagerEntityMCA.isEdible(stack)) {
                offer.accept(new Opportunity(Opportunity.Kind.FEAST, spot, pos, null, appeal * 1.5F, null, null, new Eat(pos, slot, stack)));
            } else {
                offer.accept(new Opportunity(Opportunity.Kind.INSPECT, spot, pos, null, appeal, null, type.particle(), new Borrow(pos, slot, stack, false)));
            }
        }
    }

    /** Takes the item from its slot if it is still the one offered. */
    private static ItemStack take(ServerLevel level, BlockPos pos, int slot, ItemStack expected) {
        return containerAt(level, pos)
                .filter(container -> ItemStack.isSameItemSameComponents(container.getItem(slot), expected))
                .map(container -> container.removeItem(slot, 1))
                .orElse(ItemStack.EMPTY);
    }

    private static Optional<Container> containerAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    /** Picks an item up for a while and puts it back where it was (or keeps it safe if that spot is gone). */
    private static final class Borrow implements Opportunities.Action {
        private final BlockPos pos;
        private final int slot;
        private final ItemStack expected;
        private final boolean play;
        private @Nullable ItemStack held;

        Borrow(BlockPos pos, int slot, ItemStack expected, boolean play) {
            this.pos = pos;
            this.slot = slot;
            this.expected = expected.copy();
            this.play = play;
        }

        @Override
        public void start(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
            if (!villager.getMainHandItem().isEmpty() || !villager.getOffhandItem().isEmpty()) {
                return;
            }
            ItemStack taken = take(level, pos, slot, expected);
            if (taken.isEmpty()) {
                return;
            }
            if (play) {
                IdleRoutineTask.joinBand(level, villager, taken);
            }
            held = taken;
            villager.setItemInHand(InteractionHand.MAIN_HAND, taken);
            villager.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void tick(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity, int ticks) {
            // turning it over in the hands now and then
            if (!play && held != null && ticks % 40 == 0) {
                boolean inMain = villager.getMainHandItem() == held;
                villager.setItemInHand(inMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, ItemStack.EMPTY);
                villager.setItemInHand(inMain ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, held);
                villager.swing(inMain ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            }
        }

        @Override
        public void stop(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
            if (held == null) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (villager.getItemInHand(hand) == held) {
                    villager.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
            // as it was taken: a playing instrument starts over next time
            ItemStack back = expected.copyWithCount(1);
            held = null;
            Optional<Container> container = containerAt(level, pos);
            if (container.isPresent() && container.get().getItem(slot).isEmpty()) {
                container.get().setItem(slot, back);
            } else if (!PlacedItems.put(level, pos, slot, back) && !PlacedItems.putAnywhere(level, pos, back, villager.getRandom())) {
                ItemStack rest = villager.getInventory().addItem(back);
                if (!rest.isEmpty()) {
                    villager.spawnAtLocation(level, rest);
                }
            }
            villager.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** Takes food from the table and eats it there. */
    private static final class Eat implements Opportunities.Action {
        private final BlockPos pos;
        private final int slot;
        private final ItemStack expected;
        private @Nullable ItemStack food;

        Eat(BlockPos pos, int slot, ItemStack expected) {
            this.pos = pos;
            this.slot = slot;
            this.expected = expected.copy();
        }

        @Override
        public void start(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
            if (!villager.getMainHandItem().isEmpty()) {
                return;
            }
            ItemStack taken = take(level, pos, slot, expected);
            if (!taken.isEmpty()) {
                food = taken;
                villager.setItemInHand(InteractionHand.MAIN_HAND, taken);
            }
        }

        @Override
        public void tick(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity, int ticks) {
            if (food != null && ticks % 16 == 0 && ticks <= 160) {
                villager.swing(InteractionHand.MAIN_HAND);
                villager.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
            }
        }

        @Override
        public void stop(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
            if (food == null) {
                return;
            }
            if (villager.getMainHandItem() == food) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            FoodProperties properties = food.get(DataComponents.FOOD);
            int nutrition = properties == null ? 4 : properties.nutrition();
            villager.getNeeds().onAte(Math.round(nutrition * Diet.onAte(villager, food)));
            food = null;
        }
    }
}
