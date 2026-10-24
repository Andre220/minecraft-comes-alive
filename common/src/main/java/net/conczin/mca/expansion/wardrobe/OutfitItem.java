package net.conczin.mca.expansion.wardrobe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * An outfit: the clothes MCA draws on a villager or player, as an item. It shows as the shirt's front and its two
 * sleeves cut out of the clothing texture, so it can stand in a wardrobe or lie on a table. One without clothes
 * shows the bare body texture.
 */
public class OutfitItem extends Item {
    private static final String CLOTHES = "mca_clothes";

    public OutfitItem(Properties properties) {
        super(properties);
    }

    /** The clothing texture id, or empty. */
    public static String clothes(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(CLOTHES, "");
    }

    public static void setClothes(ItemStack stack, String clothes) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (clothes.isEmpty()) {
                tag.remove(CLOTHES);
            } else {
                tag.putString(CLOTHES, clothes);
            }
        });
    }

    public static ItemStack of(Item item, String clothes) {
        ItemStack stack = new ItemStack(item);
        setClothes(stack, clothes);
        return stack;
    }
}
