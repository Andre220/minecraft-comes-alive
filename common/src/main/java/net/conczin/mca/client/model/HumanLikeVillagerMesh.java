package net.conczin.mca.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;

/**
 * Player-shaped mesh for the vanilla villager model ("human-like squidward"), in the MCA villager
 * texture layout: the player skin layout in 64 unit space, so 64x64 and higher resolution textures
 * share the same UVs.
 * <p>
 * Part names match both {@code VillagerModel} and MCA's Fresh Animations model
 * ({@code resourcepacks/mca_fresh_animations}): the arms live inside {@code body} and the head keeps
 * an empty {@code nose}. With EMF the model replaces these parts without leftovers; without it
 * (no Fresh Animations, or screens where EMF does not apply) this mesh is drawn as is.
 */
public final class HumanLikeVillagerMesh {
    public static final String RIGHT_ARM = "mca_right_arm";
    public static final String LEFT_ARM = "mca_left_arm";
    public static final String RIGHT_SLEEVE = "mca_right_sleeve";
    public static final String LEFT_SLEEVE = "mca_left_sleeve";

    private static final int TEXTURE_SIZE = 64;
    // Same scale vanilla applies to villager-like models, so size and hitbox stay unchanged.
    private static final float VILLAGER_SCALE = 0.9375F;
    private static final float WEAR = 0.25F;

    private HumanLikeVillagerMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(createMesh(), TEXTURE_SIZE, TEXTURE_SIZE).apply(MeshTransformer.scaling(VILLAGER_SCALE));
    }

    public static LayerDefinition createNoHatLayer() {
        MeshDefinition mesh = createMesh();
        mesh.getRoot().clearChild("head").clearRecursively();
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE).apply(MeshTransformer.scaling(VILLAGER_SCALE));
    }

    private static MeshDefinition createMesh() {
        // Head + hat, body + jacket and legs + pants come from the player mesh.
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("nose", CubeListBuilder.create(), PartPose.ZERO);

        // Arms move into the body, under the ids the Fresh Animations model uses.
        root.clearChild("right_arm");
        root.clearChild("left_arm");
        // Sleeves are child parts so the 3D layers can replace them.
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild(RIGHT_ARM, CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F))
                .addOrReplaceChild(RIGHT_SLEEVE, CubeListBuilder.create()
                        .texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(WEAR)), PartPose.ZERO);
        body.addOrReplaceChild(LEFT_ARM, CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F))
                .addOrReplaceChild(LEFT_SLEEVE, CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(WEAR)), PartPose.ZERO);

        // VillagerModel requires the folded "arms" part; it stays empty.
        root.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F));
        return mesh;
    }
}
