package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public interface TagsMCA {
    interface Blocks {
        TagKey<Block> TOMBSTONES = register("tombstones");
        /** Blocks that can belong to someone (besides job site blocks), see PropertyRules. */
        TagKey<Block> OWNABLE = register("ownable");

        static void bootstrap() {
        }

        static TagKey<Block> register(String path) {
            return TagKey.create(Registries.BLOCK, MCA.locate(path));
        }
    }

    interface Items {
        TagKey<Item> VILLAGER_EGGS = register("villager_eggs");
        TagKey<Item> ZOMBIE_EGGS = register("zombie_eggs");
        // what villagers drink when thirsty, see VillagerDrinks
        TagKey<Item> VILLAGER_DRINKS = register("villager_drinks");
        TagKey<Item> VILLAGER_REFRESHING_DRINKS = register("villager_refreshing_drinks");
        TagKey<Item> VILLAGER_DIRTY_DRINKS = register("villager_dirty_drinks");

        static void bootstrap() {
        }

        static TagKey<Item> register(String path) {
            return TagKey.create(Registries.ITEM, MCA.locate(path));
        }
    }
}
