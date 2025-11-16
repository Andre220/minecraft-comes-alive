package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders facial expressions (eyebrows) based on villager's emotional state
 * Textures should be placed at: assets/mca/skins/expression/{expression}.png
 * 
 * Supported expressions:
 * - neutral.png (default, relaxed eyebrows)
 * - angry.png (furrowed, angled down inward)
 * - scared.png (raised high, slightly curved)
 * - sad.png (inner corners raised, drooping)
 * - happy.png (slightly raised, gentle curve)
 * - focused.png (slightly lowered, concentrated)
 */
public class ExpressionLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {

    public ExpressionLayer(RenderLayerParent<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, 
                      float limbAngle, float limbDistance, float tickDelta, float animationProgress, 
                      float headYaw, float headPitch) {
        // Only render head (where eyebrows are)
        model.setAllVisible(false);
        model.head.visible = true;

        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, 
                    animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        VillagerLike<?> mcaVillager = CommonVillagerModel.getVillager(villager);
        String expression = detectExpression(villager, mcaVillager);
        
        return cached("skins/expression/" + expression + ".png", MCA::locate);
    }

    /**
     * Detects the current emotional state of the villager
     * Returns the appropriate expression texture name
     */
    private String detectExpression(T entity, VillagerLike<?> villager) {
        // Raiva - quando foi atingido recentemente ou tem alvo de ataque
        if (entity.getLastHurtByMob() != null || entity.getLastHurtMob() != null) {
            return "angry";
        }
        
        // Medo - quando está em pânico ou com pouca vida
        if (villager.getVillagerBrain().isPanicking() || 
            entity.getHealth() < entity.getMaxHealth() * 0.3f) {
            return "scared";
        }
        
        // Triste - quando está com humor baixo (mood negativo)
        if (villager.getVillagerBrain().getMoodValue() < 30) {
            return "sad";
        }
        
        // Feliz - quando está com humor alto
        if (villager.getVillagerBrain().getMoodValue() > 70) {
            return "happy";
        }
        
        // Focado - quando está fazendo uma tarefa (chore)
        if (villager.getVillagerBrain().getCurrentJob().ordinal() > 0) {
            return "focused";
        }
        
        // Neutro - estado padrão relaxado
        return "neutral";
    }
}
