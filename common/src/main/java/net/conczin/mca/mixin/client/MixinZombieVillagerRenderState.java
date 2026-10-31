package net.conczin.mca.mixin.client;

import net.conczin.mca.client.resources.WorkAnimationClip;
import net.conczin.mca.client.model.WorkAnimationPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

/**
 * Zombie villagers carry an MCA look too. Their renderer is a humanoid one with its own item, armor and head layers,
 * so only the look itself is used; the rest is there to satisfy the holder.
 */
@Mixin(ZombieVillagerRenderState.class)
public class MixinZombieVillagerRenderState implements VanillaVillagerLookHolder {
    @Unique
    private @Nullable VillagerVisuals mca$lookVisuals;
    @Unique
    private final ItemStackRenderState mca$handItem = new ItemStackRenderState();
    @Unique
    private final ItemStackRenderState mca$offHandItem = new ItemStackRenderState();
    @Unique
    private final ItemStack[] mca$armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    @Unique
    private final Map<String, Matrix4f> mca$armorPoses = new HashMap<>();
    @Unique
    private final HumanoidRenderState mca$wingsState = new HumanoidRenderState();
    @Unique
    private @Nullable Vec3 mca$fishingHook;
    @Unique
    private WorkAnimationPlayer.@Nullable Pose mca$workPose;
    @Unique
    private WorkAnimationClip.@Nullable Carry mca$carry;
    @Unique
    private final ItemStackRenderState mca$carriedItem = new ItemStackRenderState();

    @Override
    public @Nullable VillagerVisuals mca$getLookVisuals() {
        return mca$lookVisuals;
    }

    @Override
    public void mca$setLookVisuals(@Nullable VillagerVisuals visuals) {
        this.mca$lookVisuals = visuals;
    }

    @Override
    public ItemStackRenderState mca$getHandItem() {
        return mca$handItem;
    }

    @Override
    public ItemStackRenderState mca$getOffHandItem() {
        return mca$offHandItem;
    }

    @Override
    public ItemStack[] mca$getArmor() {
        return mca$armor;
    }

    @Override
    public Map<String, Matrix4f> mca$getArmorPoses() {
        return mca$armorPoses;
    }

    @Override
    public HumanoidRenderState mca$getWingsState() {
        return mca$wingsState;
    }

    @Override
    public @Nullable Vec3 mca$getFishingHook() {
        return mca$fishingHook;
    }

    @Override
    public void mca$setFishingHook(@Nullable Vec3 hook) {
        this.mca$fishingHook = hook;
    }

    @Override
    public WorkAnimationPlayer.@Nullable Pose mca$getWorkPose() {
        return mca$workPose;
    }

    @Override
    public void mca$setWorkPose(WorkAnimationPlayer.@Nullable Pose pose) {
        this.mca$workPose = pose;
    }

    @Override
    public WorkAnimationClip.@Nullable Carry mca$getCarry() {
        return mca$carry;
    }

    @Override
    public void mca$setCarry(WorkAnimationClip.@Nullable Carry carry) {
        this.mca$carry = carry;
    }

    @Override
    public ItemStackRenderState mca$getCarriedItem() {
        return mca$carriedItem;
    }
}
