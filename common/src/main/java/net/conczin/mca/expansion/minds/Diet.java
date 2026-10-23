package net.conczin.mca.expansion.minds;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.registries.BuiltInRegistries;
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
        if (distinct >= VARIED) {
            return List.of(new Needs.Thought(Needs.Need.HUNGER, "needs.thought.diet.varied", 2));
        }
        if (meals.size() >= VARIED && distinct == 1) {
            return List.of(new Needs.Thought(Needs.Need.HUNGER, "needs.thought.diet.monotonous", -2));
        }
        return List.of();
    }

    /** The food to eat from an inventory: the one eaten least lately, so meals vary on their own. */
    public static int pickFood(VillagerEntityMCA villager, Container inventory, Predicate<ItemStack> edible) {
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
