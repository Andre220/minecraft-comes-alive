package net.conczin.mca.expansion.wardrobe;

import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/** "Wear this outfit?": the player's own look wearing the outfit chosen in a wardrobe or dresser, yes or no. */
public class OutfitChoiceScreen extends Screen {
    private static final int PREVIEW_SIZE = 60;
    /** Preview entities never enter the world: ids far into the negatives, clear of the editor's own previews. */
    private static final AtomicInteger PREVIEW_IDS = new AtomicInteger(-1_000_000);

    private final BlockPos pos;
    private final int slot;
    private final @Nullable VillagerEntityMCA preview;

    private OutfitChoiceScreen(BlockPos pos, int slot, String clothes) {
        super(Component.translatable("gui.mca.outfit.wear"));
        this.pos = pos;
        this.slot = slot;
        this.preview = previewWearing(clothes);
    }

    /** Opens the question for the outfit in that place, if the client knows of one there. */
    public static void open(BlockPos pos, int slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.getBlockEntity(pos) instanceof ClosetBlockEntity closet) {
            ItemStack stored = closet.getItem(slot);
            if (stored.getItem() instanceof OutfitItem) {
                client.gui.setScreen(new OutfitChoiceScreen(pos, slot, OutfitItem.clothes(stored)));
            }
        }
    }

    /** A copy of the player's MCA look (a plain villager without one), in the outfit. */
    private static @Nullable VillagerEntityMCA previewWearing(String clothes) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return null;
        }
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(client.level, EntitySpawnReason.LOAD);
        if (villager == null) {
            return null;
        }
        if (MCAClient.playerData.get(client.player.getUUID()) instanceof VillagerEntityMCA look) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, look.registryAccess());
            look.saveWithoutId(output);
            villager.load(TagValueInput.create(ProblemReporter.DISCARDING, look.registryAccess(), output.buildResult()));
        }
        villager.setId(PREVIEW_IDS.getAndDecrement()); // the renderer asks for it
        villager.setClothes(clothes);
        return villager;
    }

    @Override
    public void init() {
        addRenderableWidget(new ButtonWidget(width / 2 - 85, height / 2 + 70, 80, 20, Component.translatable("gui.yes"), b -> {
            Network.sendToServer(new OutfitMessages.Wear(pos, slot));
            onClose();
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 5, height / 2 + 70, 80, 20, Component.translatable("gui.no"), b -> onClose()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, height / 2 - 95, 0xFFFFFFFF);
        if (preview != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, width / 2 - 50, height / 2 - 80, width / 2 + 50, height / 2 + 62,
                    PREVIEW_SIZE, 0.0625F, mouseX, mouseY, preview);
        }
    }
}
