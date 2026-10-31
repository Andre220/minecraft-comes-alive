package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.HumanLikeVillagerMesh;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.voxel.VillagerParts;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Armor on human-like villagers. The vanilla villager renderer has no armor layer, so this one draws the worn pieces
 * with MCA's armor shapes (the ones the classic MCA renderer uses), each fitted onto the matching part of the animated
 * model (Fresh Animations included): head, body, arms and legs.
 */
public class HumanLikeVillagerArmorLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** An armor part, the villager model's parts it goes on, and the corner of the vanilla box it is shaped for. */
    private record Fit(String armorPart, List<String> modelParts, float minX, float minY, float minZ) {
    }

    private static final List<Fit> FITS = List.of(
            new Fit("head", List.of("head"), -4.0F, -8.0F, -4.0F),
            new Fit("body", List.of("body"), -4.0F, 0.0F, -2.0F),
            new Fit("right_arm", List.of(HumanLikeVillagerMesh.RIGHT_ARM, "right_arm"), -3.0F, -2.0F, -2.0F),
            new Fit("left_arm", List.of(HumanLikeVillagerMesh.LEFT_ARM, "left_arm"), -1.0F, -2.0F, -2.0F),
            new Fit("right_leg", List.of("right_leg"), -2.0F, 0.0F, -2.0F),
            new Fit("left_leg", List.of("left_leg"), -2.0F, 0.0F, -2.0F)
    );

    private final ArmorModel head;
    private final ArmorModel chest;
    private final ArmorModel legs;
    private final ArmorModel feet;
    private final EquipmentLayerRenderer equipmentRenderer;

    public HumanLikeVillagerArmorLayer(RenderLayerParent<VillagerRenderState, VillagerModel> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        // MCA's armor thicknesses, slimmer than the player's
        this.head = armorModel(0.55F, EquipmentSlot.HEAD);
        this.chest = armorModel(0.55F, EquipmentSlot.CHEST);
        this.legs = armorModel(0.3F, EquipmentSlot.LEGS);
        this.feet = armorModel(0.55F, EquipmentSlot.FEET);
        this.equipmentRenderer = context.getEquipmentRenderer();
    }

    private static ArmorModel armorModel(float dilation, EquipmentSlot slot) {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(dilation), 0.0F);
        Set<String> parts = switch (slot) {
            case HEAD -> Set.of("head");
            case CHEST -> Set.of("body", "left_arm", "right_arm");
            case LEGS -> Set.of("left_leg", "right_leg", "body");
            default -> Set.of("left_leg", "right_leg");
        };
        if (slot == EquipmentSlot.HEAD) {
            mesh.getRoot().retainPartsAndChildren(parts);
        } else {
            mesh.getRoot().retainExactParts(parts);
        }
        return new ArmorModel(LayerDefinition.create(mesh, 64, 32).bakeRoot());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, VillagerRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state instanceof VanillaVillagerLookHolder holder) || holder.mca$getLookVisuals() == null) {
            return;
        }
        ItemStack[] armor = holder.mca$getArmor();
        boolean any = false;
        for (ItemStack stack : armor) {
            any |= !stack.isEmpty();
        }
        if (!any) {
            return;
        }

        // where each part is now, in model space, with the armor box lined up on the part's own box
        VillagerParts.poseNow(getParentModel(), state);
        Map<String, VillagerParts.Placement> placements = VillagerParts.placements(getParentModel().root(), new PoseStack());
        Map<String, Matrix4f> poses = holder.mca$getArmorPoses();
        poses.clear();
        for (Fit fit : FITS) {
            VillagerParts.Placement placement = VillagerParts.placement(placements, fit.modelParts());
            if (placement != null) {
                poses.put(fit.armorPart(), new Matrix4f(placement.pose().pose()).translate(
                        (placement.minX() - fit.minX()) / 16.0F, (placement.minY() - fit.minY()) / 16.0F, (placement.minZ() - fit.minZ()) / 16.0F));
            }
        }

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = armor[i];
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (equippable == null || equippable.assetId().isEmpty() || !HumanoidArmorLayer.shouldRender(stack, SLOTS[i])) {
                continue;
            }
            ArmorModel model = switch (SLOTS[i]) {
                case HEAD -> head;
                case CHEST -> chest;
                case LEGS -> legs;
                default -> feet;
            };
            EquipmentClientInfo.LayerType layer = SLOTS[i] == EquipmentSlot.LEGS
                    ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
            equipmentRenderer.renderLayers(layer, equippable.assetId().get(), model, state, stack, poseStack, submitNodeCollector,
                    lightCoords, state.outlineColor);
        }
    }

    /** Armor pieces posed, when drawn, from the poses the layer worked out for this villager. */
    static class ArmorModel extends Model<VillagerRenderState> {
        private final Map<String, ModelPart> parts;

        ArmorModel(ModelPart root) {
            super(root, RenderTypes::armorCutoutNoCull);
            this.parts = Map.of(
                    "head", childOrEmpty(root, "head"),
                    "body", childOrEmpty(root, "body"),
                    "right_arm", childOrEmpty(root, "right_arm"),
                    "left_arm", childOrEmpty(root, "left_arm"),
                    "right_leg", childOrEmpty(root, "right_leg"),
                    "left_leg", childOrEmpty(root, "left_leg"));
        }

        @Override
        public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            if (!(state instanceof VanillaVillagerLookHolder holder)) {
                return;
            }
            Vector3f translation = new Vector3f();
            Quaternionf rotation = new Quaternionf();
            Vector3f angles = new Vector3f();
            Vector3f scale = new Vector3f();
            holder.mca$getArmorPoses().forEach((name, pose) -> {
                ModelPart part = parts.get(name);
                if (part == null) {
                    return;
                }
                pose.getTranslation(translation);
                pose.getScale(scale);
                pose.getNormalizedRotation(rotation);
                rotation.getEulerAnglesZYX(angles);
                part.x = translation.x * 16.0F;
                part.y = translation.y * 16.0F;
                part.z = translation.z * 16.0F;
                part.xRot = angles.x;
                part.yRot = angles.y;
                part.zRot = angles.z;
                part.xScale = scale.x;
                part.yScale = scale.y;
                part.zScale = scale.z;
            });
        }
    }

    /** A part of the armor model, or an empty one where this piece leaves it out. */
    private static ModelPart childOrEmpty(ModelPart root, String name) {
        return root.hasChild(name) ? root.getChild(name) : new ModelPart(List.of(), Map.of());
    }
}
