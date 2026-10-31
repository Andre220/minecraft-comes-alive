package net.conczin.mca.client.gui;

import net.conczin.mca.expansion.townhall.TownHallMessages;
import net.conczin.mca.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two panels of the town hall, filled by the server (TownHallMessages): the overview, with the leader and every tax,
 * and the jobs panel, where the chief picks a resident and puts it on a job site; the aptitude stars tell who fits
 * where. Vanilla buttons and text, so resource packs that restyle the game restyle it too.
 */
public class TownHallJobsScreen extends Screen {
    public enum Mode {
        OVERVIEW, JOBS
    }

    private static final int ROWS = 9;
    private static final int ROW = 20;
    private static final int COLUMN = 190;

    private final Mode mode;
    private TownHallMessages.@Nullable Response data;
    private @Nullable UUID selected;
    private int residentPage;
    private int sitePage;

    public TownHallJobsScreen(Mode mode) {
        super(Component.translatable(mode == Mode.OVERVIEW ? "town_hall.overview" : "town_hall.jobs"));
        this.mode = mode;
    }

    /** The server's answer, for the open panel. */
    public static void receive(TownHallMessages.Response response) {
        if (Minecraft.getInstance().gui.screen() instanceof TownHallJobsScreen screen) {
            screen.data = response;
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        if (data == null) {
            Network.sendToServer(new TownHallMessages.Request());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> minecraft.gui.setScreen(new TownHallScreen()))
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        if (data == null) {
            return;
        }
        if (mode == Mode.OVERVIEW) {
            addRenderableWidget(Button.builder(Component.translatable("town_hall.change_rules"), b -> minecraft.gui.setScreen(new BlueprintScreen("rules")))
                    .bounds(width / 2 - 70, height - 52, 140, 20).build());
            return;
        }
        initJobs();
    }

    private void initJobs() {
        int left = width / 2 - COLUMN - 5;
        int right = width / 2 + 5;
        int top = 48;
        List<TownHallMessages.Resident> residents = residents();
        int pages = Math.max(1, (residents.size() + ROWS - 1) / ROWS);
        residentPage = Math.min(residentPage, pages - 1);
        for (int i = 0; i < ROWS; i++) {
            int index = residentPage * ROWS + i;
            if (index >= residents.size()) {
                break;
            }
            TownHallMessages.Resident resident = residents.get(index);
            Button button = Button.builder(residentLabel(resident), b -> {
                        selected = resident.id();
                        sitePage = 0;
                        rebuildWidgets();
                    })
                    .bounds(left, top + i * ROW, COLUMN, ROW - 2).build();
            button.active = !resident.id().equals(selected);
            addRenderableWidget(button);
        }
        pager(left, top + ROWS * ROW, pages, residentPage, page -> residentPage = page);

        TownHallMessages.Resident chosen = residents.stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
        if (chosen == null) {
            return;
        }
        List<TownHallMessages.Site> sites = sites(chosen);
        int sitePages = Math.max(1, (sites.size() + ROWS - 1) / ROWS);
        sitePage = Math.min(sitePage, sitePages - 1);
        for (int i = 0; i < ROWS; i++) {
            int index = sitePage * ROWS + i;
            if (index >= sites.size()) {
                break;
            }
            TownHallMessages.Site site = sites.get(index);
            Button button = Button.builder(siteLabel(chosen, site), b -> Network.sendToServer(new TownHallMessages.Assign(chosen.id(), Optional.of(site.pos()))))
                    .bounds(right, top + i * ROW, COLUMN, ROW - 2)
                    .tooltip(Tooltip.create(Component.translatable("town_hall.jobs.at", describe(site.pos())))).build();
            button.active = data.chief() && site.worker().isEmpty();
            addRenderableWidget(button);
        }
        pager(right, top + ROWS * ROW, sitePages, sitePage, page -> sitePage = page);
        if (chosen.pinned()) {
            Button release = Button.builder(Component.translatable("town_hall.jobs.release"),
                    b -> Network.sendToServer(new TownHallMessages.Assign(chosen.id(), Optional.empty())))
                    .bounds(right, top + ROWS * ROW + 22, COLUMN, 20).build();
            release.active = data.chief();
            addRenderableWidget(release);
        }
    }

    private void pager(int x, int y, int pages, int page, java.util.function.IntConsumer set) {
        if (pages <= 1) {
            return;
        }
        Button previous = Button.builder(Component.literal("<"), b -> {
            set.accept(page - 1);
            rebuildWidgets();
        }).bounds(x, y, 20, 18).build();
        previous.active = page > 0;
        Button next = Button.builder(Component.literal(">"), b -> {
            set.accept(page + 1);
            rebuildWidgets();
        }).bounds(x + COLUMN - 20, y, 20, 18).build();
        next.active = page < pages - 1;
        addRenderableWidget(previous);
        addRenderableWidget(next);
    }

    /** Residents by name, the leader first. */
    private List<TownHallMessages.Resident> residents() {
        List<TownHallMessages.Resident> residents = new ArrayList<>(data.residents());
        residents.sort(Comparator.comparing((TownHallMessages.Resident r) -> !r.leader()).thenComparing(TownHallMessages.Resident::name));
        return residents;
    }

    /** Free sites first, the ones the resident fits best on top. */
    private List<TownHallMessages.Site> sites(TownHallMessages.Resident resident) {
        List<TownHallMessages.Site> sites = new ArrayList<>(data.sites());
        sites.sort(Comparator.comparing((TownHallMessages.Site s) -> !s.worker().isEmpty())
                .thenComparing(s -> -fit(resident, s)));
        return sites;
    }

    private static float fit(TownHallMessages.Resident resident, TownHallMessages.Site site) {
        return site.profession() >= 0 && site.profession() < resident.fits().size() ? resident.fits().get(site.profession()) : -1.0F;
    }

    private Component residentLabel(TownHallMessages.Resident resident) {
        MutableComponent label = Component.literal((resident.leader() ? "♛ " : resident.pinned() ? "📌 " : "") + resident.name() + " · ")
                .append(profession(resident.profession()));
        if (resident.aptitude() >= 0) {
            label.append(" ").append(stars(resident.aptitude()));
        }
        return label;
    }

    private Component siteLabel(TownHallMessages.Resident resident, TownHallMessages.Site site) {
        String id = site.profession() >= 0 && site.profession() < data.professions().size() ? data.professions().get(site.profession()) : "";
        MutableComponent label = profession(id).copy();
        float fit = fit(resident, site);
        if (fit >= 0) {
            label.append(" ").append(stars(fit));
        }
        label.append(Component.literal(site.worker().isEmpty() ? "" : " · " + site.worker()).withStyle(ChatFormatting.GRAY));
        return label;
    }

    static Component stars(float aptitude) {
        int stars = Math.round(aptitude * 5.0F);
        return Component.literal("●".repeat(stars) + "○".repeat(5 - stars)).withStyle(ChatFormatting.GOLD);
    }

    static Component profession(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return Component.literal(id);
        }
        // the names vanilla and MCA give professions: entity.minecraft.villager.farmer, entity.minecraft.villager.mca.guard
        String name = key.getNamespace().equals("minecraft") ? key.getPath() : key.getNamespace() + "." + key.getPath();
        return Component.translatable("entity.minecraft.villager." + name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        if (data == null) {
            context.centeredText(font, Component.translatable("gui.loading"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }
        Component chief = data.chief() ? Component.translatable("town_hall.you_chief").withStyle(ChatFormatting.GREEN)
                : Component.translatable("town_hall.not_you_chief").withStyle(ChatFormatting.GRAY);
        context.centeredText(font, Component.translatable("town_hall.header", data.village(), data.population(),
                data.leader().isEmpty() ? Component.translatable("town_hall.no_leader") : Component.literal(data.leader()), chief), width / 2, 24, 0xFFDDDDDD);
        if (mode == Mode.OVERVIEW) {
            drawOverview(context);
        } else {
            context.text(font, Component.translatable("town_hall.jobs.residents"), width / 2 - COLUMN - 5, 36, 0xFFFFFFFF);
            context.text(font, Component.translatable(selected == null ? "town_hall.jobs.pick" : "town_hall.jobs.sites"), width / 2 + 5, 36, 0xFFFFFFFF);
        }
    }

    private void drawOverview(GuiGraphicsExtractor context) {
        TownHallMessages.Taxes taxes = data.taxes();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("town_hall.taxes.title").withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("town_hall.taxes.rate", Math.round(taxes.rate() * 100)));
        if (taxes.fromProduction()) {
            lines.add(Component.translatable("town_hall.taxes.production", Math.round(taxes.rate() * 100)));
        } else {
            lines.add(Component.translatable("town_hall.taxes.season", Math.round(taxes.perSeason() * 10) / 10.0F, minutes(taxes.season())));
            lines.add(Component.translatable("town_hall.taxes.next", minutes(taxes.nextIn())));
            if (taxes.library()) {
                lines.add(Component.translatable("town_hall.taxes.library").withStyle(ChatFormatting.GREEN));
            }
        }
        if (!taxes.hasStorage()) {
            lines.add(Component.translatable("town_hall.taxes.no_storage").withStyle(ChatFormatting.RED));
        }
        if (taxes.rate() == 0) {
            lines.add(Component.translatable("town_hall.taxes.none").withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("town_hall.rules.title").withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("town_hall.rules.births", Math.round(taxes.births() * 100)));
        lines.add(Component.translatable("town_hall.rules.marriages", Math.round(taxes.marriages() * 100)));
        int y = 44;
        for (Component line : lines) {
            context.centeredText(font, line, width / 2, y, 0xFFFFFFFF);
            y += 12;
        }
    }

    private static int minutes(int ticks) {
        return Math.max(1, Math.round(ticks / 1200.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A job site's position as text, for its tooltip. */
    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
