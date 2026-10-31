package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.render.voxel.VillagerParts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.Config;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.render.voxel.VoxelLayerRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Outer layers of players drawn with MCA's villager renderer, as small cubes when close. Built from
 * the same composite texture as villagers; the flat clothing and hair layers are hidden for these
 * players by {@code PlayerEntityExtendedModel}.
 */
public class Player3dLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public Player3dLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    /** Whether this player's outer layers are drawn as cubes. */
    public static boolean isActive(LivingEntityRenderState state) {
        return Config.getInstance().player3dLayers
               && state instanceof VillagerStateHolder holder
               && holder.mca$isVillagerRendererActive()
               && holder.mca$getPreviewEntityAnimationState() == null
               && VoxelLayerRenderer.inRange(state.distanceToCameraSq);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !isActive(state)) {
            return;
        }
        VillagerVisuals visuals = ((VillagerStateHolder) state).mca$getVisuals();
        if (visuals == null) {
            return;
        }
        VillagerParts.poseNow(getParentModel(), state);
        VoxelLayerRenderer.submit(getParentModel().root(), poseStack, submitNodeCollector, lightCoords,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F), DynamicSkinCache.getOrCreateStitchedSkin(visuals));
    }
}
