package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.client.gui.PreviewEntityAnimation;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A GUI entity is submitted in {@code renderToTexture} but drawn just after, in {@code renderAllFeatures}. MCA's
 * preview values (MixinGuiEntityRenderer) covered only the submit, so the model was drawn, and EMF animated it, with
 * the entity's real tick and turn while the 3D outer layers had been placed with the preview's: the cubes came loose
 * from the body. The draw runs with the same preview values now.
 */
@Mixin(PictureInPictureRenderer.class)
abstract class MixinPictureInPictureRenderer {
    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void mca$drawWithPreviewEntityState(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage, Operation<Void> original,
                                                 PictureInPictureRenderState renderState) {
        PreviewEntityAnimation.State preview = renderState instanceof GuiEntityRenderState entity
                                               && entity.renderState() instanceof VillagerStateHolder holder
                ? holder.mca$getPreviewEntityAnimationState() : null;
        if (preview == null) {
            original.call(dispatcher, storage);
            return;
        }
        preview.run(() -> {
            original.call(dispatcher, storage);
            return null;
        });
    }
}
