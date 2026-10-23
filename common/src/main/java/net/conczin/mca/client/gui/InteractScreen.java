package net.conczin.mca.client.gui;

import net.conczin.mca.expansion.speech.SpeechBubbles;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.QueuedAction;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.resources.data.Analysis;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class InteractScreen extends AbstractDynamicScreen {
    public static final Identifier ICON_TEXTURES = MCA.locate("textures/gui.png");
    private static Analysis analysis;
    private final VillagerLike<?> villager;
    private final Player player = Objects.requireNonNull(Minecraft.getInstance().player);
    private boolean inGiftMode;
    private int timeSinceLastClick;
    private String father;
    private String mother;
    private RelationshipState marriageState;
    private Component spouse;
    private List<String> dialogAnswers;
    private String dialogAnswerHover;
    private List<FormattedCharSequence> dialogQuestionText;
    private String dialogQuestionId;

    /** expansion: speech - the villager talked to, so its bubble can step aside for this screen */
    public java.util.UUID getVillagerUUID() {
        return villager.asEntity().getUUID();
    }

    public InteractScreen(VillagerLike<?> villager) {
        super(Component.literal("Interact"));
        this.villager = villager;
    }

    public static void setAnalysis(Analysis analysis) {
        InteractScreen.analysis = analysis;
    }

    public void setParents(String father, String mother) {
        this.father = father;
        this.mother = mother;
    }

    public void setSpouse(RelationshipState marriageState, String spouse) {
        this.marriageState = marriageState;
        this.spouse = spouse == null ? Component.translatable("gui.interact.label.parentUnknown") : Component.literal(spouse);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Objects.requireNonNull(this.minecraft).gui.setScreen(null);
        Network.sendToServer(new InteractionCloseRequest(villager.asEntity().getUUID()));
    }

    @Override
    public void init() {
        Network.sendToServer(new GetInteractDataRequest(villager.asEntity().getId()));
    }

    @Override
    public void tick() {
        timeSinceLastClick++;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Nop
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float tickDelta) {
        super.extractRenderState(context, mouseX, mouseY, tickDelta);

        drawIcons(context);
        drawTextPopups(context);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (dy < 0) {
            player.getInventory().setSelectedSlot(selectedSlot == 8 ? 0 : selectedSlot + 1);
        } else if (dy > 0) {
            player.getInventory().setSelectedSlot(selectedSlot == 0 ? 8 : selectedSlot - 1);
        }

        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Clicking one of your own orders in the queue cancels it.
        for (QueueCell cell : queueCells) {
            if (cell.entry() != null && isOwnOrder(cell.entry())
                && event.x() >= cell.x() && event.x() < cell.x() + cell.size() && event.y() >= cell.y() && event.y() < cell.y() + cell.size()) {
                Network.sendToServer(new QueueActionMessage(villager.asEntity().getUUID(), cell.entry().type().id(), true));
                return true;
            }
        }
        super.mouseClicked(event, doubleClick);

        // Dialog
        if (event.button() == 0 && dialogAnswerHover != null && dialogQuestionText != null) {
            //todo double click (Likely fixable via using a different event -- 7.4.0)
            Network.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUUID(), dialogQuestionId, dialogAnswerHover));
        }

        // Right mouse button
        if (inGiftMode && event.button() == 1) {
            Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID()));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Hotkey to leave gift mode
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else {
                onClose();
            }
            return true;
        }
        // Keyboard path so gifts can be given without a mouse/right mouse button. The
        // cooldown keeps key auto-repeat from handing over a whole stack.
        if (inGiftMode && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            if (timeSinceLastClick > 2) {
                timeSinceLastClick = 0;
                Network.sendToServer(new InteractionVillagerMessage("gift", villager.asEntity().getUUID()));
            }
            return true;
        }
        return false;
    }

    private void drawIcons(GuiGraphicsExtractor context) {
        final Matrix3x2fStack matrices = context.pose();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        matrices.pushMatrix();
        matrices.scale(iconScale, iconScale);

        if (marriageState != null) {
            drawIcon(context, ICON_TEXTURES, marriageState.getIcon());
        }

        drawIcon(context, ICON_TEXTURES, memory.getHearts() < 0 ? "blackHeart" : memory.getHearts() >= 100 ? "goldHeart" : "redHeart");
        // drawIcon(transform, "neutralEmerald");
        drawIcon(context, ICON_TEXTURES, "genes");

        if (canDrawParentsIcon()) {
            drawIcon(context, ICON_TEXTURES, "parents");
        }
        if (canDrawGiftIcon()) {
            drawIcon(context, ICON_TEXTURES, "gift");
        }

        if (analysis != null) {
            drawIcon(context, ICON_TEXTURES, "analysis");
        }

        matrices.popMatrix();
    }

    private void drawTextPopups(GuiGraphicsExtractor context) {
        //name or state tip (gifting, ...)
        int h = 17;
        if (inGiftMode) {
            drawTooltip(context, Component.translatable("gui.interact.label.giveGift"), 10, 28);
        } else {
            drawTooltip(context, villager.asEntity().getName(), 10, 28);
        }

        //age or profession
        drawTooltip(context, villager.asEntity().isBaby() ? villager.getAgeState().getName()
                : villager instanceof VillagerEntityMCA mca ? Skills.describe(mca, villager.getProfessionText()) // expansion: minds
                : villager.getProfessionText(), 10, 30 + h);

        VillagerBrain<?> brain = villager.getVillagerBrain();

        //mood
        drawTooltip(context,
                Component.translatable("gui.interact.label.mood", brain.getMood().getText())
                        .withStyle(brain.getMood().getColor()), 10, 30 + h * 2);

        //personality
        if (hoveringOverText(10, 30 + h * 3, 128)) {
            drawTooltip(context, brain.getPersonality().getDescription(), 10, 30 + h * 3);
        } else {
            //White as we don't know if a personality is negative
            drawTooltip(context, Component.translatable("gui.interact.label.personality", brain.getPersonality().getName()).withStyle(ChatFormatting.WHITE), 10, 30 + h * 3);
        }

        //traits
        Set<Traits.Trait> traits = villager.getTraits().getTraits();
        if (!traits.isEmpty()) {
            if (hoveringOverText(10, 30 + h * 4, 128)) {
                //details
                List<Component> traitText = traits.stream().map(Traits.Trait::getDescription).collect(Collectors.toList());
                traitText.addFirst(Component.translatable("traits.title"));
                drawTooltip(context, traitText, 10, 30 + h * 4);
            } else {
                //list
                MutableComponent traitText = Component.translatable("traits.title");
                traits.stream().map(Traits.Trait::getName).forEach(t -> {
                    if (!traitText.getSiblings().isEmpty()) {
                        traitText.append(Component.literal(", "));
                    }
                    traitText.append(t);
                });
                drawTooltip(context, traitText, 10, 30 + h * 4);
            }
        }

        //action queue
        queueCells.clear();
        if (villager instanceof VillagerEntityMCA mca && ActionQueue.isEnabled()) {
            drawQueue(context, mca.getActionQueue());
        }

        //needs
        if (villager instanceof VillagerEntityMCA mca && Needs.isEnabled() && mca.getNeeds().hasNeeds()) {
            // Bottom left, below MCA's own icons.
            drawNeeds(context, mca.getNeeds(), 10, height - 28 - 10);
        }

        //hearts
        if (hoveringOverIcon("redHeart")) {
            int hearts = brain.getMemoriesForPlayer(player).getHearts();
            drawHoveringIconText(context, Component.literal(hearts + " hearts"), "redHeart");
        }

        //marriage status
        if (marriageState != null && hoveringOverIcon("married") && villager instanceof CompassionateEntity<?>) {
            String ms = marriageState.base().getIcon().toLowerCase(Locale.ENGLISH);
            drawHoveringIconText(context, Component.translatable("gui.interact.label." + ms, spouse), "married");
        }

        //parents
        if (canDrawParentsIcon() && hoveringOverIcon("parents")) {
            drawHoveringIconText(context, Component.translatable("gui.interact.label.parents",
                    father == null ? Component.translatable("gui.interact.label.parentUnknown") : father,
                    mother == null ? Component.translatable("gui.interact.label.parentUnknown") : mother
            ), "parents");
        }

        //gift
        if (canDrawGiftIcon() && hoveringOverIcon("gift")) {
            drawHoveringIconText(context, Component.translatable("gui.interact.label.gift"), "gift");
        }

        //genes
        if (hoveringOverIcon("genes")) {
            List<Component> lines = new LinkedList<>();
            lines.add(Component.literal("Genes"));

            for (Genetics.Gene gene : villager.getGenetics()) {
                String key = gene.getType().getTranslationKey();
                int value = (int) (gene.get() * 100);
                lines.add(Component.translatable("gene.tooltip", Component.translatable(key), value));
            }

            drawHoveringIconText(context, lines, "genes");
        }

        //analysis
        if (hoveringOverIcon("analysis") && analysis != null) {
            List<Component> lines = new LinkedList<>();
            lines.add(Component.translatable("analysis.title").withStyle(ChatFormatting.GRAY));

            //summands
            for (Analysis.AnalysisElement d : analysis) {
                lines.add(Component.translatable("analysis." + d.key())
                        .append(Component.literal(": " + (d.positive() ? "+" : "") + d.value()))
                        .withStyle(d.positive() ? ChatFormatting.GREEN : ChatFormatting.RED));
            }

            //total
            String chance = analysis.getTotalAsString();
            lines.add(Component.translatable("analysis.total").append(": " + chance));

            drawHoveringIconText(context, lines, "analysis");
        }

        //dialogue, radial: the text sits above the ring of answers
        if (dialogQuestionText != null && useRadialLayout()) {
            int bottom = height / 2 - radialRadius - 22;
            int top = bottom - 10 * dialogQuestionText.size() - 6;
            context.fill(width / 2 - 88, top, width / 2 + 88, bottom, 0x99000000);
            int lineY = top + 4;
            for (FormattedCharSequence t : SpeechBubbles.typed(getVillagerUUID(), dialogQuestionText)) { // expansion: speech
                context.text(font, t, width / 2 - font.width(t) / 2, lineY, 0xFFFFFFFF);
                lineY += 10;
            }
            dialogAnswerHover = null;
        } else if (dialogQuestionText != null && dialogAnswers != null) {
            //background
            context.fill(width / 2 - 85, height / 2 - 50 - 10 * dialogQuestionText.size(), width / 2 + 85,
                    height / 2 - 30 + 10 * dialogAnswers.size(), 0x77000000);

            //question
            int i = -dialogQuestionText.size();
            for (FormattedCharSequence t : SpeechBubbles.typed(getVillagerUUID(), dialogQuestionText)) { // expansion: speech
                i++;
                context.text(font, t, width / 2 - font.width(t) / 2, height / 2 - 50 + i * 10, 0xFFFFFFFF);
            }
            dialogAnswerHover = null;

            //separator
            context.horizontalLine(width / 2 - 75, width / 2 + 75, height / 2 - 40, 0xAAFFFFFF);

            //answers
            int y = height / 2 - 35;
            for (String a : dialogAnswers) {
                boolean hover = hoveringOver(width / 2 - 100, y - 3, 200, 10);
                context.centeredText(font, Component.translatable(Question.getTranslationKey(dialogQuestionId, a)), width / 2, y, hover ? 0xFFD7D784 : 0xAAFFFFFF);
                if (hover) {
                    dialogAnswerHover = a;
                }
                y += 10;
            }
        }
    }

    private record QueueCell(int x, int y, int size, ActionQueue.Entry entry, ActionQueue.Upcoming upcoming) {
    }

    private final List<QueueCell> queueCells = new ArrayList<>();

    private boolean isOwnOrder(ActionQueue.Entry entry) {
        return entry.source() == QueuedAction.Source.PLAYER && player.getUUID().equals(entry.player());
    }

    private static int urgencyColor(float score) {
        return score >= 0.7F ? 0xFFDD4444 : score >= 0.4F ? 0xFFDDCC33 : 0xFF55CC55;
    }

    /**
     * The villager's action queue, The Sims style, at the top center: the current action large and
     * framed, then what is queued with an urgency bar each, then the coming schedule changes.
     */
    private void drawQueue(GuiGraphicsExtractor context, ActionQueue queue) {
        List<ActionQueue.Entry> entries = queue.entries();
        List<ActionQueue.Upcoming> upcoming = queue.upcoming();
        if (entries.isEmpty()) {
            return;
        }
        int big = 26;
        int small = 20;
        int gap = 4;
        int total = big + (entries.size() - 1) * (small + gap) + upcoming.size() * (small + gap) + (upcoming.isEmpty() ? 0 : 6);
        int x = width / 2 - total / 2;
        int top = 6;
        context.fill(x - 4, top - 3, x + total + 4, top + big + 14, 0x77000000);

        final Matrix3x2fStack matrices = context.pose();
        for (int i = 0; i < entries.size(); i++) {
            ActionQueue.Entry entry = entries.get(i);
            int size = i == 0 ? big : small;
            int y = top + (big - size) / 2;
            context.fill(x, y, x + size, y + size, 0xAA000000);
            if (i == 0) {
                context.outline(x, y, size, size, 0xFFFFDD55);
            } else if (isOwnOrder(entry)) {
                context.outline(x, y, size, size, 0xFF88DDFF);
            }
            context.item(entry.type().icon(), x + (size - 16) / 2, y + (size - 16) / 2);
            int bar = Math.round(size * Math.min(1.0F, entry.score()));
            context.fill(x, y + size + 1, x + size, y + size + 3, 0xFF333333);
            context.fill(x, y + size + 1, x + bar, y + size + 3, urgencyColor(entry.score()));
            if (i == 0) {
                matrices.pushMatrix();
                matrices.translate(x + size / 2.0F, y + size + 5);
                matrices.scale(0.6F, 0.6F);
                context.centeredText(font, Component.translatable(entry.type().translationKey()), 0, 0, 0xFFFFDD55);
                matrices.popMatrix();
            }
            queueCells.add(new QueueCell(x, y, size, entry, null));
            x += size + gap;
        }

        if (!upcoming.isEmpty()) {
            context.verticalLine(x, top, top + big, 0x88FFFFFF);
            x += 6;
        }
        for (ActionQueue.Upcoming change : upcoming) {
            int y = top + (big - small) / 2;
            context.fill(x, y, x + small, y + small, 0x66000000);
            context.item(change.type().icon(), x + 2, y + 2);
            context.fill(x, y, x + small, y + small, 0x66000000);
            matrices.pushMatrix();
            matrices.translate(x + small / 2.0F, y + small + 2);
            matrices.scale(0.6F, 0.6F);
            context.centeredText(font, Component.literal(String.format(Locale.ROOT, "%02dh", (change.slot() + 6) % 24)), 0, 0, 0xFFCCCCCC);
            matrices.popMatrix();
            queueCells.add(new QueueCell(x, y, small, null, change));
            x += small + gap;
        }

        for (QueueCell cell : queueCells) {
            if (hoveringOver(cell.x(), cell.y(), cell.size(), cell.size())) {
                drawTooltip(context, queueTooltip(cell), cell.x(), cell.y() + cell.size() + 22);
            }
        }
    }

    private List<Component> queueTooltip(QueueCell cell) {
        List<Component> lines = new ArrayList<>();
        if (cell.upcoming() != null) {
            lines.add(Component.translatable(cell.upcoming().type().translationKey()));
            lines.add(Component.translatable("queue.reason.upcoming", String.format(Locale.ROOT, "%02d:00", (cell.upcoming().slot() + 6) % 24))
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        ActionQueue.Entry entry = cell.entry();
        lines.add(Component.translatable(entry.type().translationKey()).withColor(urgencyColor(entry.score()) & 0xFFFFFF));
        Component reason = switch (entry.source()) {
            case SCHEDULE -> Component.translatable("queue.reason.schedule");
            case NEED -> Component.translatable("queue.reason.need");
            case PLAYER -> Component.translatable("queue.reason.player",
                    entry.player() != null && minecraft != null && minecraft.level != null && minecraft.level.getPlayerByUUID(entry.player()) != null
                            ? minecraft.level.getPlayerByUUID(entry.player()).getName()
                            : Component.translatable("property.someone"));
        };
        lines.add(reason.copy().withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("queue.urgency", Math.round(entry.score() * 100)).withStyle(ChatFormatting.DARK_GRAY));
        if (isOwnOrder(entry)) {
            lines.add(Component.translatable("queue.cancel").withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    /** The vanilla item standing for each need. */
    private static ItemStack needIcon(Needs.Need need) {
        return new ItemStack(switch (need) {
            case HUNGER -> Items.BREAD;
            case THIRST -> Items.WATER_BUCKET;
            case ENERGY -> Items.BED.red();
            case SOCIAL -> Items.BELL;
            case FUN -> Items.MUSIC_DISC_CAT;
            case SAFETY -> Items.SHIELD;
        });
    }

    private static int needColor(float value) {
        return value > 60.0F ? 0xFF55CC55 : value > 30.0F ? 0xFFDDCC33 : 0xFFDD4444;
    }

    /**
     * A row of item icons, one per need, with the value below each. Hovering an icon shows its
     * name, how to satisfy it and the thought it currently causes.
     */
    private void drawNeeds(GuiGraphicsExtractor context, Needs needs, int x, int y) {
        Needs.Need[] all = Needs.Need.values();
        int cell = 22;
        int height = 28;
        context.fill(x, y, x + all.length * cell + 2, y + height, 0x88000000);

        final Matrix3x2fStack matrices = context.pose();
        Needs.Need hovered = null;
        for (int i = 0; i < all.length; i++) {
            Needs.Need need = all[i];
            float value = needs.get(need);
            int cellX = x + 1 + i * cell;
            context.item(needIcon(need), cellX + 3, y + 2);

            String percent = Math.round(value) + "%";
            matrices.pushMatrix();
            matrices.translate(cellX + cell / 2.0F, y + 19);
            matrices.scale(0.75F, 0.75F);
            context.centeredText(font, Component.literal(percent), 0, 0, needColor(value));
            matrices.popMatrix();

            if (hoveringOver(cellX, y, cell, height)) {
                hovered = need;
            }
        }

        if (hovered != null) {
            float value = needs.get(hovered);
            List<Component> lines = new LinkedList<>();
            lines.add(Component.translatable("needs." + hovered.id()).append(": " + Math.round(value) + "%").withColor(needColor(value) & 0xFFFFFF));
            for (String line : Component.translatable("needs." + hovered.id() + ".description").getString().split("\n")) {
                lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
            for (Needs.Thought thought : needs.thoughts()) {
                if (thought.need() == hovered) {
                    lines.add(Component.translatable(thought.key()).append(" " + (thought.mood() > 0 ? "+" : "") + thought.mood())
                            .withStyle(thought.mood() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
            }
            drawTooltip(context, lines, x + all.length * cell + 2, y);
        }
    }

    //checks if the mouse hovers over a tooltip
    //tooltips are not rendered on the given coordinates, so we need an offset
    private boolean hoveringOverText(int x, int y, int w) {
        return hoveringOver(x + 8, y - 16, w, 16);
    }

    private boolean canDrawParentsIcon() {
        return father != null || mother != null;
    }

    private boolean canDrawGiftIcon() {
        return false;//villager.getVillagerBrain().getMemoriesForPlayer(player).isGiftPresent();
    }

    public void setDialogue(String dialogue, List<String> answers) {
        dialogQuestionId = dialogue;
        dialogAnswers = answers;
        if (useRadialLayout()) {
            // The answers become the ring; the arrow in the middle leaves the conversation.
            clearWidgets();
            List<RadialEntry> ring = new ArrayList<>();
            for (String answer : answers) {
                ring.add(new RadialEntry(Component.translatable(Question.getTranslationKey(dialogue, answer)), RadialIcons.dialogueIcon(answer), true,
                        () -> Network.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUUID(), dialogQuestionId, answer)), null));
            }
            addRadialRing(ring, new RadialEntry(Component.translatable("gui.button.backarrow"), RadialIcons.iconFor("gui.button.backarrow"), true,
                    this::leaveDialogue, null));
        }
    }

    private void leaveDialogue() {
        dialogQuestionText = null;
        dialogAnswers = null;
        dialogQuestionId = null;
        setLayout("main");
    }

    public void setLastPhrase(Component questionText, boolean silent) {
        Component text;
        if (!silent) {
            text = villager.sendChatMessage(questionText, player);
        } else {
            text = villager.transformMessage(questionText);
        }
        dialogQuestionText = font.split(text, 160);
    }

    @Override
    protected boolean isButtonHidden(MCAButton button) {
        return button.identifier().equals("gui.button.orders") && !ActionQueue.isEnabled();
    }

    @Override
    protected boolean useRadialLayout() {
        return Config.getInstance().radialInteractMenu;
    }

    /** The radial menu has its own main and interact layouts (see assets/mca/api/gui/radial_*). */
    @Override
    public void setLayout(String guiKey) {
        if (useRadialLayout() && (guiKey.equals("main") || guiKey.equals("interact"))) {
            guiKey = "radial_" + guiKey;
        }
        super.setLayout(guiKey);
    }

    @Override
    protected void buttonPressed(MCAButton button) {
        String id = button.identifier();

        if (timeSinceLastClick <= 2) {
            return; /* Prevents click-through on Mojang's button system */
        }
        timeSinceLastClick = 0;

        /* Progression to different GUIs */
        if (id.equals("gui.button.interact")) {
            setLayout("interact");
        } else if (id.equals("gui.button.command")) {
            setLayout("command");
            disableButton("gui.button." + villager.getVillagerBrain().getMoveState().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.clothing")) {
            setLayout("clothing");
        } else if (id.equals("gui.button.familyTree")) {
            Minecraft.getInstance().gui.setScreen(new FamilyTreeScreen(villager.asEntity().getUUID()));
        } else if (id.equals("gui.button.talk")) {
            clearWidgets();
            Network.sendToServer(new InteractionDialogueInitMessage(villager.asEntity().getUUID()));
        } else if (id.equals("gui.button.work")) {
            setLayout("work");
            disableButton("gui.button." + villager.getVillagerBrain().getCurrentJob().name().toLowerCase(Locale.ENGLISH));
        } else if (id.equals("gui.button.professions")) {
            setLayout("professions");
        } else if (id.equals("gui.button.commands")) {
            setLayout("radial_commands");
        } else if (id.equals("gui.button.orders")) {
            setLayout("radial_orders");
        } else if (id.startsWith("gui.button.order.")) {
            Network.sendToServer(new QueueActionMessage(villager.asEntity().getUUID(), id.substring("gui.button.order.".length()), false));
        } else if (id.equals("gui.button.backarrow")) {
            if (useRadialLayout() && !inGiftMode) {
                // Submenus go back to Interact, Interact back to the main ring.
                setLayout(getActiveScreen().equals("radial_interact") ? "main" : "interact");
            } else if (inGiftMode) {
                inGiftMode = false;
                setLayout("interact");
            } else if (getActiveScreen().equals("locations")) {
                setLayout("interact");
            } else {
                setLayout("main");
            }
        } else if (id.equals("gui.button.locations")) {
            setLayout("locations");
        } else if (button.notifyServer()) {
            /* Anything that should notify the server is handled here */

            if (!button.targetServer()) {
                Network.sendToServer(new InteractionVillagerMessage(id.replace("gui.button.", ""), villager.asEntity().getUUID()));
            }
        } else if (id.equals("gui.button.gift")) {
            this.inGiftMode = true;
            disableAllButtons();
        }
    }
}



