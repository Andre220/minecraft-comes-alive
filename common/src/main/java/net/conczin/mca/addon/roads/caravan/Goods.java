package net.conczin.mca.addon.roads.caravan;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.ArrayList;
import java.util.List;

/**
 * What a caravan's chests hold and which animals carry them, by the biome it set off from: llamas in the mountains,
 * the cold and the dry lands, mules and donkeys elsewhere; the goods of the land plus a rare find now and then.
 */
final class Goods {
    private Goods() {
    }

    static String animalFor(Holder<Biome> biome, RandomSource random) {
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_BADLANDS)
            || biome.is(Biomes.DESERT) || biome.is(BiomeTags.SPAWNS_SNOW_FOXES)) {
            return "minecraft:llama";
        }
        return random.nextBoolean() ? "minecraft:mule" : "minecraft:donkey";
    }

    /** Four to eight stacks: the land's goods, with a small chance of something rare. */
    static List<ItemStack> load(Holder<Biome> biome, RandomSource random) {
        List<ItemStack> pool = new ArrayList<>();
        if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
            pool.addAll(List.of(new ItemStack(Items.SAND, 32), new ItemStack(Items.CACTUS, 8), new ItemStack(Items.TERRACOTTA, 16),
                    new ItemStack(Items.DEAD_BUSH, 4), new ItemStack(Items.RABBIT_HIDE, 6)));
        } else if (biome.is(BiomeTags.SPAWNS_SNOW_FOXES)) {
            pool.addAll(List.of(new ItemStack(Items.PACKED_ICE, 16), new ItemStack(Items.SNOWBALL, 16), new ItemStack(Items.RABBIT_HIDE, 8),
                    new ItemStack(Items.SPRUCE_LOG, 16), new ItemStack(Items.SWEET_BERRIES, 16)));
        } else if (biome.is(BiomeTags.IS_JUNGLE)) {
            pool.addAll(List.of(new ItemStack(Items.COCOA_BEANS, 16), new ItemStack(Items.BAMBOO, 32), new ItemStack(Items.MELON_SLICE, 16),
                    new ItemStack(Items.JUNGLE_LOG, 16)));
        } else if (biome.is(BiomeTags.IS_SAVANNA)) {
            pool.addAll(List.of(new ItemStack(Items.ACACIA_LOG, 16), new ItemStack(Items.LEATHER, 8), new ItemStack(Items.WHEAT, 16),
                    new ItemStack(Items.SADDLE, 1)));
        } else if (biome.is(BiomeTags.IS_TAIGA)) {
            pool.addAll(List.of(new ItemStack(Items.SPRUCE_LOG, 16), new ItemStack(Items.SWEET_BERRIES, 16), new ItemStack(Items.FERN, 8),
                    new ItemStack(Items.LEATHER, 6)));
        } else {
            pool.addAll(List.of(new ItemStack(Items.WHEAT, 24), new ItemStack(Items.BREAD, 12), new ItemStack(Items.WOOL.white(), 16),
                    new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.APPLE, 8)));
        }
        pool.addAll(List.of(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.TORCH, 16)));

        List<ItemStack> load = new ArrayList<>();
        int stacks = 4 + random.nextInt(5);
        for (int i = 0; i < stacks; i++) {
            load.add(pool.get(random.nextInt(pool.size())).copy());
        }
        if (random.nextFloat() < 0.25F) {
            ItemStack[] rare = {new ItemStack(Items.NAME_TAG), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.DIAMOND),
                    new ItemStack(Items.MUSIC_DISC_CAT), new ItemStack(Items.SPYGLASS)};
            load.add(rare[random.nextInt(rare.length)].copy());
        }
        return load;
    }
}
