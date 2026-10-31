package net.conczin.mca.expansion.townhall;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the City Records' book with the enchanting table's model and texture, floating over the stand. */
public class CityRecordsRenderer implements BlockEntityRenderer<CityRecordsBlockEntity, EnchantTableRenderState> {
    private final SpriteGetter sprites;
    private final BookModel book;

    public CityRecordsRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.book = new BookModel(context.bakeLayer(ModelLayers.BOOK));
    }

    @Override
    public EnchantTableRenderState createRenderState() {
        return new EnchantTableRenderState();
    }

    @Override
    public void extractRenderState(CityRecordsBlockEntity records, EnchantTableRenderState state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(records, state, partialTick, cameraPos, crumbling);
        state.flip = Mth.lerp(partialTick, records.oFlip, records.flip);
        state.open = Mth.lerp(partialTick, records.oOpen, records.open);
        state.time = records.time + partialTick;
        state.yRot = records.oRot + CityRecordsBlockEntity.wrap(records.rot - records.oRot) * partialTick;
    }

    @Override
    public void submit(EnchantTableRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0.85F + Mth.sin(state.time * 0.1F) * 0.01F, 0.5F);
        pose.mulPose(Axis.YP.rotation(-state.yRot));
        pose.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float left = Mth.frac(state.flip + 0.25F) * 1.6F - 0.3F;
        float right = Mth.frac(state.flip + 0.75F) * 1.6F - 0.3F;
        BookModel.State bookState = BookModel.State.forAnimation(state.time, Mth.clamp(left, 0.0F, 1.0F), Mth.clamp(right, 0.0F, 1.0F), state.open);
        collector.submitModel(book, bookState, pose, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                EnchantTableRenderer.BOOK_TEXTURE, sprites, 0, state.breakProgress);
        pose.popPose();
    }
}
