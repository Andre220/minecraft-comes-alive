package net.conczin.mca.client.render.voxel;

import java.util.List;
import java.util.Map;

/**
 * The outer skin layers. Names are the flat layer part in MCA's Fresh Animations model, the
 * human-like villager mesh and the player model; {@link VillagerParts} prefers EMF's own part.
 * The cubes are placed in that part's space.
 */
public enum VoxelLayer {
    HAT(new VoxelBox(-4, -8, -4, 8, 8, 8, 32, 0), "headwear", "hat"),
    JACKET(new VoxelBox(-4, 0, -2, 8, 12, 4, 16, 32), "mca_jacket", "jacket"),
    RIGHT_SLEEVE(new VoxelBox(-3, -2, -2, 4, 12, 4, 40, 32), "mca_right_sleeve", "right_sleeve"),
    LEFT_SLEEVE(new VoxelBox(-1, -2, -2, 4, 12, 4, 48, 48), "mca_left_sleeve", "left_sleeve"),
    RIGHT_PANTS(new VoxelBox(-2, 0, -2, 4, 12, 4, 0, 32), "mca_right_pants", "right_pants"),
    LEFT_PANTS(new VoxelBox(-2, 0, -2, 4, 12, 4, 0, 48), "mca_left_pants", "left_pants");

    /**
     * Human-like mesh parts EMF keeps next to their animated counterparts in MCA's Fresh Animations
     * model (vanilla name to model id); drawn only without EMF.
     */
    public static final Map<String, String> VANILLA_DUPLICATES = Map.of(
            "mca_right_arm", "mca_right_arm",
            "mca_left_arm", "mca_left_arm",
            "jacket", "mca_jacket",
            "right_pants", "mca_right_pants",
            "left_pants", "mca_left_pants"
    );

    private final VoxelBox box;
    private final List<String> names;

    VoxelLayer(VoxelBox box, String... names) {
        this.box = box;
        this.names = List.of(names);
    }

    public VoxelBox box() {
        return box;
    }

    public List<String> names() {
        return names;
    }
}
