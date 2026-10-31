package net.conczin.mca.api.addon.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Something an addon draws over the village map (a band's land, a caravan's road). */
public interface MapOverlay {
    /** The map opened: time to ask the server for what to draw. */
    default void onOpen() {
    }

    /** Draws over the map; returns the tooltip for what is under the mouse, if anything. */
    @Nullable List<Component> render(GuiGraphicsExtractor context, MapView view, int mouseX, int mouseY);

    /** Where the map is on screen and how the world maps onto it. */
    interface MapView {
        double screenX(double worldX);

        double screenY(double worldZ);

        int left();

        int top();

        int right();

        int bottom();

        float scale();
    }

    List<MapOverlay> OVERLAYS = new ArrayList<>();

    static void register(MapOverlay overlay) {
        OVERLAYS.add(overlay);
    }

    static List<MapOverlay> all() {
        return Collections.unmodifiableList(OVERLAYS);
    }
}
