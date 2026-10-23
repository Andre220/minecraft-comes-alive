package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.client.model.HumanLikeVillagerMesh;
import net.conczin.mca.client.render.VanillaVillagerLook;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

/**
 * Swaps the adult villager model for the human-like mesh. Only the villager layers are replaced,
 * so the wandering trader (which shares the vanilla villager mesh) is unaffected. With Fresh
 * Animations, EMF replaces the same parts with MCA's model.
 */
@Mixin(LayerDefinitions.class)
public class MixinLayerDefinitions {
    @ModifyReturnValue(method = "createRoots()Ljava/util/Map;", at = @At("RETURN"))
    private static Map<ModelLayerLocation, LayerDefinition> mca$humanLikeVillagers(Map<ModelLayerLocation, LayerDefinition> roots) {
        if (!VanillaVillagerLook.isEnabled()) {
            return roots;
        }
        Map<ModelLayerLocation, LayerDefinition> result = new HashMap<>(roots);
        result.put(ModelLayers.VILLAGER, HumanLikeVillagerMesh.createBodyLayer());
        result.put(ModelLayers.VILLAGER_NO_HAT, HumanLikeVillagerMesh.createNoHatLayer());
        return result;
    }
}
