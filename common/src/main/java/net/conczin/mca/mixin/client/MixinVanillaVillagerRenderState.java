package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VanillaVillagerLookHolder;
import net.conczin.mca.client.render.VillagerVisuals;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public class MixinVanillaVillagerRenderState implements VanillaVillagerLookHolder {
    @Unique
    private @Nullable VillagerVisuals mca$lookVisuals;
    @Unique
    private final ItemStackRenderState mca$handItem = new ItemStackRenderState();

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
}
