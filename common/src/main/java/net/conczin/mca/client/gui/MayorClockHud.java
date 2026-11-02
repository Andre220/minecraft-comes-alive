package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The mayor's clock, after the clock of Majora's Mask: while it is held, a dial at the top of the screen shows the hour.
 * It has a band for each of the 24 hours, coloured from night blue through dawn to day and dusk, and turns under a
 * fixed pointer like the vanilla clock does, but it jumps from hour to hour. Below it: which day it is, the hour and the
 * part of the day. Handy to see what the villagers' routines should be doing right now.
 */
public final class MayorClockHud {
    private static final Identifier DIAL = MCA.locate("dynamic/mayor_clock_dial");
    private static final int TEXTURE = 128;
    /** Drawn size of the dial; only its upper half shows. */
    private static final int SIZE = 96;
    private static final int TOP = 4;
    private static final int HOURS = 24;
    private static final int TICKS_PER_HOUR = 1000;
    /** Tick 0 of the day is 06:00. */
    private static final int FIRST_HOUR = 6;
    private static final int POINTER_COLOR = 0xFFFFD24A;
    private static boolean dialMade;

    private MayorClockHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !isHeld(player)) {
            return;
        }
        makeDial();
        long time = minecraft.level.getOverworldClockTime();
        long dayTick = Math.floorMod(time, 24000L);
        int hour = (int) ((dayTick / TICKS_PER_HOUR + FIRST_HOUR) % HOURS);
        long day = Math.floorDiv(time, 24000L) + 1;

        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        int centerY = TOP + SIZE / 2;
        int half = SIZE / 2;

        // the dial, turned so the band of the current hour sits under the pointer; only its upper half is shown
        graphics.enableScissor(centerX - half - 2, TOP - 2, centerX + half + 2, centerY + 1);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.rotate(-(hour + 0.5F) * Mth.TWO_PI / HOURS);
        graphics.blit(RenderPipelines.GUI_TEXTURED, DIAL, -half, -half, 0.0F, 0.0F, SIZE, SIZE, TEXTURE, TEXTURE, TEXTURE, TEXTURE);
        for (int mark = 0; mark < HOURS; mark += 3) {
            float angle = (mark + 0.5F) * Mth.TWO_PI / HOURS;
            float radius = half - 11;
            String label = Integer.toString(mark);
            int labelX = Math.round(Mth.sin(angle) * radius) - font.width(label) / 2;
            int labelY = Math.round(-Mth.cos(angle) * radius) - 4;
            boolean dark = bandColor(mark) == bandColor(0);
            graphics.text(font, label, labelX, labelY, dark ? 0xFFE0E0E0 : 0xFF2A2A2A, false);
        }
        pose.popMatrix();
        graphics.disableScissor();

        // the fixed pointer over the top of the dial
        for (int row = 0; row < 5; row++) {
            graphics.fill(centerX - 4 + row, TOP - 3 + row, centerX + 5 - row, TOP - 2 + row, POINTER_COLOR);
        }

        Component line = Component.translatable("gui.mca.mayor_clock.line", day, String.format("%02d:00", hour),
                Component.translatable("gui.mca.mayor_clock.part." + partOfDay(hour)));
        graphics.centeredText(font, line, centerX, centerY + 4, 0xFFFFFFFF);
    }

    private static boolean isHeld(LocalPlayer player) {
        return player.getMainHandItem().is(ItemsMCA.MAYOR_CLOCK) || player.getOffhandItem().is(ItemsMCA.MAYOR_CLOCK);
    }

    static String partOfDay(int hour) {
        if (hour < 5) {
            return "late_night";
        } else if (hour < 7) {
            return "dawn";
        } else if (hour < 12) {
            return "morning";
        } else if (hour < 14) {
            return "noon";
        } else if (hour < 18) {
            return "afternoon";
        } else if (hour < 20) {
            return "dusk";
        }
        return "night";
    }

    /** The colour of an hour's band: night blue, warm dawn and dusk, bright day. */
    private static int bandColor(int hour) {
        return switch (partOfDay(hour)) {
            case "late_night", "night" -> 0x22305E;
            case "dawn" -> 0xE8A25C;
            case "dusk" -> 0xD9724A;
            case "noon" -> 0xFFE27A;
            default -> 0xF2CF68;
        };
    }

    /** Paints the dial once: 24 bands, every other one a little darker, a dark rim and hub. */
    private static void makeDial() {
        if (dialMade) {
            return;
        }
        dialMade = true;
        NativeImage image = new NativeImage(TEXTURE, TEXTURE, true);
        float center = TEXTURE / 2.0F;
        float outer = TEXTURE / 2.0F - 1;
        float rim = outer - 4;
        float hub = 14;
        for (int y = 0; y < TEXTURE; y++) {
            for (int x = 0; x < TEXTURE; x++) {
                float dx = x + 0.5F - center;
                float dy = y + 0.5F - center;
                float distance = Mth.sqrt(dx * dx + dy * dy);
                int color = 0;
                if (distance <= outer) {
                    if (distance > rim || distance < hub) {
                        color = 0xFF3A2A1A;
                    } else {
                        // clockwise from the top, like the hands of a clock
                        float angle = (float) Math.atan2(dx, -dy);
                        if (angle < 0) {
                            angle += Mth.TWO_PI;
                        }
                        int hour = Math.min(HOURS - 1, (int) (angle / Mth.TWO_PI * HOURS));
                        int rgb = bandColor(hour);
                        if (hour % 2 == 1) {
                            rgb = darker(rgb);
                        }
                        // a thin line between bands
                        float edge = angle / Mth.TWO_PI * HOURS - (int) (angle / Mth.TWO_PI * HOURS);
                        if (edge < 0.04F || edge > 0.96F) {
                            rgb = 0x3A2A1A;
                        }
                        color = 0xFF000000 | rgb;
                    }
                }
                image.setPixel(x, y, color);
            }
        }
        Minecraft.getInstance().getTextureManager().register(DIAL, new DynamicTexture(DIAL::toString, image));
    }

    private static int darker(int rgb) {
        int red = (rgb >> 16 & 0xFF) * 85 / 100;
        int green = (rgb >> 8 & 0xFF) * 85 / 100;
        int blue = (rgb & 0xFF) * 85 / 100;
        return red << 16 | green << 8 | blue;
    }
}
