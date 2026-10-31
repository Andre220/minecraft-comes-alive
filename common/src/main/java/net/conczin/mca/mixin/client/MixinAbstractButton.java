package net.conczin.mca.mixin.client;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;

/**
 * On MCA's own screens, buttons wear MCA's sprites (textures/gui/sprites/widget) instead of the stone-grey vanilla ones,
 * unless the client config mcaButtonStyle is off, which leaves them to vanilla and its resource packs.
 */
@Mixin(AbstractButton.class)
abstract class MixinAbstractButton {
    private static final Map<Identifier, Identifier> MCA_SPRITES = Map.of(
            Identifier.withDefaultNamespace("widget/button"), MCA.locate("widget/button"),
            Identifier.withDefaultNamespace("widget/button_highlighted"), MCA.locate("widget/button_highlighted"),
            Identifier.withDefaultNamespace("widget/button_disabled"), MCA.locate("widget/button_disabled")
    );

    @ModifyArg(method = "extractDefaultSprite", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"),
            index = 1)
    private Identifier mca$buttonSprite(Identifier sprite) {
        if (!Config.getInstance().mcaButtonStyle) {
            return sprite;
        }
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || !screen.getClass().getName().startsWith("net.conczin.mca.")) {
            return sprite;
        }
        return MCA_SPRITES.getOrDefault(sprite, sprite);
    }
}
