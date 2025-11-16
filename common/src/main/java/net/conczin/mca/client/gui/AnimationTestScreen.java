package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.TestAnimationMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen for testing villager animations in-game.
 * Shows a list of all available animations that can be triggered.
 */
public class AnimationTestScreen extends Screen {
    
    private final VillagerLike<?> villager;
    private final Screen parent;
    
    public AnimationTestScreen(VillagerLike<?> villager, Screen parent) {
        super(Component.translatable("gui.animationtest.title"));
        this.villager = villager;
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        int buttonWidth = 140;
        int buttonHeight = 18;
        int spacingX = 10;  // Space between columns
        int spacingY = 22;  // Space between rows
        int startY = 40;
        int centerX = this.width / 2;
        
        // Calculate column positions (2 columns)
        int leftX = centerX - buttonWidth - spacingX / 2;
        int rightX = centerX + spacingX / 2;
        
        int row = 0;
        
        // Left column - Movement animations
        addRenderableWidget(Button.builder(
                Component.literal("🚶 Caminhar"),
                b -> testAnimation("walk")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("🏃 Correr"),
                b -> testAnimation("run")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("🏊 Nadar"),
                b -> testAnimation("swim")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("😱 Pânico"),
                b -> testAnimation("panic")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        // Chores section
        row++; // Skip row for spacing
        addRenderableWidget(Button.builder(
                Component.literal("🪓 Cortar (CHOP)"),
                b -> testChore(Chore.CHOP)
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("⛏️ Minerar (PROSPECT)"),
                b -> testChore(Chore.PROSPECT)
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("🌾 Colher (HARVEST)"),
                b -> testChore(Chore.HARVEST)
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("⚔️ Caçar (HUNT)"),
                b -> testChore(Chore.HUNT)
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("🎣 Pescar (FISH)"),
                b -> testChore(Chore.FISH)
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        // Interactions section
        row++; // Skip row for spacing
        addRenderableWidget(Button.builder(
                Component.literal("🤝 Negociar"),
                b -> testAnimation("trade")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("😊 Reação +"),
                b -> testAnimation("positive")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("� Sim (Aceitar)"),
                b -> testAnimation("yes")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("👎 Não (Negar)"),
                b -> testAnimation("no")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("�😠 Reação -"),
                b -> testAnimation("negative")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("👀 Seguindo"),
                b -> testAnimation("follow")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        addRenderableWidget(Button.builder(
                Component.literal("🧍 Parado"),
                b -> testAnimation("stay")
        ).bounds(leftX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        
        addRenderableWidget(Button.builder(
                Component.literal("🦘 Pular"),
                b -> testAnimation("jump")
        ).bounds(rightX, startY + row * spacingY, buttonWidth, buttonHeight).build());
        row++;
        
        // Back button - centered, full width
        row++; // Extra spacing
        addRenderableWidget(Button.builder(
                Component.literal("⬅ Voltar"),
                b -> onClose()
        ).bounds(centerX - buttonWidth / 2, startY + row * spacingY, buttonWidth, buttonHeight).build());
    }
    
    private void testAnimation(String animationType) {
        Network.sendToServer(new TestAnimationMessage(villager.asEntity().getUUID(), animationType, null));
        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("▶ Testando animação: ").append(Component.literal(animationType).withStyle(s -> s.withBold(true)))
        );
    }
    
    private void testChore(Chore chore) {
        Network.sendToServer(new TestAnimationMessage(villager.asEntity().getUUID(), "chore", chore.name()));
        Minecraft.getInstance().player.sendSystemMessage(
                Component.literal("▶ Testando trabalho: ").append(Component.literal(chore.name()).withStyle(s -> s.withBold(true)))
        );
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Instructions
        guiGraphics.drawCenteredString(this.font, 
                Component.literal("Clique em uma animação para testá-la no villager").withStyle(s -> s.withItalic(true)),
                this.width / 2, this.height - 20, 0xAAAAAA);
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
