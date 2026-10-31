package net.conczin.mca.expansion.townhall;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.block.BlockEntityTypesMCA;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Optional;

/**
 * The town hall: a building with the village bell and the City Records, a stand with the enchanting table's book on
 * it. Using the records opens the village's management panel (TownHallScreen), a radial menu over the pages of the
 * blueprint: map, residents, works, rules and taxes, progress, catalog and rank.
 */
public final class TownHall {
    public static final Block CITY_RECORDS = BlocksMCA.register("city_records",
            new CityRecordsBlock(BlocksMCA.baseProps("city_records", Blocks.LECTERN).noOcclusion()));
    /** The block entity animating the book. Set once registered. */
    public static BlockEntityType<CityRecordsBlockEntity> RECORDS;

    static {
        ItemsMCA.register("city_records", new BlockItem(CITY_RECORDS, ItemsMCA.blockProps("city_records")));
    }

    private TownHall() {
    }

    /** Loads the class, so its block and item are in MCA's registration maps. */
    public static void bootstrap() {
    }

    /** Whether villages get their City Records by the bell when first seen, so a town hall is there from the start. */
    public static boolean recordsAtStart() {
        return Config.getServerConfig().villageTownHallAtStart;
    }

    /** The leader and, once per village, the City Records by the bell; from the village tick. */
    public static void villageTick(ServerLevel level, Village village, long time) {
        VillageLeaders.tick(level, village, time);
        if (time % VillageLeaders.CHECK_INTERVAL == 0 && recordsAtStart()) {
            TownHallData data = TownHallData.get(level);
            if (!data.recordsPlaced(village.getId()) && placeRecords(level, village)) {
                data.markRecordsPlaced(village.getId());
            }
        }
    }

    /**
     * Sets the City Records on free ground next to the village's bell. True once they stand there (or by the bell
     * already); false while the bell's chunk is not loaded or there is no room yet.
     */
    static boolean placeRecords(ServerLevel level, Village village) {
        Optional<BlockPos> bell = village.getBuildingsOfType("town_center").map(Building::getCenter)
                .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BELL))
                .findFirst();
        if (bell.isEmpty()) {
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(bell.get().offset(-3, -2, -3), bell.get().offset(3, 2, 3))) {
            if (level.getBlockState(pos).is(CITY_RECORDS)) {
                return true;
            }
        }
        for (int radius = 2; radius <= 3; radius++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = bell.get().relative(side, radius).above(dy);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        level.setBlockAndUpdate(pos, CITY_RECORDS.defaultBlockState());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerBlockEntityTypes(BlockEntityTypesMCA.TriFunction register) {
        RECORDS = register.apply(MCA.locate("city_records"), (BlockEntityTypesMCA.BlockEntitySupplier) CityRecordsBlockEntity::new,
                new Block[]{CITY_RECORDS});
    }
}
