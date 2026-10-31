package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * One look choice of the villager editor (skins, clothes, hair, beards, faces) as a grid of small previews: the
 * villager as it is, wearing each choice, framed on what the choice changes (the whole body, the head, the face). The
 * one worn has a golden rim; the one under the mouse is what the editor's large preview shows meanwhile.
 */
final class LookGrid {
    private static final Identifier PANEL = MCA.locate("interact/panel");
    private static final Identifier CELL = MCA.locate("interact/cell");
    private static final int WORN = 0xFFFFD24A;
    private static final int HOVER = 0xFFFFFFFF;

    /** What a card shows: how much of the villager's height, centered how far above its middle, and the card size. */
    enum Frame {
        BODY(1.08F, 0.0F, 35, 52),
        HEAD(0.46F, 0.25F, 43, 50),
        FACE(0.24F, 0.37F, 43, 50);

        final float visible;
        final float center;
        final int width;
        final int height;

        Frame(float visible, float center, int width, int height) {
            this.visible = visible;
            this.center = center;
            this.width = width;
            this.height = height;
        }
    }

    private final Frame frame;
    private final List<String> choices;
    private final @Nullable String worn;
    private final BiConsumer<VillagerEntityMCA, String> wear;
    private final float rotation;
    private final int left;
    private final int top;
    private final int columns;
    private final int rows;
    private int page;
    private @Nullable String hovered;

    LookGrid(Frame frame, List<String> choices, @Nullable String worn, BiConsumer<VillagerEntityMCA, String> wear, float rotation,
             int left, int top, int width, int height, int page) {
        this.frame = frame;
        this.choices = choices;
        this.worn = worn;
        this.wear = wear;
        this.rotation = rotation;
        this.columns = Math.max(1, width / frame.width);
        this.rows = Math.max(1, height / frame.height);
        this.left = left + (width - columns * frame.width) / 2;
        this.top = top;
        // open on the page with the worn choice
        int index = worn == null ? -1 : choices.indexOf(worn);
        this.page = page >= 0 ? Math.clamp(page, 0, pages() - 1) : Math.max(0, index) / perPage();
    }

    int perPage() {
        return columns * rows;
    }

    int pages() {
        return Math.max(1, (choices.size() + perPage() - 1) / perPage());
    }

    int page() {
        return page;
    }

    void turn(int step) {
        page = Math.floorMod(page + step, pages());
    }

    boolean isEmpty() {
        return choices.isEmpty();
    }

    /** The choice under the mouse in the last frame, for the large preview. */
    @Nullable String hovered() {
        return hovered;
    }

    /** Draws the page's cards; {@code reset} puts the shared preview villager back as the edited one. */
    void render(VillagerEditorScreen screen, GuiGraphicsExtractor context, Font font, VillagerEntityMCA preview, Runnable reset,
                int mouseX, int mouseY, float delta) {
        hovered = null;
        int start = page * perPage();
        int end = Math.min(choices.size(), start + perPage());
        for (int index = start; index < end; index++) {
            String choice = choices.get(index);
            int slot = index - start;
            int x = left + slot % columns * frame.width;
            int y = top + slot / columns * frame.height;
            boolean over = mouseX >= x && mouseX < x + frame.width && mouseY >= y && mouseY < y + frame.height;
            if (over) {
                hovered = choice;
            }
            context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x + 1, y + 1, frame.width - 2, frame.height - 2);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, CELL, x + 2, y + 2, frame.width - 4, frame.height - 4);

            reset.run();
            wear.accept(preview, choice);
            float height = Math.max(0.5F, preview.getBbHeight());
            int inner = frame.height - 6;
            int size = Math.round(inner / (height * frame.visible) * (over ? 1.06F : 1.0F));
            screen.extractLookPreview(context, x + 3, y + 3, x + frame.width - 3, y + frame.height - 3, size,
                    height * frame.center, mouseX, mouseY, delta, preview, rotation);

            if (Objects.equals(choice, worn)) {
                context.outline(x + 1, y + 1, frame.width - 2, frame.height - 2, WORN);
            } else if (over) {
                context.outline(x + 1, y + 1, frame.width - 2, frame.height - 2, HOVER);
            }
        }
        reset.run();
        if (choices.isEmpty()) {
            context.centeredText(font, Component.translatable("gui.villager_editor.no_choices"),
                    left + columns * frame.width / 2, top + 20, 0xFF888888);
        }
    }

    /** The choice clicked, if a card was. */
    @Nullable String click(double mouseX, double mouseY) {
        if (mouseX < left || mouseY < top) {
            return null;
        }
        int column = (int) ((mouseX - left) / frame.width);
        int row = (int) ((mouseY - top) / frame.height);
        if (column >= columns || row >= rows) {
            return null;
        }
        int index = page * perPage() + row * columns + column;
        return index < choices.size() ? choices.get(index) : null;
    }
}
