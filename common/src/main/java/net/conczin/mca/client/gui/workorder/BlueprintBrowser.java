package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.BlueprintCatalogMessage;
import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The blueprint library down the left side: section tabs along the top (like a building menu), a grid of cards, a
 * search box. Right click on a card or a section opens a small menu to sort, hide or delete it.
 */
final class BlueprintBrowser {
    private static final int CARD_WIDTH = 74;
    private static final int CARD_HEIGHT = 56;
    /** The picture of the building on top of a card; the name goes below. */
    private static final int THUMBNAIL_HEIGHT = 42;
    private static final int GAP = 4;
    static final int WIDTH = 2 * CARD_WIDTH + 3 * GAP;
    private static final int TAB = 20;
    private static final int TAB_GAP = 2;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MENU_WIDTH = 120;
    private static final int MENU_ROW = 12;
    private static final int SELECTED_COLOR = 0xFFFFD24A;
    static final String ALL = "*all";
    static final String HIDDEN = "*hidden";
    private static final String NEW = "*new";

    private record Tab(String id, ItemStack icon, Component name) {
    }

    private record MenuEntry(Component label, Runnable action) {
    }

    private final Consumer<String> onSelect;
    private final Runnable onNewSection;
    /** The blocks of a blueprint for its picture; null while they are still on their way. */
    private final Function<String, @Nullable Blueprint> blocks;
    private @Nullable WorkOrdersResponse data;
    private String tab = ALL;
    private int scroll;
    private int x;
    private int y;
    private int height;
    private @Nullable EditBox search;
    private List<MenuEntry> menu = List.of();
    private int menuX;
    private int menuY;

    BlueprintBrowser(Consumer<String> onSelect, Runnable onNewSection, Function<String, @Nullable Blueprint> blocks) {
        this.onSelect = onSelect;
        this.onNewSection = onNewSection;
        this.blocks = blocks;
    }

    void setData(@Nullable WorkOrdersResponse data) {
        this.data = data;
        if (data != null && !tab.equals(ALL) && !tab.equals(HIDDEN) && data.sections().stream().noneMatch(section -> section.id().equals(tab))) {
            tab = ALL;
        }
    }

    void setBounds(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    EditBox createSearch(Font font) {
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, x + 4, searchTop(), WIDTH - 8, SEARCH_HEIGHT, Component.translatable("gui.workorder.catalog.search"));
        search.setHint(Component.translatable("gui.workorder.catalog.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(previous);
        search.setResponder(text -> scroll = 0);
        return search;
    }

    boolean isSearchFocused() {
        return search != null && search.isFocused();
    }

    /** The section new captures go to: the open one, or the default one. */
    String currentSection() {
        return tab.startsWith("*") ? "" : tab;
    }

    static ItemStack iconOf(String icon) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(icon)).map(ItemStack::new).orElseGet(() -> new ItemStack(Items.MAP));
    }

    static Component sectionName(WorkOrdersResponse.SectionInfo section) {
        return Component.translatableWithFallback(section.name(), section.name());
    }

    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(ALL, new ItemStack(Items.NETHER_STAR), Component.translatable("gui.workorder.blueprints.all")));
        if (data != null) {
            for (WorkOrdersResponse.SectionInfo section : data.sections()) {
                tabs.add(new Tab(section.id(), iconOf(section.icon()), sectionName(section)));
            }
            if (data.blueprints().stream().anyMatch(WorkOrdersResponse.BlueprintInfo::hidden)) {
                tabs.add(new Tab(HIDDEN, new ItemStack(Items.ENDER_EYE), Component.translatable("gui.workorder.blueprints.hidden")));
            }
            if (data.canManageBlueprints()) {
                tabs.add(new Tab(NEW, new ItemStack(Items.WRITABLE_BOOK), Component.translatable("gui.workorder.section.new")));
            }
        }
        return tabs;
    }

    private int tabsPerRow() {
        return Math.max(1, (WIDTH - 4 + TAB_GAP) / (TAB + TAB_GAP));
    }

    private int tabTop(int index) {
        return y + 4 + (index / tabsPerRow()) * (TAB + TAB_GAP);
    }

    private int tabLeft(int index) {
        return x + 4 + (index % tabsPerRow()) * (TAB + TAB_GAP);
    }

    private int searchTop() {
        int rows = (tabs().size() + tabsPerRow() - 1) / tabsPerRow();
        return y + 4 + rows * (TAB + TAB_GAP) + 2;
    }

    private int gridTop() {
        return searchTop() + SEARCH_HEIGHT + 4;
    }

    private int visibleRows() {
        return Math.max(1, (y + height - 2 - gridTop()) / (CARD_HEIGHT + GAP));
    }

    boolean isOver(double mouseX, double mouseY) {
        boolean overMenu = !menu.isEmpty() && mouseX >= menuX && mouseX < menuX + MENU_WIDTH
                           && mouseY >= menuY && mouseY < menuY + menu.size() * MENU_ROW + 4;
        return overMenu || mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
    }

    private List<WorkOrdersResponse.BlueprintInfo> cards() {
        if (data == null) {
            return List.of();
        }
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return data.blueprints().stream()
                .filter(info -> tab.equals(HIDDEN) ? info.hidden() : !info.hidden())
                .filter(info -> tab.equals(ALL) || tab.equals(HIDDEN) || info.section().equals(tab))
                .filter(info -> query.isEmpty() || WorkPlanningScreen.blueprintName(info).getString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, @Nullable String selected) {
        graphics.fill(x, y, x + WIDTH, y + height, 0xB0000000);
        graphics.outline(x, y, WIDTH, height, 0xFF505050);
        boolean menuOpen = !menu.isEmpty();

        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            Tab candidate = tabs.get(i);
            int tabX = tabLeft(i);
            int tabY = tabTop(i);
            boolean active = candidate.id().equals(tab);
            boolean hovered = !menuOpen && mouseX >= tabX && mouseX < tabX + TAB && mouseY >= tabY && mouseY < tabY + TAB;
            graphics.fill(tabX, tabY, tabX + TAB, tabY + TAB, active ? 0xFF6A5A2A : hovered ? 0xFF4A4A4A : 0xFF2A2A2A);
            if (active) {
                graphics.outline(tabX, tabY, TAB, TAB, SELECTED_COLOR);
            }
            graphics.item(candidate.icon(), tabX + 2, tabY + 2);
            if (candidate.id().equals(NEW)) {
                graphics.text(font, "+", tabX + TAB - 6, tabY + TAB - 9, 0xFF7CFF8A, true);
            }
            if (hovered) {
                graphics.setTooltipForNextFrame(font, candidate.name(), mouseX, mouseY);
            }
        }

        List<WorkOrdersResponse.BlueprintInfo> cards = cards();
        int rows = visibleRows();
        int maxScroll = Math.max(0, (cards.size() + 1) / 2 - rows);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int top = gridTop();
        if (cards.isEmpty()) {
            graphics.text(font, Component.translatable(data != null && data.blueprints().isEmpty()
                    ? "gui.workorder.blueprints.empty" : "gui.workorder.blueprints.none_here"), x + 6, top + 4, 0xFFA0A0A0);
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < 2; column++) {
                int index = (row + scroll) * 2 + column;
                if (index >= cards.size()) {
                    break;
                }
                WorkOrdersResponse.BlueprintInfo info = cards.get(index);
                int cardX = x + GAP + column * (CARD_WIDTH + GAP);
                int cardY = top + row * (CARD_HEIGHT + GAP);
                boolean hovered = !menuOpen && mouseX >= cardX && mouseX < cardX + CARD_WIDTH && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
                boolean picked = info.id().equals(selected);
                graphics.fill(cardX, cardY, cardX + CARD_WIDTH, cardY + CARD_HEIGHT, picked ? 0xFF4A3F1F : hovered ? 0xFF3A3A3A : 0xFF222222);
                graphics.outline(cardX, cardY, CARD_WIDTH, CARD_HEIGHT, picked ? SELECTED_COLOR : 0xFF555555);
                Blueprint blueprint = blocks.apply(info.id());
                if (blueprint == null || !BlueprintThumbnails.draw(graphics, blueprint, cardX + 2, cardY + 2, CARD_WIDTH - 4, THUMBNAIL_HEIGHT)) {
                    graphics.item(iconOf(info.icon()), cardX + (CARD_WIDTH - 16) / 2, cardY + (THUMBNAIL_HEIGHT - 16) / 2 + 2);
                }
                squeezed(graphics, font, WorkPlanningScreen.blueprintName(info), cardX + 3, cardY + THUMBNAIL_HEIGHT + 3, CARD_WIDTH - 6,
                        info.hidden() ? 0xFF909090 : 0xFFFFFFFF);
                if (hovered) {
                    graphics.setComponentTooltipForNextFrame(font, List.of(
                            WorkPlanningScreen.blueprintName(info),
                            Component.translatable("gui.workorder.blueprint.info", info.sizeX(), info.sizeY(), info.sizeZ(), info.blocks())
                                    .withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.workorder.blueprint.right_click").withStyle(ChatFormatting.DARK_GRAY)
                    ), mouseX, mouseY);
                }
            }
        }

        if (menuOpen) {
            int menuHeight = menu.size() * MENU_ROW + 4;
            graphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + menuHeight, 0xF0101010);
            graphics.outline(menuX, menuY, MENU_WIDTH, menuHeight, 0xFF808080);
            for (int i = 0; i < menu.size(); i++) {
                int rowY = menuY + 2 + i * MENU_ROW;
                boolean hovered = mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= rowY && mouseY < rowY + MENU_ROW;
                if (hovered) {
                    graphics.fill(menuX + 1, rowY, menuX + MENU_WIDTH - 1, rowY + MENU_ROW, 0xFF404040);
                }
                squeezed(graphics, font, menu.get(i).label(), menuX + 4, rowY + 2, MENU_WIDTH - 8, 0xFFFFFFFF);
            }
        }
    }

    /** Text squeezed to fit {@code width}. */
    private static void squeezed(GuiGraphicsExtractor graphics, Font font, Component text, int textX, int textY, int width, int color) {
        int textWidth = font.width(text);
        if (textWidth <= width) {
            graphics.text(font, text, textX, textY, color, true);
            return;
        }
        float scale = (float) width / textWidth;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(textX, textY + 4);
        pose.scale(scale, scale);
        graphics.text(font, text, 0, -4, color, true);
        pose.popMatrix();
    }

    /** @param right whether it was the right button (opens menus) */
    /** With a menu open, any click goes to it: an entry runs, a click elsewhere just closes it. */
    boolean closeMenuOnClick(double mouseX, double mouseY) {
        if (menu.isEmpty()) {
            return false;
        }
        List<MenuEntry> entries = menu;
        menu = List.of();
        if (mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= menuY + 2) {
            int index = (int) (mouseY - menuY - 2) / MENU_ROW;
            if (index >= 0 && index < entries.size()) {
                entries.get(index).action().run();
            }
        }
        return true;
    }

    boolean mouseClicked(double mouseX, double mouseY, boolean right) {
        if (closeMenuOnClick(mouseX, mouseY)) {
            return true;
        }
        if (!isOver(mouseX, mouseY)) {
            return false;
        }
        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = tabLeft(i);
            int tabY = tabTop(i);
            if (mouseX >= tabX && mouseX < tabX + TAB && mouseY >= tabY && mouseY < tabY + TAB) {
                Tab clicked = tabs.get(i);
                if (clicked.id().equals(NEW)) {
                    onNewSection.run();
                } else if (right) {
                    openSectionMenu(clicked, (int) mouseX, (int) mouseY);
                } else {
                    tab = clicked.id();
                    scroll = 0;
                }
                return true;
            }
        }
        List<WorkOrdersResponse.BlueprintInfo> cards = cards();
        int top = gridTop();
        for (int row = 0; row < visibleRows(); row++) {
            for (int column = 0; column < 2; column++) {
                int index = (row + scroll) * 2 + column;
                int cardX = x + GAP + column * (CARD_WIDTH + GAP);
                int cardY = top + row * (CARD_HEIGHT + GAP);
                if (index < cards.size() && mouseX >= cardX && mouseX < cardX + CARD_WIDTH && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT) {
                    if (right) {
                        openCardMenu(cards.get(index), (int) mouseX, (int) mouseY);
                    } else {
                        onSelect.accept(cards.get(index).id());
                    }
                    return true;
                }
            }
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    boolean closeMenu() {
        if (menu.isEmpty()) {
            return false;
        }
        menu = List.of();
        return true;
    }

    private void showMenu(List<MenuEntry> entries, int mouseX, int mouseY) {
        menu = entries;
        menuX = mouseX;
        menuY = mouseY;
    }

    private void openCardMenu(WorkOrdersResponse.BlueprintInfo info, int mouseX, int mouseY) {
        if (data == null || !data.canManageBlueprints()) {
            menu = List.of();
            return;
        }
        List<MenuEntry> entries = new ArrayList<>();
        entries.add(new MenuEntry(Component.translatable("gui.workorder.blueprint.move").withStyle(ChatFormatting.YELLOW),
                () -> showMenu(moveEntries(info), mouseX, mouseY)));
        entries.add(new MenuEntry(Component.translatable(info.hidden() ? "gui.workorder.blueprint.show" : "gui.workorder.blueprint.hide"),
                () -> send(BlueprintCatalogMessage.ofBlueprint(info.hidden() ? BlueprintCatalogMessage.SHOW : BlueprintCatalogMessage.HIDE, info.id(), ""))));
        if (info.deletable()) {
            entries.add(new MenuEntry(Component.translatable("gui.workorder.blueprint.delete").withStyle(ChatFormatting.RED),
                    () -> showMenu(List.of(
                            new MenuEntry(Component.translatable("gui.workorder.blueprint.delete.confirm", WorkPlanningScreen.blueprintName(info))
                                    .withStyle(ChatFormatting.RED),
                                    () -> send(BlueprintCatalogMessage.ofBlueprint(BlueprintCatalogMessage.DELETE, info.id(), ""))),
                            new MenuEntry(Component.translatable("gui.cancel"), () -> {
                            })
                    ), mouseX, mouseY)));
        } else {
            entries.add(new MenuEntry(Component.translatable("gui.workorder.blueprint.bundled").withStyle(ChatFormatting.DARK_GRAY), () -> {
            }));
        }
        showMenu(entries, mouseX, mouseY);
    }

    private List<MenuEntry> moveEntries(WorkOrdersResponse.BlueprintInfo info) {
        List<MenuEntry> entries = new ArrayList<>();
        if (data != null) {
            for (WorkOrdersResponse.SectionInfo section : data.sections()) {
                Component label = sectionName(section).copy();
                entries.add(new MenuEntry(section.id().equals(info.section()) ? label.copy().withStyle(ChatFormatting.GOLD) : label,
                        () -> send(BlueprintCatalogMessage.ofBlueprint(BlueprintCatalogMessage.MOVE, info.id(), section.id()))));
            }
        }
        entries.add(new MenuEntry(Component.translatable("gui.workorder.section.none").withStyle(ChatFormatting.GRAY),
                () -> send(BlueprintCatalogMessage.ofBlueprint(BlueprintCatalogMessage.MOVE, info.id(), ""))));
        return entries;
    }

    private void openSectionMenu(Tab section, int mouseX, int mouseY) {
        if (data == null || !data.canManageBlueprints() || section.id().startsWith("*")) {
            return;
        }
        showMenu(List.of(
                new MenuEntry(Component.translatable("gui.workorder.section.delete", section.name()).withStyle(ChatFormatting.RED),
                        () -> send(BlueprintCatalogMessage.ofSection(BlueprintCatalogMessage.DELETE_SECTION, section.id()))),
                new MenuEntry(Component.translatable("gui.cancel"), () -> {
                })
        ), mouseX, mouseY);
    }

    private static void send(BlueprintCatalogMessage message) {
        Network.sendToServer(message);
    }
}
