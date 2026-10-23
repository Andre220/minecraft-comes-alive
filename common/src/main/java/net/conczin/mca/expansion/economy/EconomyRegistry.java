package net.conczin.mca.expansion.economy;

import net.conczin.mca.MCA;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Blocks, work station POIs and professions of the village economy. They are always registered
 * (registries can't follow a config option); with the feature off nobody takes the new jobs.
 */
public final class EconomyRegistry {
    /** Every block of the expansion that is a point of interest. */
    private static final Map<ResourceKey<PoiType>, Block> POI_BLOCKS = new LinkedHashMap<>();
    /** The job sites among them. */
    private static final Map<ResourceKey<PoiType>, Block> STATIONS = new LinkedHashMap<>();

    public static final Block CHOPPING_BLOCK = station("chopping_block", Blocks.OAK_PLANKS);
    public static final Block HUNTING_RACK = station("hunting_rack", Blocks.SPRUCE_PLANKS);
    public static final Block FORAGE_BASKET = station("forage_basket", Blocks.BAMBOO_PLANKS);
    /** Marks a dig site miners work at when the village has no dig orders; not a job site. */
    public static final Block SIFTING_SIEVE = poiBlock("sifting_sieve", Blocks.OAK_PLANKS);

    public static final ResourceKey<PoiType> CHOPPING_BLOCK_POI = poiKey("chopping_block");
    public static final ResourceKey<PoiType> HUNTING_RACK_POI = poiKey("hunting_rack");
    public static final ResourceKey<PoiType> FORAGE_BASKET_POI = poiKey("forage_basket");
    public static final ResourceKey<PoiType> SIFTING_SIEVE_POI = poiKey("sifting_sieve");

    public static final VillagerProfession LUMBERJACK = gatherer("lumberjack", CHOPPING_BLOCK_POI);
    public static final VillagerProfession HUNTER = gatherer("hunter", HUNTING_RACK_POI);
    public static final VillagerProfession FORAGER = gatherer("forager", FORAGE_BASKET_POI);
    /** Carries goods between workshop chests and the village storage; has no job site. */
    public static final VillagerProfession HAULER = ProfessionsMCA.register("hauler", false, true, false,
            PoiType.NONE, PoiType.NONE, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);

    private EconomyRegistry() {
    }

    /** Loads this class, so its entries join MCA's registration maps. */
    public static void bootstrap() {
    }

    private static Block block(String name, Block copy) {
        Block block = BlocksMCA.register(name, new Block(BlocksMCA.baseProps(name, copy)));
        ItemsMCA.register(name, new BlockItem(block, ItemsMCA.blockProps(name)));
        return block;
    }

    private static Block poiBlock(String name, Block copy) {
        Block block = block(name, copy);
        POI_BLOCKS.put(poiKey(name), block);
        return block;
    }

    private static Block station(String name, Block copy) {
        Block block = poiBlock(name, copy);
        STATIONS.put(poiKey(name), block);
        return block;
    }

    private static ResourceKey<PoiType> poiKey(String name) {
        return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MCA.locate(name));
    }

    /**
     * Gatherers count as important professions, like builders: they never trade, so they never
     * gain experience, and MCA would otherwise draft them as guards first.
     */
    private static VillagerProfession gatherer(String name, ResourceKey<PoiType> station) {
        return ProfessionsMCA.register(name, false, true, false,
                holder -> holder.is(station), holder -> holder.is(station), SoundEvents.VILLAGER_WORK_FARMER);
    }

    public static boolean isGatherer(VillagerProfession profession) {
        return profession == LUMBERJACK || profession == HUNTER || profession == FORAGER;
    }

    /**
     * Whether a job site may be taken right now: the economy's stations only while the feature is
     * on, so with it off they are plain decoration.
     */
    public static boolean isAcquirable(Holder<PoiType> holder) {
        return EconomyFeature.enabled() || STATIONS.keySet().stream().noneMatch(holder::is);
    }

    public static void registerPoiTypes(MCA.RegisterHelper<PoiType> helper) {
        POI_BLOCKS.forEach((key, block) -> helper.register(key.identifier(), new PoiType(states(block), 1, 1)));
    }

    /**
     * Fabric does not map a registered POI's block states (NeoForge does), so the job site
     * sensor would never see the stations. Call once after registration.
     */
    public static void bindPoiStates() {
        POI_BLOCKS.forEach((key, block) -> {
            Holder<PoiType> holder = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(key);
            Set<BlockState> unbound = states(block).stream()
                    .filter(state -> PoiTypes.forState(state).isEmpty())
                    .collect(Collectors.toSet());
            if (!unbound.isEmpty()) {
                PoiTypes.registerBlockStates(holder, unbound);
            }
        });
    }

    private static Set<BlockState> states(Block block) {
        return Set.copyOf(block.getStateDefinition().getPossibleStates());
    }
}
