package net.conczin.mca.expansion.wardrobe;

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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a wardrobe's door swinging open on its hinge and a dresser's drawers sliding out, eased in and out, and the
 * outfits inside, folded: on a wardrobe's shelves, in a dresser's drawers. All of it is worked out for a piece
 * facing south and turned to the real facing.
 */
public class ClosetRenderer implements BlockEntityRenderer<ClosetBlockEntity, ClosetRenderer.State> {
    private static final int[] NO_TINT = new int[0];
    private static final float PX = 1.0F / 16.0F;
    private static final float DOOR_SWING = 100.0F;
    /** How far a drawer slides out, in blocks. */
    private static final float DRAWER_TRAVEL = 8.0F * PX;
    private static final float SHELF_OUTFIT = 0.75F;
    private static final float DRAWER_OUTFIT = 0.33F;
    /** Where the wardrobe's shelves are, in px above the closet block: the floor, then the two shelves. */
    private static final float[] SHELVES = {2.0F, 11.5F, 21.0F};

    public static class State extends BlockEntityRenderState {
        @Nullable BlockState block;
        final float[] open = new float[4];
        final List<OutfitRenderer.@Nullable Look> outfits = new ArrayList<>();
    }

    public ClosetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ClosetBlockEntity closet, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(closet, state, partialTick, cameraPos, crumbling);
        state.block = closet.getBlockState();
        long now = Util.getMillis();
        for (int i = 0; i < state.open.length; i++) {
            state.open[i] = closet.openAmount(i, now);
        }
        state.outfits.clear();
        for (int slot = 0; slot < closet.size(); slot++) {
            state.outfits.add(closet.getItem(slot).isEmpty() ? null : OutfitRenderer.look(closet.getItem(slot)));
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockState block = state.block;
        if (block == null || !(block.getBlock() instanceof FurnitureBlock)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(block.getValue(FurnitureBlock.FACING))));
        pose.translate(-0.5F, 0.0F, -0.5F);
        BlockState south = block.setValue(FurnitureBlock.FACING, Direction.SOUTH);
        if (block.getBlock() instanceof WardrobeBlock) {
            submitWardrobe(south, state, pose, collector);
        } else {
            submitDresser(south, state, pose, collector);
        }
        pose.popPose();
    }

    private static void submitWardrobe(BlockState block, State state, PoseStack pose, SubmitNodeCollector collector) {
        boolean left = block.getValue(WardrobeBlock.LEFT);
        boolean right = block.getValue(WardrobeBlock.RIGHT);
        // the handle is on the right, but on the left for the last door of a row: the hinge is the other side
        boolean hingeRight = left && !right;
        float hinge = hingeRight ? (right ? 15.5F : 15.0F) * PX : (left ? 0.5F : 1.0F) * PX;
        float angle = state.open[0] * DOOR_SWING * (hingeRight ? 1.0F : -1.0F);
        pose.pushPose();
        pose.translate(hinge, 0.0F, 1.0F);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-hinge, 0.0F, -1.0F);
        draw(block.setValue(WardrobeBlock.PIECE, FurnitureBlock.Piece.DOOR), state, pose, collector);
        pose.popPose();

        for (int slot = 0; slot < state.outfits.size(); slot++) {
            OutfitRenderer.Look look = state.outfits.get(slot);
            if (look != null) {
                // folded on its shelf
                pose.pushPose();
                pose.translate(0.5F - SHELF_OUTFIT / 2, SHELVES[slot] * PX, 2.0F * PX);
                pose.scale(SHELF_OUTFIT, SHELF_OUTFIT, SHELF_OUTFIT);
                OutfitRenderer.submitLook(look, pose, collector, state.lightCoords);
                pose.popPose();
            }
        }
    }

    private static void submitDresser(BlockState block, State state, PoseStack pose, SubmitNodeCollector collector) {
        BlockState drawer = block.setValue(DresserBlock.PIECE, FurnitureBlock.Piece.DRAWER);
        for (int i = 0; i < DresserBlock.DRAWERS; i++) {
            // the drawer model sits in the bottom left place; the others are 7.5 px to the right, 7 px up
            float x = (i % 2) * 7.5F * PX;
            float y = (i / 2) * 7.0F * PX;
            float z = state.open[i] * DRAWER_TRAVEL;
            pose.pushPose();
            pose.translate(x, y, z);
            draw(drawer, state, pose, collector);
            OutfitRenderer.Look look = i < state.outfits.size() ? state.outfits.get(i) : null;
            if (look != null && state.open[i] > 0.0F) {
                // folded in the drawer
                pose.translate(4.25F * PX - DRAWER_OUTFIT / 2, 1.5F * PX, 7.0F * PX);
                pose.scale(DRAWER_OUTFIT, DRAWER_OUTFIT, DRAWER_OUTFIT);
                OutfitRenderer.submitLook(look, pose, collector, state.lightCoords);
            }
            pose.popPose();
        }
    }

    /** The blockstate's y rotation for a facing, as in the blockstate files. */
    private static float yRot(Direction facing) {
        return switch (facing) {
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static void draw(BlockState model, State state, PoseStack pose, SubmitNodeCollector collector) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(model).collectParts(RandomSource.create(42L), parts);
        collector.submitBlockModel(pose, RenderTypes.cutoutMovingBlock(), parts, NO_TINT, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }
}
