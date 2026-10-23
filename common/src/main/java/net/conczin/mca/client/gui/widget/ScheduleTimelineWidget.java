package net.conczin.mca.client.gui.widget;

import net.conczin.mca.entity.ai.SchedulesMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.schedule.Activity;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A day of a villager's schedule as a strip of 24 hours (06:00 first) under a day/night sky, with
 * a marker at the current time. Clicking or dragging paints the hours with the selected activity.
 */
public class ScheduleTimelineWidget extends AbstractWidget {
    public static final int CELL_WIDTH = 7;
    public static final int WIDTH = CELL_WIDTH * SchedulesMCA.SLOTS;
    public static final int HEIGHT = 42;
    private static final int LABEL_HEIGHT = 8;
    private static final int SKY_HEIGHT = 4;
    private static final int CELL_HEIGHT = 20;
    private static final int CELLS_Y = LABEL_HEIGHT + SKY_HEIGHT;

    private final Supplier<String> schedule;
    private final Consumer<String> onChange;
    private final Supplier<Activity> brush;
    private final Function<Activity, @Nullable Component> warning;

    public ScheduleTimelineWidget(int x, int y, Supplier<String> schedule, Consumer<String> onChange,
                                  Supplier<Activity> brush, Function<Activity, @Nullable Component> warning) {
        super(x, y, WIDTH, HEIGHT, Component.translatable("gui.villager_editor.page.schedule"));
        this.schedule = schedule;
        this.onChange = onChange;
        this.brush = brush;
        this.warning = warning;
    }

    public static int color(Activity activity) {
        if (activity == Activity.WORK) {
            return 0xFFE08A2E;
        } else if (activity == Activity.MEET) {
            return 0xFFC64FB6;
        } else if (activity == Activity.REST) {
            return 0xFF3B4FA0;
        } else if (activity == Activity.PLAY) {
            return 0xFFE6D04A;
        }
        return 0xFF7A9A6A;
    }

    public static Component name(Activity activity) {
        return Component.translatable("gui.villager_editor.schedule.activity." + activity.getName().toLowerCase(Locale.ROOT));
    }

    /** Clock hour of a slot: slot 0 starts at 06:00. */
    public static int hourOf(int slot) {
        return (slot + 6) % 24;
    }

    private static int skyColor(int hour) {
        if (hour >= 6 && hour < 17) {
            return 0xFF8EC9F0;
        } else if (hour == 17) {
            return 0xFFE89A5A;
        } else if (hour == 18) {
            return 0xFF8A5A8A;
        } else if (hour == 5) {
            return 0xFFE8B070;
        } else if (hour == 4) {
            return 0xFF4A4A7A;
        }
        return 0xFF1A2244;
    }

    /** Position in the day as a fractional slot, from the overworld clock. */
    private static float currentSlot() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.0F;
        }
        long day = (long) SchedulesMCA.SLOTS * SchedulesMCA.SLOT_TICKS;
        return Math.floorMod(level.getOverworldClockTime(), day) / (float) SchedulesMCA.SLOT_TICKS;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        String letters = schedule.get();
        int x0 = getX();
        int y0 = getY();
        int hovered = isHovered() ? slotAt(mouseX) : -1;

        // Hour labels every three hours.
        Matrix3x2fStack matrices = context.pose();
        for (int slot = 0; slot < SchedulesMCA.SLOTS; slot += 3) {
            matrices.pushMatrix();
            matrices.translate(x0 + slot * CELL_WIDTH, y0);
            matrices.scale(0.6F, 0.6F);
            context.text(font, Component.literal(String.format(Locale.ROOT, "%02d", hourOf(slot))), 0, 0, 0xFFCCCCCC);
            matrices.popMatrix();
        }

        for (int slot = 0; slot < SchedulesMCA.SLOTS; slot++) {
            int x = x0 + slot * CELL_WIDTH;
            context.fill(x, y0 + LABEL_HEIGHT, x + CELL_WIDTH, y0 + CELLS_Y, skyColor(hourOf(slot)));

            Activity activity = SchedulesMCA.activityOf(letters.charAt(slot));
            int top = y0 + CELLS_Y;
            int bottom = top + CELL_HEIGHT;
            context.fill(x, top, x + CELL_WIDTH, bottom, color(activity));
            if (warning.apply(activity) != null) {
                // Hatched: the activity cannot run and the villager idles instead.
                for (int y = top + 1; y < bottom; y += 3) {
                    context.horizontalLine(x, x + CELL_WIDTH - 1, y, 0x99000000);
                }
            }
            if (slot == hovered) {
                context.fill(x, top, x + CELL_WIDTH, bottom, 0x55FFFFFF);
            }
            context.verticalLine(x + CELL_WIDTH - 1, top - 1, bottom, 0x66000000);
        }
        context.outline(x0, y0 + CELLS_Y, WIDTH, CELL_HEIGHT, 0xAAFFFFFF);

        // Current time, Majora's Mask style: a line through the strip and a pointer below it.
        int now = x0 + Math.round(currentSlot() * CELL_WIDTH);
        int below = y0 + CELLS_Y + CELL_HEIGHT;
        context.verticalLine(now, y0 + LABEL_HEIGHT - 1, below, 0xFFFFFFFF);
        for (int i = 0; i < 4; i++) {
            context.horizontalLine(now - i, now + i, below + 1 + i, 0xFFFFFFFF);
        }

        if (hovered >= 0) {
            Activity activity = SchedulesMCA.activityOf(letters.charAt(hovered));
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(String.format(Locale.ROOT, "%02d:00 - %02d:00  ", hourOf(hovered), hourOf(hovered + 1)))
                    .append(name(activity).copy().withColor(color(activity) & 0xFFFFFF)));
            Component problem = warning.apply(activity);
            if (problem != null) {
                tooltip.add(problem.copy().withStyle(ChatFormatting.RED));
            }
            context.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private int slotAt(double mouseX) {
        int slot = (int) Math.floor((mouseX - getX()) / CELL_WIDTH);
        return slot >= 0 && slot < SchedulesMCA.SLOTS ? slot : -1;
    }

    private void paint(double mouseX) {
        int slot = slotAt(mouseX);
        if (slot < 0) {
            return;
        }
        String letters = schedule.get();
        char letter = SchedulesMCA.letterOf(brush.get());
        if (letters.charAt(slot) != letter) {
            onChange.accept(letters.substring(0, slot) + letter + letters.substring(slot + 1));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        paint(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        paint(event.x());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        defaultButtonNarrationText(builder);
    }
}
