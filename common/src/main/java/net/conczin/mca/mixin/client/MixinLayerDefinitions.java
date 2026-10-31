package net.conczin.mca.mixin.client;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.player.PlayerModel;
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
 * Swaps the adult villager and zombie villager models for human-like meshes. Only the villager layers are replaced,
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
        // zombie villagers: player-shaped (MCA's textures use the player layout), with the zombie's armor shapes
        LayerDefinition zombie = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
        result.put(ModelLayers.ZOMBIE_VILLAGER, zombie);
        result.put(ModelLayers.ZOMBIE_VILLAGER_NO_HAT, zombie);
        mca$copyArmor(roots, result, ModelLayers.ZOMBIE_ARMOR, ModelLayers.ZOMBIE_VILLAGER_ARMOR);
        return result;
    }

    @Unique
    private static void mca$copyArmor(Map<ModelLayerLocation, LayerDefinition> roots, Map<ModelLayerLocation, LayerDefinition> result,
                                      ArmorModelSet<ModelLayerLocation> from, ArmorModelSet<ModelLayerLocation> to) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            LayerDefinition definition = roots.get(from.get(slot));
            if (definition != null) {
                result.put(to.get(slot), definition);
            }
        }
    }
}
