package net.conczin.mca.addon.roads.client;

import net.conczin.mca.network.Network;
import net.conczin.mca.api.addon.client.MapOverlay;
import net.conczin.mca.addon.roads.faction.FactionMapMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The bands' lands over the village map (expansion: factions): a dashed ring in the band's banner color around each
 * outpost the player has seen, and its banner on the outpost; an outpost off the map shows its banner at the map's
 * edge, toward it. Hovering a banner names the band and how it sees the player.
 */
public final class FactionMapOverlay implements MapOverlay {
    public static final FactionMapOverlay INSTANCE = new FactionMapOverlay();
    private static final int DASH = 4;
    private static FactionMapMessages.@Nullable Response data;

    private FactionMapOverlay() {
    }

    public static void receive(FactionMapMessages.Response response) {
        data = response;
    }

    @Override
    public void onOpen() {
        Network.sendToServer(new FactionMapMessages.Request());
    }

    /** Draws the lands and banners; returns the tooltip of the banner under the mouse, if any. */
    @Override
    public @Nullable List<Component> render(GuiGraphicsExtractor context, MapView viewport, int mouseX, int mouseY) {
        if (data == null || data.outposts().isEmpty()) {
            return null;
        }
        List<Component> tooltip = null;
        context.enableScissor(viewport.left() + 1, viewport.top() + 1, viewport.right() - 1, viewport.bottom() - 1);
        for (FactionMapMessages.Outpost outpost : data.outposts()) {
            double cx = viewport.screenX(outpost.pos().getX() + 0.5);
            double cy = viewport.screenY(outpost.pos().getZ() + 0.5);
            double radius = data.radius() * viewport.scale();
            ring(context, cx, cy, radius, 0xCC000000 | (outpost.rgb() & 0xFFFFFF));
        }
        context.disableScissor();
        for (FactionMapMessages.Outpost outpost : data.outposts()) {
            int x = (int) Math.round(viewport.screenX(outpost.pos().getX() + 0.5)) - 8;
            int y = (int) Math.round(viewport.screenY(outpost.pos().getZ() + 0.5)) - 8;
            boolean away = x < viewport.left() || x + 16 > viewport.right() || y < viewport.top() || y + 16 > viewport.bottom();
            x = Math.clamp(x, viewport.left() + 2, viewport.right() - 18);
            y = Math.clamp(y, viewport.top() + 2, viewport.bottom() - 18);
            context.item(banner(outpost.color()), x, y);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                tooltip = List.of(
                        Component.translatable("faction.name." + outpost.template(), outpost.place()),
                        outpost.standingValue().getName(),
                        Component.translatable(away ? "faction.map.away" : "faction.map.outpost",
                                outpost.pos().getX(), outpost.pos().getZ()).withStyle(ChatFormatting.GRAY));
            }
        }
        return tooltip;
    }

    /** A dashed circle, one pixel wide. */
    private static void ring(GuiGraphicsExtractor context, double cx, double cy, double radius, int color) {
        int steps = (int) Math.min(4000, Math.max(24, radius * 2 * Math.PI));
        for (int i = 0; i < steps; i++) {
            if (i / DASH % 2 == 1) {
                continue;
            }
            double angle = i * 2 * Math.PI / steps;
            int x = (int) Math.floor(cx + Math.cos(angle) * radius);
            int y = (int) Math.floor(cy + Math.sin(angle) * radius);
            context.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static ItemStack banner(String color) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(color + "_banner")));
    }
}
