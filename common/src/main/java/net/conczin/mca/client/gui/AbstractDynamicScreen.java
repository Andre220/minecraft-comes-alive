package net.conczin.mca.client.gui;

import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.client.resources.Icon;
import net.conczin.mca.entity.interaction.Constraint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.*;

public abstract class AbstractDynamicScreen extends Screen {
    protected static final float iconScale = 1.5f;

    // Tracks which page we're on in the GUI for sending button events
    private String activeScreen = "main";

    private int mouseX;
    private int mouseY;

    private Set<Constraint> constraints = new HashSet<>();

    protected AbstractDynamicScreen(Component title) {
        super(title);
    }

    public String getActiveScreen() {
        return activeScreen;
    }

    public Set<Constraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(Set<Constraint> constraints) {
        this.constraints = constraints;
        setLayout(activeScreen);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    protected abstract void buttonPressed(MCAButton button);

    protected void disableButton(String id) {
        children().forEach(b -> {
            if (b instanceof ApiWidget api && b instanceof AbstractWidget widget && api.getApiButton() != null
                && api.getApiButton().identifier().equals(id)) {
                widget.active = false;
            }
        });
    }

    protected void disableAllButtons() {
        this.children().forEach(b -> {
            if (b instanceof AbstractWidget widget) {
                if (!(b instanceof ApiWidget api) || api.getApiButton() == null || !api.getApiButton().identifier().equals("gui.button.backarrow")) {
                    widget.active = false;
                }
            }
        });
    }

    /**
     * Whether the buttons are arranged in a circle around the center of the screen (the radial
     * menu) instead of at the positions the layout files give them.
     */
    protected boolean useRadialLayout() {
        return false;
    }

    /** Hides a button of the radial menu, for features that are turned off. */
    protected boolean isButtonHidden(MCAButton button) {
        return false;
    }

    /**
     * Adds API buttons to the GUI screen provided.
     *
     * @param guiKey String key for the GUI's buttons
     */
    public void setLayout(String guiKey) {
        activeScreen = guiKey;

        clearWidgets();
        MCAScreens.getInstance().getScreen(guiKey).ifPresent(buttons -> {
            if (useRadialLayout()) {
                layoutRadial(buttons);
                return;
            }
            for (MCAButton b : buttons) {
                addRenderableWidget(new ButtonEx(b, this));
            }
        });
    }

    /**
     * The radial menu: the visible buttons evenly on a circle around the screen center (two
     * buttons sit left and right, more start at the top), the back button in the middle.
     */
    private void layoutRadial(MCAButton[] buttons) {
        List<RadialEntry> ring = new ArrayList<>();
        RadialEntry back = null;
        for (MCAButton b : buttons) {
            boolean valid = b.isValidForConstraint(getConstraints());
            if (!valid && b.hideOnFail() || isButtonHidden(b)) {
                continue;
            }
            RadialEntry entry = new RadialEntry(getButtonMessage(b, this), RadialIcons.iconFor(b.identifier()), valid, () -> buttonPressed(b), b);
            if (b.identifier().equals("gui.button.backarrow")) {
                back = entry;
            } else {
                ring.add(entry);
            }
        }
        addRadialRing(ring, back);
    }

    /** An action of the radial menu: its name, the item standing for it and what it does. */
    protected record RadialEntry(Component label, ItemStack icon, boolean active, Runnable action, @Nullable MCAButton api) {
    }

    /** Radius of the last radial ring, so text can be placed around it. */
    protected int radialRadius = 70;

    /**
     * Adds entries evenly on a circle around the screen center (two sit left and right, more start
     * at the top), and an optional one in the middle.
     */
    protected void addRadialRing(List<RadialEntry> ring, @Nullable RadialEntry center) {
        int centerX = width / 2;
        int centerY = height / 2;
        int count = ring.size();
        radialRadius = Math.max(70, (int) (count * (RadialButton.SIZE + 26) / (2 * Math.PI)));
        double start = count == 2 ? Math.PI : -Math.PI / 2;
        long openedAt = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            double angle = start + i * 2 * Math.PI / count;
            int x = centerX + (int) Math.round(Math.cos(angle) * radialRadius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radialRadius);
            addRenderableWidget(new RadialButton(ring.get(i), x, y, centerX, centerY, openedAt));
        }
        if (center != null) {
            addRenderableWidget(new RadialButton(center, centerX, centerY, centerX, centerY, openedAt));
        }
    }

    protected void drawIcon(GuiGraphicsExtractor context, Identifier texture, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        context.blit(RenderPipelines.GUI_TEXTURED, texture, (int) (icon.x() / iconScale), (int) (icon.y() / iconScale), icon.u(), icon.v(), 16, 16, 256, 256);
    }

    protected void drawHoveringIconText(GuiGraphicsExtractor context, Component text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        drawTooltip(context, text, icon.x() + 16, icon.y() + 20);
    }

    protected void drawHoveringIconText(GuiGraphicsExtractor context, List<Component> text, String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        drawTooltip(context, text, icon.x() + 16, icon.y() + 20);
    }

    protected void drawTooltip(GuiGraphicsExtractor context, Component text, int x, int y) {
        drawTooltip(context, List.of(text), x, y);
    }

    protected void drawTooltip(GuiGraphicsExtractor context, List<Component> text, int x, int y) {
        WidgetUtils.drawTooltip(context, font, text, x, y);
    }

    //checks if the mouse hovers over a specified button
    protected boolean hoveringOverIcon(String key) {
        Icon icon = MCAScreens.getInstance().getIcon(key);
        return hoveringOver(icon.x(), icon.y(), (int) (16 * iconScale), (int) (16 * iconScale));
    }

    //checks if the mouse hovers over a rectangle
    protected boolean hoveringOver(int x, int y, int w, int h) {
        return mouseX > x && mouseX < x + w && mouseY > y && mouseY < y + h;
    }

    private enum Alignment {
        TOP_LEFT(0.0f, 0.0f),
        TOP(0.5f, 0.0f),
        TOP_RIGHT(1.0f, 0.0f),
        RIGHT(1.0f, 0.5f),
        BOTTOM_RIGHT(1.0f, 1.0f),
        BOTTOM(0.5f, 1.0f),
        BOTTOM_LEFT(0.0f, 1.0f),
        LEFT(0.0f, 0.5f),
        CENTER(0.5f, 0.5f);

        static final Map<String, Alignment> alignments = new HashMap<>();

        static {
            for (Alignment a : Alignment.values()) {
                alignments.put(a.name().toLowerCase(Locale.ENGLISH), a);
            }
        }

        final float h;
        final float v;

        Alignment(float h, float v) {
            this.h = h;
            this.v = v;
        }
    }

    private interface ApiWidget {
        @Nullable MCAButton getApiButton();
    }

    private static Component getButtonMessage(MCAButton apiButton, AbstractDynamicScreen screen) {
        if ("gui.button.ridehorse".equals(apiButton.identifier())
                && screen.getConstraints().contains(Constraint.RIDING)) {
            return Component.translatable("gui.button.dismount");
        }
        return Component.translatable(apiButton.identifier());
    }

    /**
     * A button of the radial menu: a vanilla item standing for the action, its name below, and a
     * short slide out from the center when the menu opens.
     */
    private static class RadialButton extends AbstractWidget implements ApiWidget {
        static final int SIZE = 26;
        private static final long OPEN_MILLIS = 150;
        private final RadialEntry entry;
        private final ItemStack icon;
        private final int fromX;
        private final int fromY;
        private final long openedAt;

        RadialButton(RadialEntry entry, int centerX, int centerY, int fromX, int fromY, long openedAt) {
            super(centerX - SIZE / 2, centerY - SIZE / 2, SIZE, SIZE, entry.label());
            this.entry = entry;
            this.icon = entry.icon();
            this.fromX = fromX - SIZE / 2;
            this.fromY = fromY - SIZE / 2;
            this.openedAt = openedAt;
            this.active = entry.active();
        }

        @Override
        public @Nullable MCAButton getApiButton() {
            return entry.api();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            float t = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / (float) OPEN_MILLIS);
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            int x = Math.round(fromX + (getX() - fromX) * ease);
            int y = Math.round(fromY + (getY() - fromY) * ease);

            boolean hover = active && isHovered();
            int background = !active ? 0x88202020 : hover ? 0xDD3A3A3A : 0xAA000000;
            // A rough rounded square.
            context.fill(x + 2, y, x + SIZE - 2, y + SIZE, background);
            context.fill(x, y + 2, x + 2, y + SIZE - 2, background);
            context.fill(x + SIZE - 2, y + 2, x + SIZE, y + SIZE - 2, background);
            if (hover) {
                context.horizontalLine(x + 2, x + SIZE - 3, y, 0xFFFFFFFF);
                context.horizontalLine(x + 2, x + SIZE - 3, y + SIZE - 1, 0xFFFFFFFF);
                context.verticalLine(x, y + 1, y + SIZE - 2, 0xFFFFFFFF);
                context.verticalLine(x + SIZE - 1, y + 1, y + SIZE - 2, 0xFFFFFFFF);
            }
            context.item(icon, x + (SIZE - 16) / 2, y + (SIZE - 16) / 2);
            if (!active) {
                context.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, 0x88000000);
            }

            if (t >= 1.0F) {
                var font = Minecraft.getInstance().font;
                Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.translate(x + SIZE / 2.0F, y + SIZE + 2);
                matrices.scale(0.75F, 0.75F);
                context.centeredText(font, getMessage(), 0, 0, !active ? 0xFF888888 : hover ? 0xFFFFFF55 : 0xFFFFFFFF);
                matrices.popMatrix();
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            playButtonClickSound(Minecraft.getInstance().getSoundManager());
            entry.action().run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            defaultButtonNarrationText(builder);
        }
    }

    private static class ButtonEx extends Button implements ApiWidget {
        private final MCAButton apiButton;

        public ButtonEx(MCAButton apiButton, AbstractDynamicScreen screen) {
            super((int) (screen.width * Alignment.alignments.get(apiButton.align()).h + apiButton.x()),
                    (int) (screen.height * Alignment.alignments.get(apiButton.align()).v + apiButton.y()),
                    apiButton.width(),
                    apiButton.height(),
                    getButtonMessage(apiButton, screen),
                    a -> screen.buttonPressed(apiButton),
                    DEFAULT_NARRATION);
            this.apiButton = apiButton;

            // Remove the button if we specify it should not be present on constraint failure
            // Otherwise we just mark the button as disabled.
            if (!apiButton.isValidForConstraint(screen.getConstraints())) {
                if (apiButton.hideOnFail()) {
                    visible = false;
                }
                active = false;
            }
        }

        @Override
        public MCAButton getApiButton() {
            return apiButton;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }
}
