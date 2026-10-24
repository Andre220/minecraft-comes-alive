package net.conczin.mca.expansion.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws shutters and casement windows swinging on their hinges, with an ease in and out:
 * a shutter's panel turns 180 degrees from the wall over the window, each casement leaf 90 degrees outwards.
 */
public class SwingRenderer implements BlockEntityRenderer<SwingBlockEntity, SwingRenderer.State> {
    private static final int[] NO_TINT = new int[0];

    public static class State extends BlockEntityRenderState {
        @Nullable BlockState block;
        float open;
    }

    public SwingRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SwingBlockEntity entity, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, crumbling);
        state.block = entity.getBlockState();
        boolean open = state.block.hasProperty(ShutterBlock.OPEN) && state.block.getValue(ShutterBlock.OPEN);
        state.open = entity.openAmount(open, Util.getMillis());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState block = state.block;
        if (block == null) {
            return;
        }
        if (block.getBlock() instanceof ShutterBlock) {
            // drawn from the open model (flat on the wall), folded over the window as it closes
            boolean right = block.getValue(ShutterBlock.HINGE) == DoorHingeSide.RIGHT;
            float angle = (1.0F - state.open) * 180.0F * (right ? 1.0F : -1.0F);
            Vec3 hinge = rotateForFacing(right ? 1.0 : 0.0, 2.0 / 16.0, block.getValue(ShutterBlock.FACING));
            draw(block.setValue(ShutterBlock.OPEN, true), hinge, angle, state, pose, collector);
        } else if (block.getBlock() instanceof CasementBlock) {
            Direction facing = block.getValue(CasementBlock.FACING);
            float angle = state.open * 90.0F;
            // hinged at the outer corners of the opening (or in the middle of the block), swinging out
            double depth = block.getValue(CasementBlock.CENTERED) ? 0.5 : 15.0 / 16.0;
            draw(block.setValue(CasementBlock.LEAF, CasementBlock.Leaf.LEFT), rotateForFacing(1.0 / 16.0, depth, facing), -angle, state, pose, collector);
            draw(block.setValue(CasementBlock.LEAF, CasementBlock.Leaf.RIGHT), rotateForFacing(15.0 / 16.0, depth, facing), angle, state, pose, collector);
        }
    }

    /** Draws a block state's model turned by {@code degrees} around a vertical line through {@code hinge}. */
    private static void draw(BlockState model, Vec3 hinge, float degrees, State state, PoseStack pose, SubmitNodeCollector collector) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(model).collectParts(RandomSource.create(42L), parts);
        pose.pushPose();
        pose.translate(hinge.x, 0.0, hinge.z);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.translate(-hinge.x, 0.0, -hinge.z);
        collector.submitBlockModel(pose, RenderTypes.cutoutMovingBlock(), parts, NO_TINT, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /**
     * A point given for a block facing south (x across, z from the north edge), turned to the block's facing the
     * same way the blockstate turns its models.
     */
    private static Vec3 rotateForFacing(double x, double z, Direction facing) {
        double dx = x - 0.5;
        double dz = z - 0.5;
        return switch (facing) {
            case WEST -> new Vec3(0.5 - dz, 0.0, 0.5 + dx);
            case NORTH -> new Vec3(0.5 - dx, 0.0, 0.5 - dz);
            case EAST -> new Vec3(0.5 + dz, 0.0, 0.5 - dx);
            default -> new Vec3(x, 0.0, z);
        };
    }
}
