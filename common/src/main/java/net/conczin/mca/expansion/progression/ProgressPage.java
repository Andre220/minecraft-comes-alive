package net.conczin.mca.expansion.progression;

import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.conczin.mca.MCA;
import net.conczin.mca.network.Network;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Progress" page of the village screen: the village's path of stages, drawn from what the
 * server sent. Client only.
 */
public final class ProgressPage {
    public static final String PAGE = "progress";
    /** The widest the text gets; narrower when the screen has less room. */
    private static final int WIDTH = 300;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 6;
    private static final Identifier PANEL = MCA.locate("interact/panel");

    private static int scroll;

    private ProgressPage() {
    }

    /** The village screen's tabs, with this page while the feature is on. */
    public static String[] pages(String[] pages) {
        if (!VillageGrowth.enabled()) {
            return pages;
        }
        String[] all = new String[pages.length + 1];
        System.arraycopy(pages, 0, all, 0, pages.length);
        all[pages.length] = PAGE;
        return all;
    }

    /** Opening the page asks the server for the village's progress. */
    public static void open(int villageId) {
        scroll = 0;
        ProgressCache.clear();
        Network.sendToServer(new ProgressMessages.Request(villageId));
    }

    public static void scroll(double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount));
    }

    /**
     * Draws the page in the area right of the screen's tabs: from {@code left} to the screen's right edge, from
     * {@code top} down to {@code bottom}, wrapped to fit and scrolled with the mouse wheel.
     */
    public static void render(GuiGraphicsExtractor context, Font font, int left, int top, int right, int bottom) {
        int width = Math.min(WIDTH, right - left - PADDING * 2);
        if (ProgressCache.isWaiting()) {
            context.text(font, Component.translatable("gui.blueprint.waiting"), left + PADDING, top + PADDING, 0xffaaaaaa);
            return;
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : ProgressCache.lines()) {
            List<FormattedCharSequence> split = font.split(line, width);
            if (split.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            }
            wrapped.addAll(split);
        }
        int maxLines = Math.max(3, (bottom - top - PADDING * 2 - LINE_HEIGHT) / LINE_HEIGHT);
        scroll = Math.min(scroll, Math.max(0, wrapped.size() - maxLines));
        int shown = Math.min(maxLines, wrapped.size() - scroll);
        int panelBottom = top + PADDING * 2 + shown * LINE_HEIGHT + (wrapped.size() > maxLines ? LINE_HEIGHT : 0);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left, top, width + PADDING * 2, panelBottom - top);
        int x = left + PADDING;
        for (int i = 0; i < shown; i++) {
            context.text(font, wrapped.get(scroll + i), x, top + PADDING + i * LINE_HEIGHT, 0xffffffff, true);
        }
        if (wrapped.size() > maxLines) {
            context.text(font, Component.literal((scroll + 1) + "-" + Math.min(wrapped.size(), scroll + maxLines) + " / " + wrapped.size()),
                    x, top + PADDING + shown * LINE_HEIGHT, 0xff888888);
        }
    }
}
