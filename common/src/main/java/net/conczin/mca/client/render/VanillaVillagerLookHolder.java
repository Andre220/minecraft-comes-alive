package net.conczin.mca.client.render;

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
}
