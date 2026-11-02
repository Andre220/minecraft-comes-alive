package net.conczin.mca.client.gui.workorder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The block catalog of the build tool, like the creative inventory: tabs along the top (what the village has, recently
 * used, then the game's own creative tabs), a grid of blocks and a search box. Any block can be planned; outside the
 * Villager Editor and creative the builders will look for it in the village storage.
 */
final class BlockCatalog {
    static final int COLUMNS = 9;
    static final int SLOT = 18;
    static final int WIDTH = COLUMNS * SLOT + 8;
    private static final int TAB = 20;
    private static final int TAB_GAP = 2;
    private static final int SEARCH_HEIGHT = 16;
    private static final int MAX_RECENT = COLUMNS * 2;
    private static final int SELECTED_COLOR = 0xFFFFD24A;
    /** Recently picked blocks, kept while the game runs. */
    private static final List<Block> RECENT = new ArrayList<>();

    private record Tab(ItemStack icon, Component name, Supplier<List<ItemStack>> contents) {
    }

    private final Consumer<Block> onPick;
    private final List<Tab> tabs = new ArrayList<>();
    private int tab;
    private int scroll;
    private int x;
    private int y;
    private int height;
    private @Nullable EditBox search;
    private List<ItemStack> shown = List.of();
    private String shownFor = "";
    private int shownTab = -1;

    BlockCatalog(Consumer<Block> onPick, Supplier<List<Block>> villageBlocks) {
        this.onPick = onPick;
        tabs.add(new Tab(new ItemStack(Items.CHEST), Component.translatable("gui.workorder.catalog.village"),
                () -> stacksOf(villageBlocks.get())));
        tabs.add(new Tab(new ItemStack(Items.CLOCK), Component.translatable("gui.workorder.catalog.recent"),
                () -> stacksOf(RECENT)));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            CreativeModeTabs.tryRebuildTabContents(minecraft.player.connection.enabledFeatures(), false, minecraft.level.registryAccess());
        }
        for (CreativeModeTab creative : CreativeModeTabs.tabs()) {
            if (creative.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            List<ItemStack> blocks = creative.getDisplayItems().stream().filter(stack -> stack.getItem() instanceof BlockItem).toList();
            if (!blocks.isEmpty()) {
                tabs.add(new Tab(creative.getIconItem(), creative.getDisplayName(), () -> blocks));
            }
        }
    }

    static void noteUsed(Block block) {
        RECENT.remove(block);
        RECENT.addFirst(block);
        while (RECENT.size() > MAX_RECENT) {
            RECENT.removeLast();
        }
    }

    private static List<ItemStack> stacksOf(List<Block> blocks) {
        return blocks.stream().filter(block -> block.asItem() != Items.AIR).map(block -> new ItemStack(block.asItem())).toList();
    }

    /** Places the panel; {@code height} is all the room it has, from the tabs down. */
    void setBounds(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    /** The search box, to be added as a widget of the screen. */
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

    private int tabsPerRow() {
        return Math.max(1, (WIDTH - 4 + TAB_GAP) / (TAB + TAB_GAP));
    }

    private int tabRows() {
        return (tabs.size() + tabsPerRow() - 1) / tabsPerRow();
    }

    private int searchTop() {
        return y + 4 + tabRows() * (TAB + TAB_GAP) + 2;
    }

    private int gridTop() {
        return searchTop() + SEARCH_HEIGHT + 4;
    }

    private int visibleRows() {
        return Math.max(1, (y + height - 4 - gridTop()) / SLOT);
    }

    boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
    }

    private List<ItemStack> contents() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (shownTab == tab && shownFor.equals(query) && tab != 0 && tab != 1) {
            return shown;
        }
        shownTab = tab;
        shownFor = query;
        if (query.isEmpty()) {
            shown = tabs.get(Math.min(tab, tabs.size() - 1)).contents().get();
        } else {
            // the search looks through every block, not just the open tab
            Set<ItemStack> found = new LinkedHashSet<>();
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item instanceof BlockItem && item.getName(new ItemStack(item)).getString().toLowerCase(Locale.ROOT).contains(query)) {
                    found.add(new ItemStack(item));
                }
            });
            shown = List.copyOf(found);
        }
        return shown;
    }

    /**
     * @param stored how many of an item the village has, or -1 when it does not matter (free planning)
     */
    void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, @Nullable Block selected, ToIntFunction<ItemStack> stored) {
        graphics.fill(x, y, x + WIDTH, y + height, 0xB0000000);
        graphics.outline(x, y, WIDTH, height, 0xFF505050);

        int perRow = tabsPerRow();
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = x + 4 + (i % perRow) * (TAB + TAB_GAP);
            int tabY = y + 4 + (i / perRow) * (TAB + TAB_GAP);
            boolean active = i == tab && (search == null || search.getValue().isBlank());
            boolean hovered = mouseX >= tabX && mouseX < tabX + TAB && mouseY >= tabY && mouseY < tabY + TAB;
            graphics.fill(tabX, tabY, tabX + TAB, tabY + TAB, active ? 0xFF6A5A2A : hovered ? 0xFF4A4A4A : 0xFF2A2A2A);
            if (active) {
                graphics.outline(tabX, tabY, TAB, TAB, SELECTED_COLOR);
            }
            graphics.item(tabs.get(i).icon(), tabX + 2, tabY + 2);
            if (hovered) {
                graphics.setTooltipForNextFrame(font, tabs.get(i).name(), mouseX, mouseY);
            }
        }

        List<ItemStack> items = contents();
        int rows = visibleRows();
        int maxScroll = Math.max(0, (items.size() + COLUMNS - 1) / COLUMNS - rows);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int top = gridTop();
        if (items.isEmpty()) {
            graphics.text(font, Component.translatable("gui.workorder.catalog.empty"), x + 6, top + 4, 0xFFA0A0A0);
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (row + scroll) * COLUMNS + column;
                if (index >= items.size()) {
                    break;
                }
                ItemStack stack = items.get(index);
                int slotX = x + 4 + column * SLOT;
                int slotY = top + row * SLOT;
                boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= slotY && mouseY < slotY + SLOT;
                graphics.fill(slotX, slotY, slotX + SLOT - 1, slotY + SLOT - 1, hovered ? 0x60FFFFFF : 0x40FFFFFF);
                graphics.item(stack, slotX + 1, slotY + 1);
                int count = stored.applyAsInt(stack);
                if (count >= 0) {
                    String label = count > 999 ? "999+" : Integer.toString(count);
                    graphics.itemDecorations(font, stack, slotX + 1, slotY + 1, count == 0 ? "§c0" : label);
                }
                if (selected != null && stack.getItem() instanceof BlockItem item && item.getBlock() == selected) {
                    graphics.outline(slotX - 1, slotY - 1, SLOT + 1, SLOT + 1, SELECTED_COLOR);
                }
                if (hovered) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(stack.getHoverName());
                    if (count >= 0) {
                        lines.add(Component.translatable("gui.workorder.storage_count", count)
                                .withStyle(count > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
                    }
                    graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                }
            }
        }
        if (maxScroll > 0) {
            // a thin scroll bar along the right edge
            int barTop = top;
            int barHeight = rows * SLOT;
            int knob = Math.max(8, barHeight * rows / (maxScroll + rows));
            int knobY = barTop + (barHeight - knob) * scroll / maxScroll;
            graphics.fill(x + WIDTH - 3, barTop, x + WIDTH - 1, barTop + barHeight, 0xFF303030);
            graphics.fill(x + WIDTH - 3, knobY, x + WIDTH - 1, knobY + knob, 0xFFA0A0A0);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY)) {
            return false;
        }
        int perRow = tabsPerRow();
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = x + 4 + (i % perRow) * (TAB + TAB_GAP);
            int tabY = y + 4 + (i / perRow) * (TAB + TAB_GAP);
            if (mouseX >= tabX && mouseX < tabX + TAB && mouseY >= tabY && mouseY < tabY + TAB) {
                tab = i;
                scroll = 0;
                if (search != null) {
                    search.setValue("");
                }
                shownTab = -1;
                return true;
            }
        }
        int top = gridTop();
        if (mouseY >= top && mouseX >= x + 4 && mouseX < x + 4 + COLUMNS * SLOT) {
            int index = ((int) (mouseY - top) / SLOT + scroll) * COLUMNS + (int) (mouseX - x - 4) / SLOT;
            List<ItemStack> items = contents();
            if ((int) (mouseY - top) / SLOT < visibleRows() && index >= 0 && index < items.size()
                && items.get(index).getItem() instanceof BlockItem item) {
                onPick.accept(item.getBlock());
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

    /** The village tab changes as storage reports come in. */
    void refresh() {
        if (tab <= 1) {
            shownTab = -1;
        }
    }
}
