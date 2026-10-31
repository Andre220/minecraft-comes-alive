package net.conczin.mca.client.gui;

import net.conczin.mca.expansion.speech.SpeechBubbles;
import net.conczin.mca.expansion.minds.Skills;
import net.conczin.mca.expansion.minds.AttributeMessages;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.expansion.talk.TalkPanels;
import net.conczin.mca.client.gui.widget.ScheduleTimelineWidget;
import net.conczin.mca.entity.ai.queue.ActionQueue;
import net.conczin.mca.entity.ai.queue.QueuedAction;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.Opinions;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.*;
import net.conczin.mca.resources.data.Analysis;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;

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
        // Clicking an attribute spends a point on it (expansion: minds).
        if (event.button() == 0 && villager instanceof VillagerEntityMCA mca && VillagerAttributes.points(mca) > 0) {
            for (AttributeCell cell : attributeCells) {
                if (event.x() >= cell.x() && event.x() < cell.x() + cell.size() && event.y() >= cell.y() && event.y() < cell.y() + cell.size()) {
                    Network.sendToServer(new AttributeMessages.Spend(mca.getId(), cell.stat().id()));
                    return true;
                }
            }
        }
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

    /**
     * The sprites behind the screen's blocks of information, nine-slice so they stretch: a resource pack can restyle
     * them at assets/mca/textures/gui/sprites/interact/ (see scripts/claude-tools/gui_sprites.py for the defaults).
     */
    private static final Identifier PANEL = MCA.locate("interact/panel");
    private static final Identifier CELL = MCA.locate("interact/cell");
    private static final Identifier CELL_EMPTY = MCA.locate("interact/cell_empty");
    private static final Identifier CELL_LOCKED = MCA.locate("interact/cell_locked");

    private static void panel(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1) {
        sprite(context, PANEL, x0, y0, x1, y1);
    }

    private static void sprite(GuiGraphicsExtractor context, Identifier sprite, int x0, int y0, int x1, int y1) {
        if (x1 > x0 && y1 > y0) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x0, y0, x1 - x0, y1 - y0);
        }
    }

    /** Panels each villager has told about, kept while the game runs (expansion: talk). */
    private static final Map<UUID, Set<String>> REVEALED = new HashMap<>();
    private static final int SCHEDULE_CELL = 5;

    /** Whether a panel is shown: always, unless the information waits for the conversation and was not told yet. */
    private boolean shows(String panel) {
        return !TalkPanels.enabled() || REVEALED.getOrDefault(villager.asEntity().getUUID(), Set.of()).contains(panel);
    }

    /** The villager in the open interaction screen told about a panel. */
    public static void reveal(String panel) {
        if (Minecraft.getInstance().gui.screen() instanceof InteractScreen screen) {
            REVEALED.computeIfAbsent(screen.villager.asEntity().getUUID(), id -> new HashSet<>()).add(panel);
        }
    }

    /** A cell of the attribute strip, clickable to spend a point. */
    private record AttributeCell(VillagerAttributes.Stat stat, int x, int y, int size) {
    }

    private final List<AttributeCell> attributeCells = new ArrayList<>();

    /**
     * Level, points and aptitude for the profession on a line, then a cell per attribute with its value; hovering one
     * explains it, clicking spends a point on it (the server checks the player is the village chief).
     */
    private void drawAttributes(GuiGraphicsExtractor context, VillagerEntityMCA villager, int x, int y) {
        int points = VillagerAttributes.points(villager);
        int level = VillagerAttributes.level(villager);
        int stars = VillagerAttributes.stars(VillagerAttributes.aptitude(villager, villager.getProfession()));
        MutableComponent header = Component.translatable("attribute.mca.level", level);
        if (points > 0) {
            header.append(Component.literal(" · ")).append(Component.translatable("attribute.mca.points", points).withStyle(ChatFormatting.YELLOW));
        }
        header.append(Component.literal(" · ")).append(Component.translatable("attribute.mca.aptitude",
                Component.literal("●".repeat(stars) + "○".repeat(5 - stars)).withStyle(ChatFormatting.GOLD)));
        context.text(font, header, x, y, 0xFFFFFFFF);

        int cell = 18;
        int top = y + 11;
        VillagerAttributes.Stat[] stats = VillagerAttributes.Stat.values();
        float xp = VillagerAttributes.experience(villager) / VillagerAttributes.needed(level);
        panel(context, x, top, x + stats.length * (cell + 6) + 2, top + cell + 5);
        context.fill(x + 1, top + cell + 2, x + 1 + Math.round((stats.length * (cell + 6)) * xp), top + cell + 4, 0xFF7FD35A);
        VillagerAttributes.Fit fit = VillagerAttributes.fit(villager.getProfession());
        List<Component> tooltip = null;
        for (int i = 0; i < stats.length; i++) {
            VillagerAttributes.Stat stat = stats[i];
            int cellX = x + 1 + i * (cell + 6);
            boolean used = fit != null && (fit.primary() == stat || fit.others().contains(stat));
            context.fill(cellX, top + 1, cellX + cell, top + 1 + cell, used ? 0xFFC9A227 : 0xFF3A3A3A);
            sprite(context, CELL, cellX + 1, top + 2, cellX + cell - 1, top + cell);
            context.item(stat.icon(), cellX + 1, top + 2);
            context.text(font, Component.literal(String.valueOf(VillagerAttributes.get(villager, stat))), cellX + cell + 1, top + 6, stat.color());
            attributeCells.add(new AttributeCell(stat, cellX, top, cell + 6));
            if (hoveringOver(cellX, top, cell + 6, cell + 2)) {
                tooltip = new ArrayList<>();
                tooltip.add(stat.getName().copy().withStyle(ChatFormatting.BOLD));
                for (String line : stat.getDescription().getString().split("\n")) {
                    tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
                if (used) {
                    tooltip.add(Component.translatable(fit.primary() == stat ? "attribute.mca.main_for" : "attribute.mca.used_for",
                            villager.getProfessionText()).withStyle(ChatFormatting.GOLD));
                }
                if (points > 0) {
                    tooltip.add(Component.translatable("attribute.mca.click_to_spend").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        if (tooltip != null) {
            drawTooltip(context, tooltip, x + stats.length * (cell + 6), top + cell);
        }
    }

    /** A strip of the 24 hours of the day, colored by what the schedule asks for, the current hour marked. */
    private void drawSchedule(GuiGraphicsExtractor context, VillagerEntityMCA villager, int x, int y) {
        int now = SchedulesMCA.currentSlot(villager.level());
        Activity current = SchedulesMCA.plannedAt(villager, now);
        context.text(font, Component.translatable("gui.interact.label.schedule", ScheduleTimelineWidget.name(current)), x, y, 0xFFFFFFFF);
        int top = y + 11;
        panel(context, x - 1, top - 1, x + SchedulesMCA.SLOTS * SCHEDULE_CELL + 1, top + 7);
        for (int slot = 0; slot < SchedulesMCA.SLOTS; slot++) {
            context.fill(x + slot * SCHEDULE_CELL, top, x + (slot + 1) * SCHEDULE_CELL - 1, top + 6,
                    ScheduleTimelineWidget.color(SchedulesMCA.plannedAt(villager, slot)));
        }
        context.fill(x + now * SCHEDULE_CELL, top + 6, x + (now + 1) * SCHEDULE_CELL - 1, top + 8, 0xFFFFFFFF);
    }

    /** Where the villager's card starts; its icons (assets/mca/api/gui/icons.json) sit in a row right below it. */
    private static final int CARD_X = 10;
    private static final int CARD_Y = 8;
    private static final int CARD_HEIGHT = 44;
    /** Where the blocks of information start, below the card and its icons. */
    private static final int INFO_Y = CARD_Y + CARD_HEIGHT + 30;
    private static final int GAP = 6;

    /** The villager's card: name (or what a click does now), age or profession, mood once told. */
    private void drawCard(GuiGraphicsExtractor context, VillagerBrain<?> brain) {
        List<Component> lines = new ArrayList<>();
        lines.add(inGiftMode
                ? Component.translatable("gui.interact.label.giveGift").withStyle(ChatFormatting.YELLOW)
                : villager.asEntity().getName().copy().withStyle(ChatFormatting.BOLD));
        //age or profession; the skill only once told (expansion: talk)
        lines.add(villager.asEntity().isBaby() ? villager.getAgeState().getName()
                : villager instanceof VillagerEntityMCA mca && shows(TalkPanels.WORK) ? Skills.describe(mca, villager.getProfessionText()) // expansion: minds
                : villager.getProfessionText());
        if (shows(TalkPanels.FEELINGS)) {
            lines.add(Component.translatable("gui.interact.label.mood", brain.getMood().getText()).withStyle(brain.getMood().getColor()));
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        panel(context, CARD_X, CARD_Y, CARD_X + Math.max(150, width + 12), CARD_Y + CARD_HEIGHT);
        for (int i = 0; i < lines.size(); i++) {
            context.text(font, lines.get(i), CARD_X + 6, CARD_Y + 5 + i * 12, 0xFFFFFFFF);
        }
    }

    private void drawTextPopups(GuiGraphicsExtractor context) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        drawCard(context, brain);

        // the blocks of information, one below the other
        int below = INFO_Y;

        //traits, in place of the former personality line
        if (shows(TalkPanels.TRAITS)) {
            drawTraits(context, brain.getPersonality(), CARD_X, below);
            below += 20 + GAP;
        }

        //friends and rivals among the villagers
        if (villager instanceof VillagerEntityMCA mca && shows(TalkPanels.FRIENDS) && !mca.getOpinions().all().isEmpty()) {
            drawOpinions(context, mca.getOpinions(), CARD_X, below);
            below += 16 + GAP;
        }

        //attributes and level (expansion: minds), then the day's schedule once asked where they work (expansion: talk)
        attributeCells.clear();
        if (villager instanceof VillagerEntityMCA mca && VillagerAttributes.enabled() && VillagerAttributes.isRolled(mca) && shows(TalkPanels.SKILLS)) {
            drawAttributes(context, mca, CARD_X, below);
            below += 36 + GAP;
        }
        if (villager instanceof VillagerEntityMCA mca && TalkPanels.enabled() && shows(TalkPanels.WORK)) {
            drawSchedule(context, mca, CARD_X, below);
        }

        //action queue
        queueCells.clear();
        if (villager instanceof VillagerEntityMCA mca && ActionQueue.isEnabled()) {
            drawQueue(context, mca.getActionQueue());
        }

        //needs
        if (villager instanceof VillagerEntityMCA mca && Needs.isEnabled() && mca.getNeeds().hasNeeds() && shows(TalkPanels.FEELINGS)) {
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
            panel(context, width / 2 - 88, top, width / 2 + 88, bottom);
            int lineY = top + 4;
            for (FormattedCharSequence t : SpeechBubbles.typed(getVillagerUUID(), dialogQuestionText)) { // expansion: speech
                context.text(font, t, width / 2 - font.width(t) / 2, lineY, 0xFFFFFFFF);
                lineY += 10;
            }
            dialogAnswerHover = null;
        } else if (dialogQuestionText != null && dialogAnswers != null) {
            //background
            panel(context, width / 2 - 85, height / 2 - 50 - 10 * dialogQuestionText.size(), width / 2 + 85,
                    height / 2 - 30 + 10 * dialogAnswers.size());

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
        panel(context, x - 4, top - 3, x + total + 4, top + big + 14);

        final Matrix3x2fStack matrices = context.pose();
        for (int i = 0; i < entries.size(); i++) {
            ActionQueue.Entry entry = entries.get(i);
            int size = i == 0 ? big : small;
            int y = top + (big - size) / 2;
            sprite(context, CELL, x, y, x + size, y + size);
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
        panel(context, x, y, x + all.length * cell + 2, y + height);

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

    /**
     * A row of item icons, one per personality slot of the age (locked ones dimmed), then the
     * genetic traits. The icon border shows the category, a star the trait giving the voice.
     * Hovering a trait lists its effects.
     */
    private void drawTraits(GuiGraphicsExtractor context, Personality voice, int x, int y) {
        Traits traits = villager.getTraits();
        List<Traits.Trait> personality = traits.getPersonalityTraits();
        List<Traits.Trait> genetic = traits.getTraits().stream().filter(t -> !t.getCategory().usesSlot()).toList();
        int open = Math.max(Traits.getSlots(villager.getAgeState()), personality.size());
        int slots = Math.max(open, TraitTooltips.maxSlots());
        if (slots == 0 && genetic.isEmpty()) {
            return;
        }
        int cell = 18;
        int gap = genetic.isEmpty() || slots == 0 ? 0 : 6;
        int width = (slots + genetic.size()) * cell + gap + 2;
        panel(context, x, y, x + width, y + cell + 2);

        final Matrix3x2fStack matrices = context.pose();
        List<Component> tooltip = null;
        for (int i = 0; i < slots + genetic.size(); i++) {
            int cellX = x + 1 + i * cell + (i >= slots ? gap : 0);
            Traits.Trait trait = i < personality.size() ? personality.get(i) : i >= slots ? genetic.get(i - slots) : null;
            if (trait != null) {
                context.fill(cellX, y + 1, cellX + cell, y + 1 + cell, trait.getCategory().color());
                sprite(context, CELL, cellX + 1, y + 2, cellX + cell - 1, y + cell);
                context.item(trait.getIcon(), cellX + 1, y + 2);
                if (trait.getVoice() == voice) {
                    matrices.pushMatrix();
                    matrices.translate(cellX + cell - 5, y + 1);
                    matrices.scale(0.75F, 0.75F);
                    context.text(font, Component.literal("★"), 0, 0, 0xFFFFDD55);
                    matrices.popMatrix();
                }
            } else {
                // a free slot of this age, or one that opens later
                sprite(context, i < open ? CELL_EMPTY : CELL_LOCKED, cellX + 1, y + 2, cellX + cell - 1, y + cell);
                if (i >= open) {
                    context.centeredText(font, Component.literal("-"), cellX + cell / 2, y + 6, 0xFF555555);
                }
            }
            if (hoveringOver(cellX, y, cell, cell + 2)) {
                tooltip = trait != null ? TraitTooltips.lines(trait, voice)
                        : List.of(i < open ? Component.translatable("trait.slot.empty").withStyle(ChatFormatting.GRAY) : TraitTooltips.lockedSlot(i));
            }
        }
        if (tooltip != null) {
            drawTooltip(context, tooltip, x + width, y + cell);
        }
    }

    /** A line with the best friends and the worst rivals; hovering it lists everyone remembered. */
    private void drawOpinions(GuiGraphicsExtractor context, Opinions opinions, int x, int y) {
        List<Opinions.Entry> all = opinions.all();
        if (all.isEmpty()) {
            return;
        }
        MutableComponent line = Component.empty();
        List<Opinions.Entry> friends = opinions.friends();
        List<Opinions.Entry> rivals = opinions.rivals();
        if (!friends.isEmpty()) {
            line.append(Component.translatable("opinion.friends", names(friends)).withStyle(ChatFormatting.GREEN));
        }
        if (!rivals.isEmpty()) {
            if (!friends.isEmpty()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY));
            }
            line.append(Component.translatable("opinion.rivals", names(rivals)).withStyle(ChatFormatting.RED));
        }
        if (friends.isEmpty() && rivals.isEmpty()) {
            line.append(Component.translatable("opinion.title").withStyle(ChatFormatting.GRAY));
        }
        int width = font.width(line) + 12;
        panel(context, x, y, x + width, y + 16);
        context.text(font, line, x + 6, y + 4, 0xFFFFFFFF);
        if (hoveringOver(x, y, width, 16)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("opinion.title").withStyle(ChatFormatting.BOLD));
            for (Opinions.Entry entry : all) {
                lines.add(Component.literal(entry.name() + " ").append(entry.label())
                        .append(Component.literal(" " + (entry.value() > 0 ? "+" : "") + entry.value()).withStyle(ChatFormatting.DARK_GRAY)));
            }
            drawTooltip(context, lines, x + width, y + 16);
        }
    }

    /** The first names, at most three. */
    private static String names(List<Opinions.Entry> entries) {
        return String.join(", ", entries.stream().limit(3).map(Opinions.Entry::name).toList());
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



