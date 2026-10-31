package net.conczin.mca.client.gui;

import org.joml.Matrix3x2fStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.expansion.townhall.TownHallMessages;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * The village's residents, from the town hall or the blueprint: a grid of cards with each one's face and name, and an
 * inspector beside it for the one picked, with age, job, aptitude, traits and attributes. From the inspector the chief
 * puts a resident on a job site, the one without a job too. Filled by the server (TownHallMessages); a resident far
 * away (not loaded) shows its name without a face.
 */
public class ResidentsScreen extends Screen {
    private static final Identifier PANEL = MCA.locate("interact/panel");
    private static final Identifier CELL = MCA.locate("interact/cell");
    private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);
    private static final ItemStack NO = new ItemStack(Items.BARRIER);
    private static final int CARD_WIDTH = 52;
    private static final int CARD_HEIGHT = 60;
    private static final int FACE = 40;
    private static final int INSPECTOR = 190;
    private static final int SITE_ROWS = 5;
    private static final int TOP = 30;
    /** How far (in pixels of mouse offset) faces follow the mouse up and down; about three degrees. */
    private static final float LOOK_UP_DOWN = 6.0F;

    private TownHallMessages.@Nullable Response data;
    /** One copy for the cards and one for the inspector: the same entity drawn twice a frame jiggles under EMF. */
    private final Map<UUID, VillagerEntityMCA> looks = new HashMap<>();
    private final Map<UUID, VillagerEntityMCA> inspected = new HashMap<>();
    /** The tooltip of this frame, drawn after everything else so no text runs over it. */
    private @Nullable List<Component> tooltip;
    private @Nullable UUID selected;
    private boolean joblessOnly;
    private boolean pickingSite;
    private int page;
    private int sitePage;
    private List<TownHallMessages.Resident> shown = List.of();
    private int columns = 1;
    private int rows = 1;
    private int gridLeft;
    private int pageCount = 1;

    public ResidentsScreen() {
        super(Component.translatable("town_hall.residents"));
    }

    /** The server's answer, for the open screen. */
    public static void receive(TownHallMessages.Response response) {
        if (Minecraft.getInstance().gui.screen() instanceof ResidentsScreen screen) {
            screen.data = response;
            screen.looks.clear();
            screen.inspected.clear();
            CompoundTag looks = response.looks();
            for (String key : looks.keySet()) {
                looks.getCompound(key).ifPresent(nbt -> {
                    screen.looks.put(UUID.fromString(key), dummy(nbt));
                    screen.inspected.put(UUID.fromString(key), dummy(nbt));
                });
            }
            screen.rebuildWidgets();
        }
    }

    private static VillagerEntityMCA dummy(CompoundTag nbt) {
        VillagerEntityMCA dummy = PreviewEntities.villager();
        dummy.load(TagValueInput.create(ProblemReporter.DISCARDING, dummy.registryAccess(), nbt));
        return dummy;
    }

    /**
     * Draws a villager in a box, turning to the mouse from side to side but only a little up and down, so a face
     * never looks down at the player. On the preview clock, like the editor's preview.
     */
    private static void drawLook(GuiGraphicsExtractor context, VillagerEntityMCA look, int x0, int y0, int x1, int y1,
                                 int size, float offsetY, int mouseX, int mouseY) {
        float centerY = (y0 + y1) / 2.0F;
        float lookY = centerY + Mth.clamp(mouseY - centerY, -LOOK_UP_DOWN, LOOK_UP_DOWN);
        PreviewEntityAnimation.withPreviewTime(look, () -> {
            InventoryScreen.extractEntityInInventoryFollowsMouse(context, x0, y0, x1, y1, size, offsetY, mouseX, lookY, look);
            return null;
        });
    }

    @Override
    protected void init() {
        if (data == null) {
            Network.sendToServer(new TownHallMessages.Request());
        }
        int gridWidth = width - INSPECTOR - 30;
        columns = Math.max(2, gridWidth / CARD_WIDTH);
        rows = Math.max(1, (height - TOP - 40) / CARD_HEIGHT);
        gridLeft = 10 + (gridWidth - columns * CARD_WIDTH) / 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> minecraft.gui.setScreen(new TownHallScreen()))
                .bounds(10, height - 26, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(joblessOnly ? "town_hall.residents.all" : "town_hall.residents.jobless"), b -> {
            joblessOnly = !joblessOnly;
            page = 0;
            rebuildWidgets();
        }).bounds(74, height - 26, 90, 20).build());
        if (data == null) {
            return;
        }
        shown = residents();
        int perPage = columns * rows;
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        pager(gridLeft + columns * CARD_WIDTH - 44, height - 26, pages, page, value -> page = value);
        pageCount = pages;
        initInspector();
    }

    /** Residents by name, the leader first; only the ones without a job when filtered. */
    private List<TownHallMessages.Resident> residents() {
        List<TownHallMessages.Resident> residents = new ArrayList<>(data.residents());
        if (joblessOnly) {
            residents.removeIf(r -> !isJobless(r));
        }
        residents.sort(Comparator.comparing((TownHallMessages.Resident r) -> !r.leader()).thenComparing(TownHallMessages.Resident::name));
        return residents;
    }

    private static boolean isJobless(TownHallMessages.Resident resident) {
        return resident.profession().endsWith(":none") || resident.profession().endsWith(":nitwit");
    }

    private Optional<TownHallMessages.Resident> chosen() {
        return data == null || selected == null ? Optional.empty() : data.residents().stream().filter(r -> r.id().equals(selected)).findFirst();
    }

    private int inspectorLeft() {
        return width - INSPECTOR - 10;
    }

    /** The inspector's buttons: pick a job site (free ones, best fit first), or let the leader decide again. */
    private void initInspector() {
        Optional<TownHallMessages.Resident> resident = chosen();
        if (resident.isEmpty()) {
            return;
        }
        int left = inspectorLeft() + 6;
        int buttonWidth = INSPECTOR - 12;
        int y = height - 26;
        if (resident.get().pinned()) {
            Button release = Button.builder(Component.translatable("town_hall.jobs.release"),
                            b -> Network.sendToServer(new TownHallMessages.Assign(resident.get().id(), Optional.empty())))
                    .bounds(left, y, buttonWidth, 20).build();
            release.active = data.chief();
            addRenderableWidget(release);
            y -= 22;
        }
        Button pick = Button.builder(Component.translatable(pickingSite ? "town_hall.residents.hide_sites"
                        : isJobless(resident.get()) ? "town_hall.residents.give_job" : "town_hall.residents.change_job"), b -> {
                    pickingSite = !pickingSite;
                    sitePage = 0;
                    rebuildWidgets();
                })
                .bounds(left, y, buttonWidth, 20).build();
        pick.active = data.chief();
        if (!data.chief()) {
            pick.setTooltip(Tooltip.create(Component.translatable("town_hall.not_you_chief")));
        }
        addRenderableWidget(pick);
        if (!pickingSite || !data.chief()) {
            return;
        }
        List<TownHallMessages.Site> free = data.sites().stream().filter(s -> s.worker().isEmpty())
                .sorted(Comparator.comparingDouble((TownHallMessages.Site s) -> -fit(resident.get(), s))).toList();
        int pages = Math.max(1, (free.size() + SITE_ROWS - 1) / SITE_ROWS);
        sitePage = Math.min(sitePage, pages - 1);
        int top = y - 22 * SITE_ROWS - (pages > 1 ? 20 : 0);
        for (int i = 0; i < SITE_ROWS; i++) {
            int index = sitePage * SITE_ROWS + i;
            if (index >= free.size()) {
                break;
            }
            TownHallMessages.Site site = free.get(index);
            MutableComponent label = TownHallJobsScreen.profession(professionOf(site)).copy();
            float fit = fit(resident.get(), site);
            if (fit >= 0) {
                label.append(" ").append(TownHallJobsScreen.stars(fit));
            }
            addRenderableWidget(Button.builder(label, b -> {
                        pickingSite = false;
                        Network.sendToServer(new TownHallMessages.Assign(resident.get().id(), Optional.of(site.pos())));
                    })
                    .bounds(left, top + i * 22, buttonWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("town_hall.jobs.at", site.pos().getX() + ", " + site.pos().getY() + ", " + site.pos().getZ())))
                    .build());
        }
        if (free.isEmpty()) {
            pickingSite = false;
        }
        if (pages > 1) {
            pager(left, top + SITE_ROWS * 22, pages, sitePage, value -> sitePage = value);
        }
    }

    private String professionOf(TownHallMessages.Site site) {
        return site.profession() >= 0 && site.profession() < data.professions().size() ? data.professions().get(site.profession()) : "";
    }

    private static float fit(TownHallMessages.Resident resident, TownHallMessages.Site site) {
        return site.profession() >= 0 && site.profession() < resident.fits().size() ? resident.fits().get(site.profession()) : -1.0F;
    }

    private void pager(int x, int y, int pages, int current, IntConsumer set) {
        if (pages <= 1) {
            return;
        }
        Button previous = Button.builder(Component.literal("<"), b -> {
            set.accept(current - 1);
            rebuildWidgets();
        }).bounds(x, y, 20, 20).build();
        previous.active = current > 0;
        Button next = Button.builder(Component.literal(">"), b -> {
            set.accept(current + 1);
            rebuildWidgets();
        }).bounds(x + 22, y, 20, 20).build();
        next.active = current < pages - 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int index = cardAt(event.x(), event.y());
        if (event.button() == 0 && index >= 0) {
            UUID id = shown.get(index).id();
            selected = id.equals(selected) ? null : id;
            pickingSite = false;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    /** The index (in the filtered list) of the card under the mouse, or -1. */
    private int cardAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - gridLeft) / CARD_WIDTH);
        int row = (int) Math.floor((mouseY - TOP) / CARD_HEIGHT);
        if (column < 0 || column >= columns || row < 0 || row >= rows || mouseX < gridLeft || mouseY < TOP) {
            return -1;
        }
        int index = page * columns * rows + row * columns + column;
        return index < shown.size() ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (data == null) {
            context.centeredText(font, Component.translatable("gui.loading"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        context.text(font, Component.translatable("town_hall.residents.header", data.village(), data.residents().size()), gridLeft, 12, 0xFFFFFFFF);
        int hovered = cardAt(mouseX, mouseY);
        int start = page * columns * rows;
        for (int i = 0; i < columns * rows && start + i < shown.size(); i++) {
            drawCard(context, shown.get(start + i), gridLeft + (i % columns) * CARD_WIDTH, TOP + (i / columns) * CARD_HEIGHT,
                    start + i == hovered, mouseX, mouseY);
        }
        if (pageCount > 1) {
            context.text(font, Component.literal((page + 1) + "/" + pageCount), gridLeft + columns * CARD_WIDTH - 70, height - 20, 0xFFAAAAAA);
        }
        tooltip = null;
        chosen().ifPresent(resident -> drawInspector(context, resident, mouseX, mouseY));
        if (tooltip != null) {
            WidgetUtils.drawTooltip(context, font, tooltip, mouseX, mouseY);
        }
    }

    private void drawCard(GuiGraphicsExtractor context, TownHallMessages.Resident resident, int x, int y, boolean hovered, int mouseX, int mouseY) {
        boolean picked = resident.id().equals(selected);
        context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x + 1, y + 1, CARD_WIDTH - 2, CARD_HEIGHT - 2);
        if (picked || hovered) {
            int color = picked ? 0xFFFFD54A : 0x80FFFFFF;
            context.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + 2, color);
            context.fill(x + 1, y + CARD_HEIGHT - 2, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, color);
            context.fill(x + 1, y + 1, x + 2, y + CARD_HEIGHT - 1, color);
            context.fill(x + CARD_WIDTH - 2, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, color);
        }
        int faceX = x + (CARD_WIDTH - FACE) / 2;
        int faceY = y + 4;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, CELL, faceX, faceY, FACE, FACE);
        VillagerEntityMCA look = looks.get(resident.id());
        if (look != null) {
            // head and shoulders: about 44% of the villager's height shows, centered three quarters up
            float height = look.getBbHeight();
            drawLook(context, look, faceX + 1, faceY + 1, faceX + FACE - 1, faceY + FACE - 1,
                    (int) (FACE / Math.max(0.5F, height * 0.44F)), height * 0.25F, mouseX, mouseY);
        } else {
            context.centeredText(font, Component.literal("?"), faceX + FACE / 2, faceY + FACE / 2 - 4, 0xFF888888);
        }
        if (resident.leader()) {
            context.text(font, Component.literal("♛"), x + 3, y + 3, 0xFFFFD54A);
        }
        if (isJobless(resident)) {
            // no job: a hoe crossed out
            Matrix3x2fStack matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate(x + CARD_WIDTH - 13, y + 3);
            matrices.scale(0.6F, 0.6F);
            context.item(HOE, 0, 0);
            context.item(NO, 0, 0);
            matrices.popMatrix();
        }
        String name = font.plainSubstrByWidth(resident.name(), CARD_WIDTH - 4);
        context.centeredText(font, Component.literal(name), x + CARD_WIDTH / 2, y + CARD_HEIGHT - 13, 0xFFFFFFFF);
    }

    private void drawInspector(GuiGraphicsExtractor context, TownHallMessages.Resident resident, int mouseX, int mouseY) {
        int left = inspectorLeft();
        context.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left, 8, INSPECTOR, height - 40);
        VillagerEntityMCA look = inspected.get(resident.id());
        int x = left + 8;
        int y = 14;
        if (look != null) {
            drawLook(context, look, left + INSPECTOR - 62, y, left + INSPECTOR - 8, y + 74, 36, 0.0F, mouseX, mouseY);
        }
        int textWidth = INSPECTOR - 74;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal((resident.leader() ? "♛ " : "") + resident.name()).withStyle(ChatFormatting.BOLD));
        if (look != null) {
            lines.add(look.getAgeState().getName().copy().withStyle(ChatFormatting.GRAY));
        }
        lines.add(isJobless(resident) ? Component.translatable("town_hall.residents.no_job").withStyle(ChatFormatting.RED)
                : TownHallJobsScreen.profession(resident.profession()));
        if (resident.aptitude() >= 0) {
            lines.add(Component.translatable("attribute.mca.aptitude", TownHallJobsScreen.stars(resident.aptitude())));
        }
        if (resident.pinned()) {
            lines.add(Component.translatable("town_hall.residents.pinned").withStyle(ChatFormatting.AQUA));
        }
        if (look != null) {
            lines.add(Component.translatable("gui.interact.label.mood", look.getVillagerBrain().getMood().getText())
                    .withStyle(look.getVillagerBrain().getMood().getColor()));
        }
        for (Component line : lines) {
            for (var part : font.split(line, textWidth)) {
                context.text(font, part, x, y, 0xFFFFFFFF);
                y += 10;
            }
        }
        y = Math.max(y, 92) + 4;
        if (look == null) {
            context.text(font, Component.translatable("town_hall.residents.far"), x, y, 0xFF888888);
            return;
        }

        // traits, with their tooltips
        List<Traits.Trait> traits = look.getTraits().getPersonalityTraits();
        if (!traits.isEmpty()) {
            context.text(font, Component.translatable("gui.villager_editor.page.traits"), x, y, 0xFFAAAAAA);
            y += 10;
            for (int i = 0; i < traits.size(); i++) {
                int cellX = x + (i % 8) * 20;
                int cellY = y + (i / 8) * 20;
                context.fill(cellX, cellY, cellX + 18, cellY + 18, traits.get(i).getCategory().color());
                context.blitSprite(RenderPipelines.GUI_TEXTURED, CELL, cellX + 1, cellY + 1, 16, 16);
                context.item(traits.get(i).getIcon(), cellX + 1, cellY + 1);
                if (mouseX >= cellX && mouseX < cellX + 18 && mouseY >= cellY && mouseY < cellY + 18) {
                    tooltip = TraitTooltips.lines(traits.get(i), look.getVillagerBrain().getPersonality());
                }
            }
            y += ((traits.size() + 7) / 8) * 20 + 4;
        }

        // attributes
        if (VillagerAttributes.enabled() && VillagerAttributes.isRolled(look)) {
            context.text(font, Component.translatable("attribute.mca.level", VillagerAttributes.level(look)), x, y, 0xFFAAAAAA);
            y += 10;
            int column = 0;
            for (VillagerAttributes.Stat stat : VillagerAttributes.Stat.values()) {
                int cellX = x + (column % 3) * 58;
                int cellY = y + (column / 3) * 18;
                context.item(stat.icon(), cellX, cellY);
                context.text(font, Component.literal(String.valueOf(VillagerAttributes.get(look, stat))), cellX + 18, cellY + 4, stat.color());
                if (mouseX >= cellX && mouseX < cellX + 56 && mouseY >= cellY && mouseY < cellY + 16) {
                    List<Component> about = new ArrayList<>();
                    about.add(stat.getName().copy().withStyle(ChatFormatting.BOLD));
                    for (String line : stat.getDescription().getString().split("\n")) {
                        about.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                    }
                    tooltip = about;
                }
                column++;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
