package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlanningSelectionTest {
    @Test
    void nothingIsSelectedBeforeTheFirstClick() {
        PlanningSelection selection = new PlanningSelection();

        assertFalse(selection.isActive());
        assertTrue(selection.area().isEmpty());
    }

    @Test
    void draggingStaysOnTheClickedLayer() {
        PlanningSelection selection = new PlanningSelection();
        selection.start(new BlockPos(10, 64, 10));
        selection.update(new BlockPos(7, 70, 13));

        assertEquals(Optional.of(new WorkArea(7, 64, 10, 10, 64, 13)), selection.area());
    }

    @Test
    void diggingGoesDownAndBuildingGoesUp() {
        PlanningSelection dig = new PlanningSelection();
        dig.setType(WorkOrderType.DIG);
        dig.start(new BlockPos(0, 64, 0));
        dig.adjustHeight(2);

        PlanningSelection build = new PlanningSelection();
        build.setType(WorkOrderType.BUILD);
        build.start(new BlockPos(0, 64, 0));
        build.adjustHeight(2);

        assertEquals(new WorkArea(0, 62, 0, 0, 64, 0), dig.area().orElseThrow());
        assertEquals(new WorkArea(0, 64, 0, 0, 66, 0), build.area().orElseThrow());
    }

    @Test
    void heightIsClamped() {
        PlanningSelection selection = new PlanningSelection();
        selection.start(BlockPos.ZERO);
        selection.adjustHeight(-5);
        assertEquals(1, selection.getHeight());

        selection.adjustHeight(1000);
        assertEquals(PlanningSelection.MAX_HEIGHT, selection.getHeight());
    }

    @Test
    void switchingToolsDropsTheSelection() {
        PlanningSelection selection = new PlanningSelection();
        selection.start(BlockPos.ZERO);
        selection.setType(WorkOrderType.BUILD);

        assertFalse(selection.isActive());

        selection.start(BlockPos.ZERO);
        selection.setType(WorkOrderType.BUILD);
        assertTrue(selection.isActive(), "choosing the current tool again keeps the selection");
    }

    @Test
    void cursorCornersMapToTheScreenEdges() {
        assertArrayEquals(new float[]{-1.0F, 1.0F}, PlanningMath.toNdc(0, 0, 800, 600));
        assertArrayEquals(new float[]{1.0F, -1.0F}, PlanningMath.toNdc(800, 600, 800, 600));
        assertArrayEquals(new float[]{0.0F, 0.0F}, PlanningMath.toNdc(400, 300, 800, 600));
    }

    @Test
    void rayHitsTheGroundPlaneInFrontOnly() {
        Vec3 origin = new Vec3(0, 10, 0);
        Vec3 down = new Vec3(1, -1, 0);

        Vec3 hit = PlanningMath.intersectHorizontal(origin, down, 4, 100).orElseThrow();
        assertEquals(6.0, hit.x, 1.0E-9);
        assertEquals(4.0, hit.y, 1.0E-9);

        assertTrue(PlanningMath.intersectHorizontal(origin, new Vec3(1, 1, 0), 4, 100).isEmpty(), "plane is behind the ray");
        assertTrue(PlanningMath.intersectHorizontal(origin, new Vec3(1, 0, 0), 4, 100).isEmpty(), "ray is parallel");
        assertTrue(PlanningMath.intersectHorizontal(origin, down, 4, 5).isEmpty(), "plane is out of range");
    }

    @Test
    void cameraDirectionsFollowMinecraftYaw() {
        // yaw 0 looks south (+z); the left hand points east (+x)
        Vec3 forward = PlanningCamera.forward(0);
        Vec3 left = PlanningCamera.left(0);
        assertEquals(0.0, forward.x, 1.0E-6);
        assertEquals(1.0, forward.z, 1.0E-6);
        assertEquals(1.0, left.x, 1.0E-6);
        assertEquals(0.0, left.z, 1.0E-6);

        // yaw 90 looks west (-x)
        assertEquals(-1.0, PlanningCamera.forward(90).x, 1.0E-6);
    }
}
