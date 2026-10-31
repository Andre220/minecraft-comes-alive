package net.conczin.mca.client.render;

import net.conczin.mca.client.resources.WorkAnimationClip;
import net.conczin.mca.client.model.WorkAnimationPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import java.util.Map;
import org.joml.Matrix4f;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Implemented on vanilla {@code VillagerRenderState}. Carries the MCA visuals of a human-like
 * squidward villager, without marking the vanilla state as an MCA-rendered villager.
 */
public interface VanillaVillagerLookHolder {
    @Nullable VillagerVisuals mca$getLookVisuals();

    void mca$setLookVisuals(@Nullable VillagerVisuals visuals);

    /** Main hand item resolved for a right hand, drawn in the player-shaped arm. */
    ItemStackRenderState mca$getHandItem();

    /** Off hand item resolved for a left hand. */
    ItemStackRenderState mca$getOffHandItem();

    /** What is worn on the head, chest, legs and feet, in that order. */
    ItemStack[] mca$getArmor();

    /**
     * Where the armor goes on the animated model, part by part (armor part name to its pose in model space), worked
     * out by the armor layer when it submits and read back when the armor is drawn.
     */
    Map<String, Matrix4f> mca$getArmorPoses();

    /** A humanoid state for drawing an elytra on this villager (the elytra model only takes humanoid states). */
    HumanoidRenderState mca$getWingsState();

    /** Where the bobber of the villager's fishing rod is, a little above it, or null with none out. */
    @Nullable Vec3 mca$getFishingHook();

    void mca$setFishingHook(@Nullable Vec3 hook);

    /** The work animation playing, or null. */
    WorkAnimationPlayer.@Nullable Pose mca$getWorkPose();

    void mca$setWorkPose(WorkAnimationPlayer.@Nullable Pose pose);

    /** What the work animation carries in front of the body (like the hauler's crate), or null; and its item. */
    WorkAnimationClip.@Nullable Carry mca$getCarry();

    void mca$setCarry(WorkAnimationClip.@Nullable Carry carry);

    ItemStackRenderState mca$getCarriedItem();
}
