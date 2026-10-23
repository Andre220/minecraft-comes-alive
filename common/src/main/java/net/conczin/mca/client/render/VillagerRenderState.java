package net.conczin.mca.client.render;

import net.minecraft.network.chat.Component;
import java.util.List;
import net.conczin.mca.client.resources.WorkAnimationClip;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class VillagerRenderState extends UndeadRenderState {
    public boolean isConverting;
    /** What the villager is saying, shown in a bubble above it. */ // expansion: speech
    public List<Component> speechLines = List.of();
    /** What the villager is doing, shown above it to players close by. */ // expansion: minds
    public @Nullable Component actionLabel;
    public float panicAnimationProgress;
    public @Nullable Vec3 fishingHookPosition;
    public @Nullable WorkAnimationClip workAnimationClip;
    public float workAnimationPhase;
    public float workAnimationBlend;
    /** What the work animation carries in front of the body, empty when nothing. */
    public final ItemStackRenderState carriedItem = new ItemStackRenderState();
    public WorkAnimationClip.@Nullable Carry carry;
}
