package net.conczin.mca.expansion.minds;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Food variety: villagers remember their last meals. The same food again and again fills them
 * less and gets boring; a varied diet cheers them up.
 */
public final class Diet {
    /** How many meals a villager remembers. */
    public static final int MEMORY = 8;
    /** Distinct foods among the remembered meals for a varied diet. */
    private static final int VARIED = 4;

    private static final TagKey<Item> GLUTEN = TagKey.create(Registries.ITEM, MCA.locate("gluten"));
    private static final TagKey<Item> SWEETS = TagKey.create(Registries.ITEM, MCA.locate("confectionaries"));

    /** A food a trait keeps away from, and the mood when it has to be eaten anyway. */
    private record Restriction(Traits.Trait trait, Predicate<ItemStack> food, int mood) {
    }

    private static final List<Restriction> RESTRICTIONS = List.of(
            new Restriction(Traits.VEGETARIAN, food -> food.is(ItemTags.MEAT), -3),
            new Restriction(Traits.COELIAC_DISEASE, food -> food.is(GLUTEN), -3),
            new Restriction(Traits.DIABETES, food -> food.is(SWEETS), -2)
    );

    /** The last meals, item ids separated by commas, oldest first. */
    private static final CDataParameter<String> RECENT = CParameter.create("RecentFoods", "");

    private Diet() {
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(RECENT);
    }

    public static List<String> recent(VillagerEntityMCA villager) {
        String value = villager.getTrackedValue(RECENT);
        return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(value.split(",")));
    }

    private static String id(ItemStack food) {
        return BuiltInRegistries.ITEM.getKey(food.getItem()).toString();
    }

    /** How many of the remembered meals were this food. */
    public static int timesEaten(VillagerEntityMCA villager, ItemStack food) {
        String id = id(food);
        return (int) recent(villager).stream().filter(id::equals).count();
    }

    /**
     * Remembers a meal and tells how well it fills: fully the first time, two thirds the second,
     * half the third and so on.
     */
    public static float onAte(VillagerEntityMCA villager, ItemStack food) {
        for (Restriction restriction : RESTRICTIONS) {
            if (restriction.food().test(food) && villager.getTraits().hasTrait(restriction.trait())) {
                villager.getVillagerBrain().modifyMoodValue(restriction.mood());
            }
        }
        if (!Skills.enabled() || food.isEmpty()) {
            return 1.0F;
        }
        float factor = 1.0F / (1.0F + 0.5F * timesEaten(villager, food));
        List<String> meals = recent(villager);
        meals.add(id(food));
        while (meals.size() > MEMORY) {
            meals.removeFirst();
        }
        villager.setTrackedValue(RECENT, String.join(",", meals));
        return factor;
    }

    /** The mood of the diet: varied food cheers up, always the same is boring. */
    public static List<Needs.Thought> thoughts(VillagerEntityMCA villager) {
        if (!Skills.enabled()) {
            return List.of();
        }
        List<String> meals = recent(villager);
        long distinct = meals.stream().distinct().count();
        // foodies care twice as much about what is on the plate
        int weight = villager.getTraits().hasTrait(PersonalityTraits.FOODIE) ? 2 : 1;
        if (distinct >= VARIED) {
            return List.of(new Needs.Thought(Needs.Need.HUNGER, "needs.thought.diet.varied", 2 * weight));
        }
        if (meals.size() >= VARIED && distinct == 1) {
            return List.of(new Needs.Thought(Needs.Need.HUNGER, "needs.thought.diet.monotonous", -2 * weight));
        }
        return List.of();
    }

    /** Whether the traits keep the villager away from this food: meat for vegetarians, gluten, sweets. */
    public static boolean isAvoided(VillagerEntityMCA villager, ItemStack food) {
        for (Restriction restriction : RESTRICTIONS) {
            if (restriction.food().test(food) && villager.getTraits().hasTrait(restriction.trait())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The food to eat from an inventory: the one eaten least lately, so meals vary on their own.
     * What the traits forbid only when nothing else is left.
     */
    public static int pickFood(VillagerEntityMCA villager, Container inventory, Predicate<ItemStack> edible) {
        int slot = leastEaten(villager, inventory, edible.and(food -> !isAvoided(villager, food)));
        return slot >= 0 ? slot : leastEaten(villager, inventory, edible);
    }

    private static int leastEaten(VillagerEntityMCA villager, Container inventory, Predicate<ItemStack> edible) {
        int best = -1;
        int bestTimes = Integer.MAX_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!edible.test(stack)) {
                continue;
            }
            int times = Skills.enabled() ? timesEaten(villager, stack) : 0;
            if (times < bestTimes) {
                best = slot;
                bestTimes = times;
                if (times == 0) {
                    break;
                }
            }
        }
        return best;
    }
}
