package net.conczin.mca.client.gui.widget;

import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * A button with a Minecraft item as its icon next to its label. The icon hops up a little
 * under the mouse, and a highlighted button (the picked tool) gets a golden frame instead of
 * going grey, so it still reads as the one in use.
 */
public class IconButtonWidget extends ButtonWidget {
    private static final int ICON_SIZE = 16;
    private static final int HIGHLIGHT_COLOR = 0xFFFFD24A;

    private final ItemStack icon;
    private boolean highlighted;

    public IconButtonWidget(int x, int y, int width, int height, ItemStack icon, Component message, OnPress onPress, @Nullable Component tooltip) {
        super(x, y, width, height, message, onPress);
        this.icon = icon;
        if (tooltip != null) {
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip));
        }
    }

    public IconButtonWidget highlighted(boolean highlighted) {
        this.highlighted = highlighted;
        return this;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractDefaultSprite(graphics);
        if (highlighted) {
            graphics.outline(getX(), getY(), getWidth(), getHeight(), HIGHLIGHT_COLOR);
        }

        boolean iconOnly = getMessage().getString().isEmpty();
        int iconX = iconOnly ? getX() + (getWidth() - ICON_SIZE) / 2 : getX() + 3;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2 - (isHovered() && active ? 1 : 0);
        graphics.item(icon, iconX, iconY);
        if (iconOnly) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int textLeft = iconX + ICON_SIZE + 3;
        int available = getX() + getWidth() - 3 - textLeft;
        int textWidth = font.width(getMessage());
        int textY = getY() + (getHeight() - 8) / 2;
        int color = !active ? 0xFFA0A0A0 : highlighted ? HIGHLIGHT_COLOR : 0xFFFFFFFF;
        if (textWidth <= available || available <= 0) {
            graphics.text(font, getMessage(), textLeft, textY, color, true);
        } else {
            // squeeze long labels instead of cutting them
            float scale = (float) available / textWidth;
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(textLeft, textY + 4);
            pose.scale(scale, scale);
            graphics.text(font, getMessage(), 0, -4, color, true);
            pose.popMatrix();
        }
    }
}
