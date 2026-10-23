package net.conczin.mca.client.render.voxel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Finds parts of the villager model by name, wherever they sit. EMF keeps every vanilla part and
 * nests its own parts (ids prefixed with {@code EMF_}) inside them, so the animated part is the
 * prefixed one when it exists.
 */
public final class VillagerParts {
    public static final String EMF_PREFIX = "EMF_";

    private static final Map<ModelPart, Function<String, @Nullable ModelPart>> LOOKUPS = new WeakHashMap<>();

    private VillagerParts() {
    }

    /** The part with exactly this name, or null. */
    public static @Nullable ModelPart exact(ModelPart root, String name) {
        return LOOKUPS.computeIfAbsent(root, ModelPart::createPartLookup).apply(name);
    }

    /** The first of the names that exists, preferring EMF's own parts over vanilla ones. */
    public static @Nullable ModelPart find(ModelPart root, List<String> names) {
        Function<String, @Nullable ModelPart> lookup = LOOKUPS.computeIfAbsent(root, ModelPart::createPartLookup);
        for (String name : names) {
            ModelPart part = lookup.apply(EMF_PREFIX + name);
            if (part != null) {
                return part;
            }
        }
        for (String name : names) {
            ModelPart part = lookup.apply(name);
            if (part != null) {
                return part;
            }
        }
        return null;
    }

    /** Whether EMF replaced this model with its own parts. */
    public static boolean isEmf(ModelPart root) {
        return root.getClass() != ModelPart.class;
    }

    /**
     * Where a part is drawn: its world pose, and the corner of its first box in the part's own space.
     * EMF parts use their own local coordinates, so the box corner is read rather than assumed.
     */
    public record Placement(PoseStack.Pose pose, float minX, float minY, float minZ) {
    }

    /**
     * Placements of the model's parts, as its own traversal computes them from {@code base}. Only
     * parts with geometry are reached. Keys are the exact part names found.
     */
    public static Map<String, Placement> placements(ModelPart root, PoseStack base) {
        Map<String, Placement> placements = new HashMap<>();
        root.visit(base, (pose, path, cubeIndex, cube) -> {
            if (cubeIndex == 0) {
                placements.putIfAbsent(path.substring(path.lastIndexOf('/') + 1),
                        new Placement(pose.copy(), cube.minX, cube.minY, cube.minZ));
            }
        });
        return placements;
    }

    /** The placement of the first named part found, preferring EMF's own parts. */
    public static @Nullable Placement placement(Map<String, Placement> placements, List<String> names) {
        for (String name : names) {
            Placement placement = placements.get(EMF_PREFIX + name);
            if (placement != null) {
                return placement;
            }
        }
        for (String name : names) {
            Placement placement = placements.get(name);
            if (placement != null) {
                return placement;
            }
        }
        return null;
    }
}
