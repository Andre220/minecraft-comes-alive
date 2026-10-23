package net.conczin.mca.server.world.data.workorder;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintTransformTest {
    // a 3 wide (x), 2 high, 5 deep (z) blueprint
    private static final int SX = 3;
    private static final int SY = 2;
    private static final int SZ = 5;

    @Test
    void oddTurnsSwapWidthAndDepth() {
        assertArrayEquals(new int[]{3, 2, 5}, BlueprintTransform.rotatedSize(SX, SY, SZ, 0));
        assertArrayEquals(new int[]{5, 2, 3}, BlueprintTransform.rotatedSize(SX, SY, SZ, 1));
        assertArrayEquals(new int[]{3, 2, 5}, BlueprintTransform.rotatedSize(SX, SY, SZ, 2));
        assertArrayEquals(new int[]{5, 2, 3}, BlueprintTransform.rotatedSize(SX, SY, SZ, -1));
    }

    @Test
    void everyTurnFillsItsBoxExactly() {
        for (int rotation = 0; rotation < 4; rotation++) {
            int[] size = BlueprintTransform.rotatedSize(SX, SY, SZ, rotation);
            Set<BlockPos> placed = new HashSet<>();
            for (int x = 0; x < SX; x++) {
                for (int y = 0; y < SY; y++) {
                    for (int z = 0; z < SZ; z++) {
                        BlockPos pos = BlueprintTransform.rotate(x, y, z, SX, SZ, rotation);
                        assertTrue(pos.getX() >= 0 && pos.getX() < size[0] && pos.getZ() >= 0 && pos.getZ() < size[2],
                                "turn " + rotation + " moved a block out of its box: " + pos);
                        assertEquals(y, pos.getY(), "turning must keep the height");
                        placed.add(pos);
                    }
                }
            }
            assertEquals(SX * SY * SZ, placed.size(), "turn " + rotation + " put two blocks on the same spot");
        }
    }

    @Test
    void clockwiseMatchesVanilla() {
        // vanilla turns (x, z) into (-z, x); shifted back into the box
        assertEquals(new BlockPos(SZ - 1, 0, 0), BlueprintTransform.rotate(0, 0, 0, SX, SZ, 1));
        assertEquals(new BlockPos(0, 0, 2), BlueprintTransform.rotate(2, 0, 4, SX, SZ, 1));
    }

    @Test
    void fourTurnsAreNoTurn() {
        BlockPos pos = new BlockPos(1, 1, 3);
        BlockPos turned = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        int sx = SX;
        int sz = SZ;
        for (int i = 0; i < 4; i++) {
            turned = BlueprintTransform.rotate(turned.getX(), turned.getY(), turned.getZ(), sx, sz, 1);
            int swap = sx;
            sx = sz;
            sz = swap;
        }
        assertEquals(pos, turned);
    }

    @Test
    void placedAreaStartsAtTheOrigin() {
        WorkArea area = BlueprintTransform.placedArea(new BlockPos(10, 64, -5), SX, SY, SZ, 1);
        assertEquals(new WorkArea(10, 64, -5, 14, 65, -3), area);
    }
}
