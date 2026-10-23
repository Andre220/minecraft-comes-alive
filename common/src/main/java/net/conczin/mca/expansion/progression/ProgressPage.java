package net.conczin.mca.expansion.progression;

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
    private static final int WIDTH = 300;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = 20;

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

    public static void render(GuiGraphicsExtractor context, Font font, int screenWidth, int top) {
        if (ProgressCache.isWaiting()) {
            context.centeredText(font, Component.translatable("gui.blueprint.waiting"), screenWidth / 2, top + 40, 0xffaaaaaa);
            return;
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : ProgressCache.lines()) {
            List<FormattedCharSequence> split = font.split(line, WIDTH);
            if (split.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            }
            wrapped.addAll(split);
        }
        scroll = Math.min(scroll, Math.max(0, wrapped.size() - MAX_LINES));
        int x = screenWidth / 2 - WIDTH / 2;
        for (int i = 0; i < MAX_LINES && scroll + i < wrapped.size(); i++) {
            context.text(font, wrapped.get(scroll + i), x, top + i * LINE_HEIGHT, 0xffffffff, true);
        }
        if (wrapped.size() > MAX_LINES) {
            context.centeredText(font, Component.literal((scroll + 1) + "-" + Math.min(wrapped.size(), scroll + MAX_LINES) + " / " + wrapped.size()),
                    screenWidth / 2, top + MAX_LINES * LINE_HEIGHT + 4, 0xff888888);
        }
    }
}
