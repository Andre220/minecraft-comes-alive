package net.conczin.mca.client.gui.workorder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What a blueprint or the draft takes, laid out like an inventory: one slot per item, the amount needed in the corner,
 * green when the village storage has enough, red when it does not. The tooltip tells how many the storage holds.
 */
final class MaterialGrid {
    static final int COLUMNS = 8;
    static final int ROWS = 3;
    private static final int SLOT = 18;
    private static final int TITLE = 13;
    static final int WIDTH = COLUMNS * SLOT + 8;
    static final int HEIGHT = TITLE + ROWS * SLOT + 6;
    private static final int ENOUGH = 0x55FF55;
    private static final int LACKING = 0xFF5555;
    private static final int FREE = 0x55FFFF;

    private MaterialGrid() {
    }

    /**
     * @param stored how many of an item the village storage holds, or -1 when materials do not matter
     */
    static void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int mouseX, int mouseY,
                        Map<Item, Integer> needed, ToIntFunction<Item> stored) {
        graphics.fill(x, y, x + width, y + HEIGHT, 0xB0000000);
        graphics.outline(x, y, width, HEIGHT, 0xFF505050);
        List<Map.Entry<Item, Integer>> entries = needed.entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        int total = entries.stream().mapToInt(Map.Entry::getValue).sum();
        long lacking = entries.stream().filter(entry -> {
            int have = stored.applyAsInt(entry.getKey());
            return have >= 0 && have < entry.getValue();
        }).count();
        Component title = Component.translatable("gui.workorder.materials", total);
        if (lacking > 0) {
            title = title.copy().append(" ").append(Component.translatable("gui.workorder.materials.lacking", lacking).withStyle(ChatFormatting.RED));
        }
        graphics.text(font, title, x + 4, y + 3, 0xFFFFD27F);
        if (entries.isEmpty()) {
            graphics.text(font, Component.translatable("gui.workorder.materials.none"), x + 4, y + TITLE + 4, 0xFFA0A0A0);
            return;
        }

        int slots = COLUMNS * ROWS;
        boolean overflow = entries.size() > slots;
        int shown = overflow ? slots - 1 : entries.size();
        for (int i = 0; i < shown; i++) {
            Map.Entry<Item, Integer> entry = entries.get(i);
            int slotX = x + 4 + (i % COLUMNS) * SLOT;
            int slotY = y + TITLE + (i / COLUMNS) * SLOT;
            int have = stored.applyAsInt(entry.getKey());
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= slotY && mouseY < slotY + SLOT;
            graphics.fill(slotX, slotY, slotX + SLOT - 1, slotY + SLOT - 1, hovered ? 0x60FFFFFF : 0x40FFFFFF);
            ItemStack stack = new ItemStack(entry.getKey());
            graphics.item(stack, slotX + 1, slotY + 1);
            int color = have < 0 ? FREE : have >= entry.getValue() ? ENOUGH : LACKING;
            graphics.itemDecorations(font, stack, slotX + 1, slotY + 1, colored(compact(entry.getValue()), color));
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(stack.getHoverName());
                lines.add(Component.translatable("gui.workorder.materials.needed", entry.getValue()).withStyle(ChatFormatting.GRAY));
                lines.add(have < 0
                        ? Component.translatable("gui.workorder.materials.free").withStyle(ChatFormatting.AQUA)
                        : Component.translatable("gui.workorder.materials.stored", have)
                        .withStyle(have >= entry.getValue() ? ChatFormatting.GREEN : ChatFormatting.RED));
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
        if (overflow) {
            int slotX = x + 4 + (shown % COLUMNS) * SLOT;
            int slotY = y + TITLE + (shown / COLUMNS) * SLOT;
            graphics.fill(slotX, slotY, slotX + SLOT - 1, slotY + SLOT - 1, 0x40FFFFFF);
            graphics.centeredText(font, "+" + (entries.size() - shown), slotX + SLOT / 2, slotY + 5, 0xFFFFFFFF);
            if (mouseX >= slotX && mouseX < slotX + SLOT && mouseY >= slotY && mouseY < slotY + SLOT) {
                List<Component> lines = new ArrayList<>();
                for (Map.Entry<Item, Integer> entry : entries.subList(shown, entries.size())) {
                    lines.add(Component.literal(entry.getValue() + "× ").append(new ItemStack(entry.getKey()).getHoverName()));
                }
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }

    /** 152, 1.2k, 35k: fits the corner of a slot. */
    private static String compact(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        if (count < 10000) {
            return String.format(java.util.Locale.ROOT, "%.1fk", count / 1000.0);
        }
        return count / 1000 + "k";
    }

    /** The corner text with a color code, the way item counts take one. */
    private static String colored(String text, int rgb) {
        String code = rgb == ENOUGH ? "§a" : rgb == LACKING ? "§c" : "§b";
        return code + text;
    }
}
