package net.conczin.mca.entity.ai;

import net.conczin.mca.registry.TagsMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.UseRemainder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/**
 * What villagers drink from their inventory when thirsty, and how much it helps.
 * <p>
 * With Tough As Nails installed its own item tags decide: {@code toughasnails:thirst/<n>_thirst_drinks}
 * gives how much a drink quenches (on the player's 0-20 scale) and
 * {@code toughasnails:poison_chance/<n>_poison_chance_drinks} how likely it makes sick, so drinks
 * other mods add to those tags work too. Without it those tags are simply empty and MCA's own
 * drink tags apply, which list Tough As Nails items as optional entries.
 */
public final class VillagerDrinks {
    /** A drink: thirst it quenches (0-100 scale), mood it gives, and the chance it makes sick. */
    public record Drink(float thirst, int mood, float poisonChance) {
    }

    private static final Drink CLEAN = new Drink(50.0F, 0, 0.0F);
    private static final Drink REFRESHING = new Drink(70.0F, 3, 0.0F);
    private static final Drink DIRTY = new Drink(20.0F, 0, 0.75F);
    /** Tough As Nails measures thirst like hunger, 0 to 20; villagers 0 to 100. */
    private static final float TAN_THIRST_SCALE = 5.0F;
    /** Mood lost when a drink makes sick. */
    public static final int SICK_MOOD = -5;

    private static final List<TagKey<Item>> TAN_THIRST = IntStream.rangeClosed(1, 20)
            .mapToObj(n -> tanTag("thirst/" + n + "_thirst_drinks"))
            .toList();
    private static final int[] TAN_POISON_CHANCES = {100, 75, 50, 25};
    private static final List<TagKey<Item>> TAN_POISON = IntStream.of(TAN_POISON_CHANCES)
            .mapToObj(n -> tanTag("poison_chance/" + n + "_poison_chance_drinks"))
            .toList();

    private VillagerDrinks() {
    }

    private static TagKey<Item> tanTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("toughasnails", path));
    }

    /** The drink this stack is, or null when villagers won't drink it. */
    public static @Nullable Drink drinkOf(ItemStack stack) {
        if (stack.isEmpty() || stack.isDamageableItem()) {
            // canteens hold several sips; drinking one would use up the whole canteen
            return null;
        }
        if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null || !contents.is(Potions.WATER)) {
                // brewed potions are never drunk out of thirst
                return null;
            }
        }

        int mood = stack.is(TagsMCA.Items.VILLAGER_REFRESHING_DRINKS) ? REFRESHING.mood() : 0;
        for (int n = 0; n < TAN_THIRST.size(); n++) {
            if (stack.is(TAN_THIRST.get(n))) {
                return new Drink((n + 1) * TAN_THIRST_SCALE, mood, tanPoisonChance(stack));
            }
        }

        if (stack.is(TagsMCA.Items.VILLAGER_REFRESHING_DRINKS)) {
            return REFRESHING;
        }
        if (stack.is(Items.POTION) || stack.is(TagsMCA.Items.VILLAGER_DRINKS)) {
            return CLEAN;
        }
        if (stack.is(TagsMCA.Items.VILLAGER_DIRTY_DRINKS)) {
            return DIRTY;
        }
        return null;
    }

    private static float tanPoisonChance(ItemStack stack) {
        for (int i = 0; i < TAN_POISON.size(); i++) {
            if (stack.is(TAN_POISON.get(i))) {
                return TAN_POISON_CHANCES[i] / 100.0F;
            }
        }
        return 0.0F;
    }

    public static boolean isDrink(ItemStack stack) {
        return drinkOf(stack) != null;
    }

    /** Safe drinks first; something that may make sick only when nothing else is left. */
    public static boolean isPreferred(ItemStack stack) {
        Drink drink = drinkOf(stack);
        return drink != null && drink.poisonChance() < 0.5F;
    }

    /** What is left in hand after drinking: the item's own remainder, else an empty bottle for potions. */
    public static ItemStack remainderOf(ItemStack stack) {
        UseRemainder remainder = stack.get(DataComponents.USE_REMAINDER);
        if (remainder != null) {
            return remainder.convertInto().create();
        }
        return stack.is(Items.POTION) ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
    }
}
