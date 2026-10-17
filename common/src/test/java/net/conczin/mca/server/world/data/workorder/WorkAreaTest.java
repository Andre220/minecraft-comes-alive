package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkAreaTest {
    @Test
    void cornersInAnyOrderGiveTheSameBox() {
        WorkArea a = WorkArea.of(new BlockPos(5, 70, -3), new BlockPos(1, 64, 2));
        WorkArea b = WorkArea.of(new BlockPos(1, 64, 2), new BlockPos(5, 70, -3));

        assertEquals(a, b);
        assertEquals(new WorkArea(1, 64, -3, 5, 70, 2), a);
        assertEquals(5 * 7 * 6, a.volume());
    }

    @Test
    void singleBlockAreaHasVolumeOne() {
        WorkArea area = WorkArea.of(new BlockPos(3, 3, 3), new BlockPos(3, 3, 3));

        assertEquals(1, area.volume());
        assertTrue(area.contains(new BlockPos(3, 3, 3)));
        assertFalse(area.contains(new BlockPos(3, 4, 3)));
    }

    @Test
    void invertedBoxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WorkArea(2, 0, 0, 1, 0, 0));
    }

    @Test
    void diggingStartsAtTheTopAndBuildingAtTheBottom() {
        WorkArea area = new WorkArea(0, 10, 0, 1, 12, 1);

        assertArrayEquals(new int[]{12, 11, 10}, area.layerOrder(true));
        assertArrayEquals(new int[]{10, 11, 12}, area.layerOrder(false));
    }

    @Test
    void layerCoversEveryColumnOnce() {
        WorkArea area = new WorkArea(-1, 5, 4, 1, 7, 5);
        List<BlockPos> layer = area.layer(6);

        assertEquals(3 * 2, layer.size());
        assertEquals(layer.size(), layer.stream().distinct().count());
        assertTrue(layer.stream().allMatch(pos -> pos.getY() == 6 && area.contains(pos)));
    }

    @Test
    void arrayRoundTrip() {
        WorkArea area = new WorkArea(-7, -60, 12, 3, 80, 40);

        assertEquals(area, WorkArea.fromArray(area.toArray()));
        assertThrows(IllegalArgumentException.class, () -> WorkArea.fromArray(new int[]{1, 2, 3}));
    }

    @Test
    void nearIncludesTheMargin() {
        WorkArea area = new WorkArea(0, 0, 0, 2, 2, 2);

        assertTrue(area.isNear(new BlockPos(3, 1, 1), 1));
        assertFalse(area.isNear(new BlockPos(4, 1, 1), 1));
        assertFalse(area.isNear(new BlockPos(3, 1, 1), 0));
    }
}
