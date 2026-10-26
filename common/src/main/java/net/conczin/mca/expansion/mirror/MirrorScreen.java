package net.conczin.mca.expansion.mirror;

import net.conczin.mca.client.gui.CombScreen;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/** Looking into a mirror: the comb's hair editor, for the player's own hair, and no comb wears out. */
public class MirrorScreen extends CombScreen {
    private MirrorScreen(UUID playerUUID) {
        super(playerUUID);
    }

    public static void open() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.setScreen(new MirrorScreen(client.player.getUUID()));
        }
    }

    @Override
    protected void eventCallback(String event) {
    }
}
