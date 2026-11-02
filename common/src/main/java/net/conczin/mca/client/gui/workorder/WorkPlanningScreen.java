package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.client.gui.widget.IconButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.BlueprintCatalogMessage;
import net.conczin.mca.network.c2s.BlueprintRequest;
import net.conczin.mca.network.c2s.CaptureBlueprintMessage;
import net.conczin.mca.network.c2s.ManageWorkOrderMessage;
import net.conczin.mca.network.c2s.WorkOrdersRequest;
import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.conczin.mca.server.world.data.workorder.BlueprintCatalog;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plan how the village grows, MineFortress style: fly a free camera over the village, pick a
 * tool, drag out areas, paint walls or put down blueprints. Everything goes into a draft first,
 * which can be looked at from all sides and changed (pieces can be picked up again) before it is
 * confirmed; then the village's builders and miners do the work. Buildings made by hand can be
 * captured as new blueprints and sorted into sections.
 */
public class WorkPlanningScreen extends Screen {
    private static final int REFRESH_INTERVAL = 40;
    private static final double PICK_RANGE = 160.0;
    private static final float LOOK_SENSITIVITY = 0.3F;
    private static final double SCROLL_CLIMB = 2.0;
    private static final int TOOLBAR_Y_OFFSET = 26;
    private static final int PANEL_WIDTH = 196;
    private static final int TOOL_WIDTH = 74;
    private static final int SHAPE_WIDTH = 58;
    private static final int SECOND_ROW_OFFSET = 24;
    private static final int TOP_ROW_Y = 6;
    /** Below the top buttons: the draft bar, then the panels on the right. */
    private static final int DRAFT_ROW_Y = 30;
    private static final int RIGHT_PANEL_TOP = 54;
    private static final int ORDERS_FIRST_ROW_OFFSET = 14;
    private static final int ORDER_ROW_HEIGHT = 46;
    private static final int LIST_TOP = 74;
    /** How far a blueprint can be raised above the spot it points at; it can be sunk its own height plus this much. */
    private static final int MAX_LIFT = 16;
    private static final int MAX_EXTRUDE = 48;
    /** The widest brush and eraser, in blocks. */
    private static final int MAX_BRUSH = 5;
    private static final int MAX_RISE = 4;
    private static final int STATUS_TICKS = 100;
    /** Blocks touching the capture box from outside that are marked at most. */
    private static final int MAX_TOUCHING = 400;
    private static final String HELP_PREF = "help";

    private enum Movement {FORWARD, BACK, LEFT, RIGHT, UP, DOWN, FAST}

    private final PlanningSelection selection = new PlanningSelection();
    private final PlanningDraft draft = new PlanningDraft();
    private final Set<Movement> held = EnumSet.noneOf(Movement.class);
    /** Blueprints fetched for previews, by id; dropped when the server reports them changed. */
    private final Map<String, Blueprint> blueprints = new HashMap<>();
    private final Set<String> requested = new LinkedHashSet<>();
    private final BlockCatalog catalog;
    private final BlueprintBrowser browser;
    private @Nullable PlanningCamera camera;
    private @Nullable WorkOrdersResponse data;
    private boolean showHelp = PlanningPrefs.getBoolean(HELP_PREF, true);
    private boolean showOrders;
    private boolean rotating;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private int ticks;
    private @Nullable Component status;
    private int statusTicks;
    /** Leaving with an unconfirmed draft takes a second try. */
    private int closeArmedTicks;

    // cursor
    private @Nullable BlockPos hovered;
    private @Nullable BlockHitResult hit;
    /** The draft piece under the cursor, in front of the world. */
    private PlanningDraft.@Nullable Piece hoveredPiece;

    // digging
    private boolean demolish;

    // building
    private PlanningWands.Shape shape = PlanningWands.Shape.BOX;
    private @Nullable Block block;
    private int turns;
    private boolean flipped;
    /** The block as it would be placed at the cursor, facing and all. */
    private @Nullable BlockState placing;
    /** Box shapes: the state fixed when the box was started. */
    private @Nullable BlockState boxState;
    private @Nullable BlockPos lineStart;
    private @Nullable BlockState lineState;
    // brush
    private final Map<BlockPos, BlockState> stroke = new LinkedHashMap<>();
    /** Which column of the stroke stands where, by x and z: the stroke keeps one ground spot per column. */
    private final Map<Long, BlockPos> strokeColumns = new HashMap<>();
    /** Extruding: all columns up to one level top (a straight wall), or each the same height above its own ground. */
    private boolean strokeLevel = true;
    private int strokeHeight = 1;
    private @Nullable BlockState strokeTop;
    private boolean painting;
    private @Nullable BlockPos lastPainted;
    private int brushSize = 1;
    /** Dragging the eraser over planned blocks. */
    private boolean erasing;
    // repeat and mirror
    private @Nullable Map<BlockPos, BlockState> copySource;
    private @Nullable WorkArea copyArea;
    private int repeatRise;
    private @Nullable Map<BlockPos, BlockState> copyPreview;

    // pieces picked up, and blueprints at the cursor
    private PlanningDraft.@Nullable Piece heldPiece;
    private BlockPos heldOffset = BlockPos.ZERO;
    private int heldLift;
    private @Nullable String blueprintId;
    private int rotation;
    /** How far the blueprint is raised (or sunk into the ground, below zero) from where the cursor points. */
    private int blueprintLift;
    private PlanningDraft.@Nullable Piece preview;

    // capturing
    private @Nullable WorkArea captureBox;
    private @Nullable Direction captureFace;
    private List<BlockPos> touching = List.of();
    private @Nullable EditBox nameBox;
    private boolean naming;
    private String captureSection = "";
    // a new blueprint section
    private boolean sectionNaming;
    private @Nullable EditBox sectionBox;
    private @Nullable Block sectionIcon;

    public WorkPlanningScreen() {
        super(Component.translatable("gui.workorder.title"));
        catalog = new BlockCatalog(this::pickFromCatalog, this::villageBlocks);
        browser = new BlueprintBrowser(id -> {
            selectBlueprint(id);
            rebuildWidgets();
        }, this::startSectionNaming, this::blueprintForThumbnail);
    }

    /** The blocks of a blueprint for its library picture, asked for the first time it is shown. */
    private @Nullable Blueprint blueprintForThumbnail(String id) {
        Blueprint found = blueprints.get(id);
        if (found == null && requested.add(id)) {
            Network.sendToServer(new BlueprintRequest(id));
        }
        return found;
    }

    /** Server data arrived; only matters while the planning screen is open. */
    public static void receive(WorkOrdersResponse response) {
        if (Minecraft.getInstance().gui.screen() instanceof WorkPlanningScreen screen) {
            screen.setData(response);
        }
    }

    /** The blocks of a blueprint arrived for the preview. */
    public static void receiveBlueprint(Blueprint blueprint) {
        if (Minecraft.getInstance().gui.screen() instanceof WorkPlanningScreen screen) {
            screen.blueprints.put(blueprint.id().toString(), blueprint);
            screen.requested.remove(blueprint.id().toString());
            screen.preview = null;
        }
    }

    private void setData(WorkOrdersResponse response) {
        boolean listsChanged = data == null || data.orders().size() != response.orders().size()
                               || data.villageId() != response.villageId()
                               || data.canManageBlueprints() != response.canManageBlueprints()
                               || !data.sections().equals(response.sections());
        if (data != null) {
            // a blueprint that was captured again must be fetched again
            for (WorkOrdersResponse.BlueprintInfo info : data.blueprints()) {
                if (response.blueprints().stream().noneMatch(other -> other.id().equals(info.id()) && other.sizeX() == info.sizeX()
                                                                       && other.sizeY() == info.sizeY() && other.sizeZ() == info.sizeZ()
                                                                       && other.blocks() == info.blocks())) {
                    blueprints.remove(info.id());
                }
            }
        }
        data = response;
        browser.setData(response);
        catalog.refresh();
        if (blueprintId != null && response.blueprints().stream().noneMatch(info -> info.id().equals(blueprintId))) {
            blueprintId = null;
        }
        if (blueprintId == null) {
            response.blueprints().stream().filter(info -> !info.hidden()).findFirst().ifPresent(info -> selectBlueprint(info.id()));
        }
        preview = null;
        if (listsChanged || showOrders) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft.player;
        if (camera == null && player != null) {
            camera = new PlanningCamera(minecraft, player);
            camera.activate();
            requestData();
        }
        buildToolbar();
        buildTopButtons();
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            browser.setBounds(6, LIST_TOP, materialsTop() - 4 - LIST_TOP);
            addRenderableWidget(browser.createSearch(font));
        }
        if (showsCatalog()) {
            catalog.setBounds(width - BlockCatalog.WIDTH - 6, RIGHT_PANEL_TOP, bottomUiTop() - RIGHT_PANEL_TOP - 6);
            addRenderableWidget(catalog.createSearch(font));
        }
        if (showOrders) {
            buildOrdersPanel();
        }
        if (naming) {
            buildNamePrompt();
        }
        if (sectionNaming) {
            buildSectionPrompt();
        }
    }

    @Override
    public void removed() {
        if (camera != null) {
            camera.deactivate();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // the village keeps working while it is planned
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // no blur or darkening, the world is what we look at
    }

    @Override
    public void onClose() {
        if (!draft.isEmpty() && closeArmedTicks <= 0) {
            closeArmedTicks = STATUS_TICKS;
            setStatus(Component.translatable("gui.workorder.draft.leave").withStyle(ChatFormatting.GOLD));
            return;
        }
        super.onClose();
    }

    // ---------------------------------------------------------------- widgets

    private boolean showsCatalog() {
        return sectionNaming || selection.getTool() == PlanningTool.BUILD && !showOrders;
    }

    private void buildToolbar() {
        PlanningTool tool = selection.getTool();
        int y = height - TOOLBAR_Y_OFFSET;
        int total = PlanningTool.values().length * (TOOL_WIDTH + 4) - 4;
        int x = Math.max(4, (width - total) / 2);
        for (PlanningTool candidate : PlanningTool.values()) {
            String key = "gui.workorder." + candidate.name().toLowerCase(java.util.Locale.ROOT);
            addRenderableWidget(new IconButtonWidget(x, y, TOOL_WIDTH, 20, iconOf(candidate), Component.translatable(key),
                    b -> setTool(candidate), Component.translatable(key + ".tooltip")).highlighted(tool == candidate));
            x += TOOL_WIDTH + 4;
        }

        // second row: what the picked tool works with
        int rowY = y - SECOND_ROW_OFFSET;
        switch (tool) {
            case DIG -> addRenderableWidget(new IconButtonWidget((width - 120) / 2, rowY, 120, 20, new ItemStack(Items.TNT),
                    Component.translatable("gui.workorder.demolish"), b -> {
                demolish = !demolish;
                rebuildWidgets();
            }, Component.translatable("gui.workorder.demolish.tooltip")).highlighted(demolish));
            case BUILD -> {
                PlanningWands.Shape[] shapes = PlanningWands.Shape.values();
                int rowX = (width - shapes.length * (SHAPE_WIDTH + 2)) / 2;
                for (PlanningWands.Shape candidate : shapes) {
                    String key = "gui.workorder.shape." + candidate.name().toLowerCase(java.util.Locale.ROOT);
                    addRenderableWidget(new IconButtonWidget(rowX, rowY, SHAPE_WIDTH, 20, new ItemStack(candidate.icon()),
                            Component.translatable(key), b -> setShape(candidate), Component.translatable(key + ".tooltip"))
                            .highlighted(candidate == shape));
                    rowX += SHAPE_WIDTH + 2;
                }
            }
            case BLUEPRINT -> addRenderableWidget(new IconButtonWidget((width - 100) / 2, rowY, 100, 20, new ItemStack(Items.COMPASS),
                    Component.translatable("gui.workorder.rotate.button", rotation * 90), b -> rotate(),
                    Component.translatable("gui.workorder.rotate")));
            case CAPTURE -> {
                if (captureBox != null && !naming) {
                    addRenderableWidget(new IconButtonWidget(width / 2 - 104, rowY, 100, 20, new ItemStack(Items.NAME_TAG),
                            Component.translatable("gui.workorder.capture.save_box"), b -> startNaming(), null));
                    addRenderableWidget(new IconButtonWidget(width / 2 + 4, rowY, 100, 20, new ItemStack(Items.BARRIER),
                            Component.translatable("gui.workorder.discard"), b -> discardCapture(), null));
                }
            }
        }
    }

    private void buildTopButtons() {
        int doneX = width - 6 - 70;
        addRenderableWidget(new IconButtonWidget(doneX, TOP_ROW_Y, 70, 20, new ItemStack(Items.OAK_DOOR),
                Component.translatable("gui.workorder.done"), b -> onClose(), Component.translatable("gui.workorder.done.tooltip")));
        int orderCount = data == null ? 0 : data.orders().size();
        int ordersX = doneX - 4 - 92;
        addRenderableWidget(new IconButtonWidget(ordersX, TOP_ROW_Y, 92, 20, new ItemStack(Items.WRITABLE_BOOK),
                Component.translatable("gui.workorder.orders", orderCount), b -> toggleOrders(),
                Component.translatable("gui.workorder.orders.tooltip")).highlighted(showOrders));
        addRenderableWidget(new IconButtonWidget(ordersX - 24, TOP_ROW_Y, 20, 20, new ItemStack(Items.KNOWLEDGE_BOOK),
                Component.empty(), b -> toggleHelp(), Component.translatable("gui.workorder.help.toggle")).highlighted(showHelp));

        if (!draft.isEmpty() || !stroke.isEmpty()) {
            long blocks = draft.totalBlocks() + PlanningWands.extrude(stroke, strokeHeight, strokeTop, strokeLevel).size();
            addRenderableWidget(new IconButtonWidget(width - 6 - 84, DRAFT_ROW_Y, 84, 20, new ItemStack(Items.BARRIER),
                    Component.translatable("gui.workorder.discard"), b -> discardDraft(), Component.translatable("gui.workorder.draft.discard")));
            addRenderableWidget(new IconButtonWidget(width - 6 - 84 - 4 - 150, DRAFT_ROW_Y, 150, 20, new ItemStack(Items.WRITABLE_BOOK),
                    Component.translatable("gui.workorder.draft.confirm", blocks), b -> commit(),
                    Component.translatable("gui.workorder.draft.confirm.tooltip", draft.digs().size(), draft.pieces().size())))
                    .highlighted(true);
        }
    }

    private static ItemStack iconOf(PlanningTool tool) {
        return new ItemStack(switch (tool) {
            case DIG -> Items.IRON_PICKAXE;
            case BUILD -> Items.BRICKS;
            case BLUEPRINT -> Items.MAP;
            case CAPTURE -> Items.PAINTING;
        });
    }

    /** Lowest y the floating texts and panels may use above the bottom rows. */
    private int bottomUiTop() {
        boolean secondRow = switch (selection.getTool()) {
            case DIG, BUILD, BLUEPRINT -> true;
            case CAPTURE -> captureBox != null && !naming;
        };
        return height - TOOLBAR_Y_OFFSET - (secondRow ? SECOND_ROW_OFFSET : 0);
    }

    /** The material grid sits at the bottom of the left side, below the blueprint library. */
    private int materialsTop() {
        return bottomUiTop() - 6 - MaterialGrid.HEIGHT;
    }

    private boolean showsMaterials() {
        return switch (selection.getTool()) {
            case BLUEPRINT -> true;
            case BUILD -> !draft.pieces().isEmpty() || !stroke.isEmpty();
            default -> false;
        };
    }

    /** What the blueprint at the cursor takes, or with the build tool everything planned so far. */
    private Map<Item, Integer> materialsNeeded() {
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            Blueprint blueprint = preview != null ? preview.shape() : blueprintId == null ? null : blueprints.get(blueprintId);
            return blueprint == null ? Map.of() : blueprint.materials().items();
        }
        List<BlockState> states = new ArrayList<>();
        for (PlanningDraft.Piece piece : draft.pieces()) {
            states.addAll(piece.placed().values());
        }
        states.addAll(PlanningWands.extrude(stroke, strokeHeight, strokeTop, strokeLevel).values());
        return Blueprint.materials(states).items();
    }

    /** Bundled blueprints have translated names, captured ones keep the name they were given. */
    static Component blueprintName(WorkOrdersResponse.BlueprintInfo info) {
        return blueprintName(Identifier.tryParse(info.id()), info.name());
    }

    static Component blueprintName(@Nullable Identifier id, String fallback) {
        if (id == null) {
            return Component.literal(fallback);
        }
        return Component.translatableWithFallback("blueprint.name." + id.getPath().replace('/', '.'), fallback);
    }

    private void buildOrdersPanel() {
        if (data == null) {
            return;
        }
        int x = width - PANEL_WIDTH - 6;
        int y = RIGHT_PANEL_TOP + ORDERS_FIRST_ROW_OFFSET;
        boolean canManage = data.getAccess().allowed();
        for (WorkOrdersResponse.OrderInfo order : data.orders().subList(0, visibleOrderRows())) {
            int buttonY = y + 25;
            addRenderableWidget(new IconButtonWidget(x, buttonY, 56, 18, new ItemStack(Items.ENDER_EYE),
                    Component.translatable("gui.workorder.focus.button"), b -> focus(order.area()),
                    Component.translatable("gui.workorder.focus")));
            IconButtonWidget pause = addRenderableWidget(new IconButtonWidget(x + 58, buttonY, 64, 18,
                    new ItemStack(order.paused() ? Items.RABBIT_FOOT : Items.CLOCK),
                    Component.translatable(order.paused() ? "gui.workorder.resume" : "gui.workorder.pause"),
                    b -> manage(order, order.paused() ? ManageWorkOrderMessage.RESUME : ManageWorkOrderMessage.PAUSE), null));
            IconButtonWidget cancel = addRenderableWidget(new IconButtonWidget(x + 124, buttonY, 70, 18, new ItemStack(Items.BARRIER),
                    Component.translatable("gui.workorder.cancel"), b -> manage(order, ManageWorkOrderMessage.CANCEL), null));
            pause.active = canManage;
            cancel.active = canManage;
            y += ORDER_ROW_HEIGHT;
        }
    }

    /** Asks for a name and a section once the capture box is right. */
    private void buildNamePrompt() {
        int boxWidth = 170;
        int total = boxWidth + 4 + 120 + 4 + 66 + 4 + 70;
        int x = (width - total) / 2;
        int y = height / 2 + 30;
        String previous = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(font, x, y, boxWidth, 20, Component.translatable("gui.workorder.capture.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(previous);
        nameBox.setHint(Component.translatable("gui.workorder.capture.name"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        x += boxWidth + 4;
        WorkOrdersResponse.SectionInfo section = data == null ? null
                : data.sections().stream().filter(candidate -> candidate.id().equals(captureSection)).findFirst().orElse(null);
        addRenderableWidget(new IconButtonWidget(x, y, 120, 20,
                section == null ? new ItemStack(Items.NETHER_STAR) : BlueprintBrowser.iconOf(section.icon()),
                section == null ? Component.translatable("gui.workorder.section.none") : BlueprintBrowser.sectionName(section),
                b -> cycleCaptureSection(), Component.translatable("gui.workorder.section.pick")));
        x += 124;
        addRenderableWidget(new IconButtonWidget(x, y, 66, 20, new ItemStack(Items.NAME_TAG),
                Component.translatable("gui.workorder.capture.save"), b -> saveCapture(), null));
        addRenderableWidget(new IconButtonWidget(x + 70, y, 70, 20, new ItemStack(Items.BARRIER),
                Component.translatable("gui.cancel"), b -> cancelNaming(), null));
    }

    /** A new blueprint section: a name, and an icon picked in the block catalog on the right. */
    private void buildSectionPrompt() {
        int boxWidth = 150;
        int x = (width - boxWidth - 4 - 66 - 4 - 70) / 2;
        int y = height / 2 + 30;
        String previous = sectionBox == null ? "" : sectionBox.getValue();
        sectionBox = new EditBox(font, x, y, boxWidth, 20, Component.translatable("gui.workorder.section.name"));
        sectionBox.setMaxLength(32);
        sectionBox.setValue(previous);
        sectionBox.setHint(Component.translatable("gui.workorder.section.name"));
        addRenderableWidget(sectionBox);
        setInitialFocus(sectionBox);
        addRenderableWidget(new IconButtonWidget(x + boxWidth + 4, y, 66, 20,
                sectionIcon == null ? new ItemStack(Items.CHEST) : new ItemStack(sectionIcon),
                Component.translatable("gui.workorder.section.create"), b -> saveSection(), null));
        addRenderableWidget(new IconButtonWidget(x + boxWidth + 74, y, 70, 20, new ItemStack(Items.BARRIER),
                Component.translatable("gui.cancel"), b -> cancelSectionNaming(), null));
    }

    private void setTool(PlanningTool tool) {
        cancelNaming();
        cancelSectionNaming();
        PlanningTool previous = selection.getTool();
        if (previous == PlanningTool.DIG && tool != PlanningTool.DIG && !draft.digs().isEmpty()) {
            // leaving the dig tool drops the areas that were not confirmed
            draft.clearDigs();
            setStatus(Component.translatable("gui.workorder.draft.digs_dropped").withStyle(ChatFormatting.GRAY));
        }
        if (previous == PlanningTool.BUILD && tool != PlanningTool.BUILD) {
            finishStroke();
        }
        if (previous == PlanningTool.CAPTURE && tool != PlanningTool.CAPTURE) {
            captureBox = null;
        }
        dropHeld();
        resetShapeState();
        selection.setTool(tool);
        preview = null;
        if (tool == PlanningTool.BLUEPRINT && blueprintId != null) {
            selectBlueprint(blueprintId);
        }
        rebuildWidgets();
    }

    private void setShape(PlanningWands.Shape shape) {
        finishStroke();
        resetShapeState();
        selection.clear();
        this.shape = shape;
        rebuildWidgets();
    }

    /** Drops what a build shape was in the middle of. */
    private void resetShapeState() {
        lineStart = null;
        lineState = null;
        boxState = null;
        copySource = null;
        copyArea = null;
        copyPreview = null;
        repeatRise = 0;
        painting = false;
        erasing = false;
    }

    private void selectBlueprint(String id) {
        blueprintId = id;
        preview = null;
        if (!blueprints.containsKey(id) && requested.add(id)) {
            Network.sendToServer(new BlueprintRequest(id));
        }
    }

    private void rotate() {
        if (heldPiece != null) {
            heldPiece.moveTo(heldPiece.origin(), heldPiece.rotation() + 1);
            return;
        }
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            rotation = (rotation + 1) % 4;
            preview = null;
            rebuildWidgets();
        } else if (selection.getTool() == PlanningTool.BUILD) {
            turns = (turns + 1) % 4;
            setStatus(Component.translatable("gui.workorder.turned", turns * 90).withStyle(ChatFormatting.GRAY));
        }
    }

    private void toggleOrders() {
        showOrders = !showOrders;
        rebuildWidgets();
    }

    private void toggleHelp() {
        showHelp = !showHelp;
        PlanningPrefs.setBoolean(HELP_PREF, showHelp);
        rebuildWidgets();
    }

    private void pickFromCatalog(Block picked) {
        if (sectionNaming) {
            sectionIcon = picked;
            rebuildWidgets();
            return;
        }
        block = picked;
        BlockCatalog.noteUsed(picked);
    }

    /** The village tab: blocks the player carries and blocks in the village storage, most stored first. */
    private List<Block> villageBlocks() {
        Set<Block> blocks = new LinkedHashSet<>();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).getItem() instanceof BlockItem item) {
                    blocks.add(item.getBlock());
                }
            }
        }
        if (data != null) {
            data.storage().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .map(entry -> BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(entry.getKey())))
                    .flatMap(Optional::stream)
                    .filter(item -> item instanceof BlockItem)
                    .map(item -> ((BlockItem) item).getBlock())
                    .forEach(blocks::add);
        }
        return new ArrayList<>(blocks);
    }

    private int stored(Item item) {
        if (data == null) {
            return 0;
        }
        return data.stored(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    /** Materials do not matter: the Villager Editor, or creative where everything is built at once. */
    private boolean isFree() {
        return data != null && data.getAccess() == WorkOrderPermissions.Access.EDITOR || isCreative();
    }

    private boolean isCreative() {
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }

    // ---------------------------------------------------------------- server

    private void requestData() {
        BlockPos focus = camera != null ? BlockPos.containing(camera.position()) : minecraft.player.blockPosition();
        Network.sendToServer(new WorkOrdersRequest(focus));
    }

    private void manage(WorkOrdersResponse.OrderInfo order, int action) {
        if (data != null) {
            Network.sendToServer(new ManageWorkOrderMessage(data.villageId(), order.id(), action));
        }
    }

    /** Sends the whole draft; the server turns it into work orders (or builds it at once in creative). */
    private void commit() {
        finishStroke();
        dropHeld();
        if (draft.isEmpty()) {
            setStatus(Component.translatable("gui.workorder.draft.empty").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!isCreative()) {
            if (data == null || !data.hasVillage()) {
                setStatus(Component.translatable("gui.workorder.no_village").withStyle(ChatFormatting.YELLOW));
                return;
            }
            if (!data.getAccess().allowed()) {
                setStatus(Component.translatable("gui.workorder.access.denied", Component.translatable(data.minRank())).withStyle(ChatFormatting.RED));
                return;
            }
        }
        Network.sendToServer(draft.toMessage());
        draft.clear();
        setStatus(Component.translatable("gui.workorder.draft.sent").withStyle(ChatFormatting.GREEN));
        rebuildWidgets();
    }

    private void discardDraft() {
        draft.clear();
        stroke.clear();
        strokeColumns.clear();
        strokeTop = null;
        strokeHeight = 1;
        heldPiece = null;
        setStatus(Component.translatable("gui.workorder.draft.discarded").withStyle(ChatFormatting.GRAY));
        rebuildWidgets();
    }

    private void startNaming() {
        if (captureBox == null) {
            return;
        }
        naming = true;
        captureSection = browser.currentSection().isEmpty() ? BlueprintCatalog.DEFAULT_SECTION : browser.currentSection();
        rebuildWidgets();
    }

    private void cycleCaptureSection() {
        if (data == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        data.sections().forEach(section -> options.add(section.id()));
        options.add("");
        int index = options.indexOf(captureSection);
        captureSection = options.get((index + 1) % options.size());
        rebuildWidgets();
    }

    private void saveCapture() {
        if (!naming || captureBox == null || nameBox == null) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            setStatus(Component.translatable("gui.workorder.capture.name_missing").withStyle(ChatFormatting.RED));
            return;
        }
        Network.sendToServer(new CaptureBlueprintMessage(
                new BlockPos(captureBox.minX(), captureBox.minY(), captureBox.minZ()),
                new BlockPos(captureBox.maxX(), captureBox.maxY(), captureBox.maxZ()),
                name, captureSection));
        nameBox.setValue("");
        naming = false;
        captureBox = null;
        rebuildWidgets();
    }

    private void cancelNaming() {
        if (naming) {
            naming = false;
            rebuildWidgets();
        }
    }

    private void discardCapture() {
        captureBox = null;
        naming = false;
        rebuildWidgets();
    }

    private void startSectionNaming() {
        sectionNaming = true;
        sectionIcon = null;
        showOrders = false;
        rebuildWidgets();
    }

    private void saveSection() {
        if (sectionBox == null) {
            return;
        }
        String name = sectionBox.getValue().trim();
        if (BlueprintLibrary.slug(name).isEmpty()) {
            setStatus(Component.translatable("gui.workorder.section.name_missing").withStyle(ChatFormatting.RED));
            return;
        }
        String icon = BuiltInRegistries.ITEM.getKey(sectionIcon == null ? Items.CHEST : sectionIcon.asItem()).toString();
        Network.sendToServer(BlueprintCatalogMessage.createSection(name, icon));
        sectionBox.setValue("");
        cancelSectionNaming();
    }

    private void cancelSectionNaming() {
        if (sectionNaming) {
            sectionNaming = false;
            rebuildWidgets();
        }
    }

    private void setStatus(Component message) {
        status = message;
        statusTicks = STATUS_TICKS;
    }

    // ---------------------------------------------------------------- the draft

    private void addPiece(Map<BlockPos, BlockState> blocks, Component label) {
        if (blocks.isEmpty()) {
            return;
        }
        if (blocks.size() > maxPieceBlocks()) {
            setStatus(Component.translatable("gui.workorder.too_big", maxPieceBlocks()).withStyle(ChatFormatting.RED));
            return;
        }
        draft.addPiece(draft.pieceOf(PlanningWands.completeHalves(blocks), label));
        rebuildWidgets();
    }

    private int maxPieceBlocks() {
        int volume = data != null && data.maxVolume() > 0 ? data.maxVolume() : 4096;
        return volume * 4;
    }

    /** The brush stroke so far becomes a piece of the draft, raised as high as it was extruded. */
    private void finishStroke() {
        if (stroke.isEmpty()) {
            return;
        }
        Map<BlockPos, BlockState> blocks = PlanningWands.extrude(stroke, strokeHeight, strokeTop, strokeLevel);
        stroke.clear();
        strokeColumns.clear();
        strokeTop = null;
        strokeHeight = 1;
        addPiece(blocks, Component.translatable("gui.workorder.shape.brush"));
    }

    private void dropHeld() {
        heldPiece = null;
        heldLift = 0;
    }

    private void grab(PlanningDraft.Piece piece, BlockPos at) {
        heldPiece = piece;
        heldOffset = piece.origin().subtract(at);
        heldLift = 0;
        setStatus(Component.translatable("gui.workorder.piece.picked", piece.label()).withStyle(ChatFormatting.AQUA));
    }

    private Component blockName() {
        return block == null ? Component.literal("?") : block.getName();
    }

    // ---------------------------------------------------------------- camera and cursor

    private void focus(WorkArea area) {
        if (camera == null) {
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(area.center());
        Vec3 from = camera.position();
        // keep the current view angle, just move so the order is in front of the camera
        Vec3 forward = PlanningCamera.forward(minecraft.gameRenderer.mainCamera().yRot());
        Vec3 desired = target.add(forward.scale(-8.0)).add(0, 9.0, 0);
        camera.moveBy(desired.x - from.x, desired.y - from.y, desired.z - from.z);
    }

    /** The ray from the camera through the mouse cursor. */
    private Vec3[] cursorRay() {
        Camera view = minecraft.gameRenderer.mainCamera();
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        float[] ndc = PlanningMath.toNdc(mouseX, mouseY, minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight());
        Vec3 direction = view.getNearPlane(view.getFov()).getPointOnPlane(ndc[0], ndc[1]).normalize();
        return new Vec3[]{view.position(), direction};
    }

    private boolean busyTyping() {
        return naming || sectionNaming || catalog.isSearchFocused() || browser.isSearchFocused();
    }

    private void updateCursor(ClientLevel level) {
        hoveredPiece = null;
        captureFace = null;
        if (naming) {
            hovered = null;
            return;
        }
        Vec3[] ray = cursorRay();
        Vec3 origin = ray[0];
        Vec3 direction = ray[1];

        hit = level.clip(new ClipContext(origin, origin.add(direction.scale(PICK_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        double worldDistance = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(origin) : PICK_RANGE;
        if (!draft.pieces().isEmpty() && heldPiece == null) {
            hoveredPiece = PlanningMath.march(origin, direction, worldDistance + 0.5, pos -> draft.pieceAt(pos) != null)
                    .map(draft::pieceAt)
                    .orElse(null);
        }
        if (captureBox != null) {
            captureFace = PlanningMath.enteredFace(origin, direction, WorkOrderOverlay.box(captureBox), PICK_RANGE).orElse(null);
        }

        PlanningTool tool = selection.getTool();
        BlockPos anchor = selection.getAnchor();
        if (anchor != null) {
            // while dragging, follow the cursor on the selection's own layer
            double planeY = targetsBlock() ? anchor.getY() + 1.0 : anchor.getY();
            Optional<Vec3> onPlane = PlanningMath.intersectHorizontal(origin, direction, planeY, PICK_RANGE);
            if (onPlane.isPresent()) {
                selection.update(BlockPos.containing(onPlane.get().x, anchor.getY(), onPlane.get().z));
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                selection.update(hit.getBlockPos());
            }
            hovered = null;
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            hovered = targetsBlock() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        } else {
            hovered = null;
        }

        if (tool == PlanningTool.BUILD && block != null && hit.getType() == HitResult.Type.BLOCK && minecraft.player != null) {
            Camera view = minecraft.gameRenderer.mainCamera();
            placing = PlanningPlacement.place(block, minecraft.player, hit, view.yRot(), view.xRot(), turns, flipped);
        } else {
            placing = block == null ? null : PlanningPlacement.adjust(block.defaultBlockState(), turns, flipped);
        }
    }

    /** Whether the cursor picks the block itself rather than the free spot on its face. */
    private boolean targetsBlock() {
        PlanningTool tool = selection.getTool();
        return tool == PlanningTool.BUILD ? shape.copies() : tool.targetsBlock();
    }

    /** Where the cursor ray crosses the horizontal plane at {@code y}, as a block on that layer. */
    private Optional<BlockPos> onLayer(int y, double planeY) {
        Vec3[] ray = cursorRay();
        return PlanningMath.intersectHorizontal(ray[0], ray[1], planeY, PICK_RANGE)
                .map(point -> BlockPos.containing(point.x, y, point.z));
    }

    /** Where the selected blueprint would go at the cursor; rebuilt only when cursor, rotation or blueprint change. */
    private PlanningDraft.@Nullable Piece updatePreview() {
        if (selection.getTool() != PlanningTool.BLUEPRINT || hovered == null || blueprintId == null || heldPiece != null || hoveredPiece != null) {
            return null;
        }
        Blueprint blueprint = blueprints.get(blueprintId);
        if (blueprint == null) {
            return null;
        }
        BlockPos origin = hovered.above(blueprintLift);
        if (preview != null && preview.shape() == blueprint && preview.origin().equals(origin) && preview.rotation() == rotation) {
            return preview;
        }
        Component name = data == null ? Component.literal(blueprint.name()) : data.blueprints().stream()
                .filter(info -> info.id().equals(blueprintId)).findFirst().map(WorkPlanningScreen::blueprintName)
                .orElse(Component.literal(blueprint.name()));
        return new PlanningDraft.Piece(blueprint, true, name, origin, rotation);
    }

    /**
     * Paints the brush on the ground under the cursor, filling the gap from the last painted spot. Every column of the
     * stroke sits on the ground where it is, so a wall later raised from it has no gaps where the ground dips.
     */
    private void paint(boolean erase) {
        ClientLevel level = minecraft.level;
        if (placing == null || level == null || hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos to = groundAt(level, hit.getBlockPos().relative(hit.getDirection()));
        BlockPos from = lastPainted == null ? to : lastPainted;
        int radius = brushSize / 2;
        for (BlockPos center : PlanningWands.line(from, to, placing).keySet()) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long column = column(center.getX() + dx, center.getZ() + dz);
                    if (erase) {
                        BlockPos removed = strokeColumns.remove(column);
                        if (removed != null) {
                            stroke.remove(removed);
                        }
                    } else if (!strokeColumns.containsKey(column) && stroke.size() < maxPieceBlocks()) {
                        BlockPos base = groundAt(level, center.offset(dx, 0, dz));
                        strokeColumns.put(column, base);
                        stroke.put(base, placing);
                    }
                }
            }
        }
        lastPainted = to;
    }

    /** Takes the planned blocks at the cursor out of the draft: the one under it, or a cube as wide as the brush. */
    private void erase() {
        eraseTarget().ifPresent(center -> {
            int radius = brushSize / 2;
            Set<BlockPos> cube = new java.util.HashSet<>();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
                cube.add(pos.immutable());
            }
            draft.erase(cube);
        });
    }

    /** The first planned block along the cursor ray, in front of the world's blocks. */
    private Optional<BlockPos> eraseTarget() {
        if (draft.pieces().isEmpty()) {
            return Optional.empty();
        }
        Vec3[] ray = cursorRay();
        double reach = hit != null && hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(ray[0]) + 0.5 : PICK_RANGE;
        return PlanningMath.march(ray[0], ray[1], reach, pos -> draft.pieceAt(pos) != null);
    }

    private static long column(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    /** The lowest free spot of the column at {@code start}: down through air, grass and leaves, up out of solid ground. */
    private static BlockPos groundAt(ClientLevel level, BlockPos start) {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < 48 && pos.getY() > level.getMinY() && isPassable(level.getBlockState(pos.below())); i++) {
            pos.move(Direction.DOWN);
        }
        for (int i = 0; i < 48 && !isPassable(level.getBlockState(pos)); i++) {
            pos.move(Direction.UP);
        }
        return pos.immutable();
    }

    private static boolean isPassable(BlockState state) {
        return state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /** Repeat and mirror: where the copies would go for the cursor. */
    private void updateCopyPreview() {
        copyPreview = null;
        if (copySource == null || copyArea == null) {
            return;
        }
        double planeY = copyArea.minY();
        Optional<BlockPos> at = onLayer(copyArea.minY(), planeY);
        if (at.isEmpty()) {
            return;
        }
        BlockPos center = copyArea.center();
        int dx = at.get().getX() - center.getX();
        int dz = at.get().getZ() - center.getZ();
        Direction direction = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        if (shape == PlanningWands.Shape.MIRROR) {
            copyPreview = PlanningWands.mirror(copySource, copyArea, direction);
            return;
        }
        int length = direction.getAxis() == Direction.Axis.X ? copyArea.sizeX() : copyArea.sizeZ();
        int distance = Math.abs(direction.getAxis() == Direction.Axis.X ? dx : dz);
        int copies = Math.clamp((distance + length / 2) / Math.max(1, length), 1, PlanningWands.MAX_COPIES);
        copyPreview = PlanningWands.repeat(copySource, copyArea, direction, copies, repeatRise);
    }

    /** Blocks touching the capture box from outside (not from below, that is the ground). */
    private List<BlockPos> findTouching(ClientLevel level, WorkArea box) {
        List<BlockPos> found = new ArrayList<>();
        WorkArea outer = new WorkArea(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        for (BlockPos pos : BlockPos.betweenClosed(outer.minX(), outer.minY(), outer.minZ(), outer.maxX(), outer.maxY(), outer.maxZ())) {
            if (box.contains(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
                found.add(pos.immutable());
                if (found.size() >= MAX_TOUCHING) {
                    break;
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- tick and drawing

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !player.isAlive() || camera == null) {
            super.onClose();
            return;
        }
        if (player.hurtTime > 0) {
            player.sendSystemMessage(Component.translatable("gui.workorder.interrupted").withStyle(ChatFormatting.RED));
            super.onClose();
            return;
        }

        if (!busyTyping()) {
            int forward = (held.contains(Movement.FORWARD) ? 1 : 0) - (held.contains(Movement.BACK) ? 1 : 0);
            int strafe = (held.contains(Movement.LEFT) ? 1 : 0) - (held.contains(Movement.RIGHT) ? 1 : 0);
            int vertical = (held.contains(Movement.UP) ? 1 : 0) - (held.contains(Movement.DOWN) ? 1 : 0);
            camera.tick(forward, strafe, vertical, held.contains(Movement.FAST));
        } else {
            camera.tick(0, 0, 0, false);
        }

        updateCursor(level);
        if (painting) {
            paint(isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT));
        }
        if (heldPiece != null && hovered != null) {
            heldPiece.moveTo(hovered.offset(heldOffset).above(heldLift), heldPiece.rotation());
        }
        preview = updatePreview();
        updateCopyPreview();
        touching = captureBox != null && ticks % 5 == 0 ? findTouching(level, captureBox) : captureBox == null ? List.of() : touching;
        draw(level);

        if (++ticks % REFRESH_INTERVAL == 0) {
            requestData();
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
        if (closeArmedTicks > 0) {
            closeArmedTicks--;
        }
    }

    private void draw(ClientLevel level) {
        WorkOrderOverlay.beginFrame();
        PlanningTool tool = selection.getTool();
        if (data != null) {
            for (WorkOrdersResponse.OrderInfo order : data.orders()) {
                boolean highlighted = hovered != null && order.area().contains(hovered);
                WorkOrderOverlay.drawOrder(order, highlighted);
            }
        }

        // the draft: the piece in hand and the one under the cursor stand out
        for (PlanningDraft.Dig dig : draft.digs()) {
            WorkOrderOverlay.drawDig(dig.area(), dig.demolish(), hovered != null && dig.area().contains(hovered));
        }
        for (PlanningDraft.Piece piece : draft.pieces()) {
            int color = piece == heldPiece ? WorkOrderOverlay.HELD_COLOR
                    : piece == hoveredPiece ? WorkOrderOverlay.HOVER_COLOR_PIECE
                    : piece.isSavedBlueprint() ? WorkOrderOverlay.BLUEPRINT_COLOR : WorkOrderOverlay.DRAFT_COLOR;
            WorkOrderOverlay.drawPiece(level, piece.placed(), piece.area(), color, piece == heldPiece);
        }

        if (captureBox != null) {
            WorkOrderOverlay.drawCaptureBox(captureBox, naming ? null : captureFace, touching);
        }
        if (tool == PlanningTool.BLUEPRINT && preview != null) {
            WorkOrderOverlay.drawBlueprint(level, preview.placed(), preview.area());
            if (blueprintLift < 0 && hovered != null) {
                WorkOrderOverlay.drawGroundLevel(preview.area(), hovered.getY());
            }
        }
        if (tool == PlanningTool.BUILD) {
            drawBuildPreview(level);
        } else if (tool != PlanningTool.BLUEPRINT) {
            selection.area().ifPresent(box -> {
                if (tool == PlanningTool.CAPTURE) {
                    WorkOrderOverlay.drawCapture(box);
                } else if (tool.orderType() != null) {
                    WorkOrderOverlay.drawSelection(level, box, tool.orderType(), null,
                            data == null || data.maxVolume() <= 0 || box.volume() <= data.maxVolume(), demolish);
                }
            });
        }
        if (hovered != null && tool != PlanningTool.BLUEPRINT && heldPiece == null) {
            WorkOrderOverlay.drawHover(hovered, WorkOrderOverlay.colorOf(tool));
        }
    }

    private void drawBuildPreview(ClientLevel level) {
        Map<BlockPos, BlockState> ghost = null;
        Optional<WorkArea> box = selection.area();
        if (shape == PlanningWands.Shape.ERASE) {
            int radius = brushSize / 2;
            eraseTarget().ifPresent(pos -> WorkOrderOverlay.drawDig(WorkArea.of(pos.offset(-radius, -radius, -radius),
                    pos.offset(radius, radius, radius)), false, true));
            return;
        }
        if (box.isPresent()) {
            if (shape.copies()) {
                WorkOrderOverlay.drawCapture(box.get());
            } else if (boxState != null) {
                ghost = shape == PlanningWands.Shape.HOLLOW ? PlanningWands.hollow(box.get(), boxState) : PlanningWands.box(box.get(), boxState);
            }
        } else if (!stroke.isEmpty() || lineStart != null && lineState != null && hovered != null) {
            // the stroke as raised so far, and the line being drawn
            ghost = new LinkedHashMap<>(PlanningWands.extrude(stroke, strokeHeight, strokeTop, strokeLevel));
            if (lineStart != null && lineState != null && hovered != null) {
                ghost.putAll(PlanningWands.line(lineStart, hovered, lineState));
            }
        } else if (copyPreview != null) {
            ghost = copyPreview;
            if (copyArea != null) {
                WorkOrderOverlay.drawCapture(copyArea);
            }
        } else if (placing != null && hovered != null && heldPiece == null && hoveredPiece == null) {
            ghost = PlanningPlacement.withSecondHalf(hovered, placing);
        }
        if (ghost != null && !ghost.isEmpty() && ghost.size() <= maxPieceBlocks()) {
            WorkOrderOverlay.drawPiece(level, ghost, WorkArea.around(ghost.keySet()), WorkOrderOverlay.BUILD_COLOR, true);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int textColor = 0xFFFFFFFF;
        int grey = 0xFFB0B0B0;
        int y = 6;

        // header: village, permission, workers; long lines wrap next to the buttons on the right
        int headerWidth = Math.max(120, width - 300);
        if (data == null) {
            y = wrapped(graphics, Component.translatable("gui.workorder.loading"), 6, y, headerWidth, grey);
        } else if (!data.hasVillage()) {
            y = wrapped(graphics, Component.translatable(isCreative() ? "gui.workorder.creative" : "gui.workorder.no_village")
                    .withStyle(ChatFormatting.YELLOW), 6, y, headerWidth, textColor);
        } else {
            y = wrapped(graphics, Component.translatable("gui.workorder.village", data.villageName()).withStyle(ChatFormatting.GOLD), 6, y, headerWidth, textColor);
            Component access = switch (data.getAccess()) {
                case EDITOR -> Component.translatable("gui.workorder.access.editor").withStyle(ChatFormatting.AQUA);
                case RANK -> Component.translatable("gui.workorder.access.rank").withStyle(ChatFormatting.GREEN);
                case DENIED -> Component.translatable("gui.workorder.access.denied", Component.translatable(data.minRank())).withStyle(ChatFormatting.RED);
            };
            y = wrapped(graphics, access, 6, y, headerWidth, textColor);
            MutableComponent workers = Component.translatable("gui.workorder.workers", data.builders(), data.miners());
            y = wrapped(graphics, data.builders() + data.miners() == 0 ? workers.withStyle(ChatFormatting.YELLOW) : workers, 6, y, headerWidth, grey);
            if (!data.hasStorage() && !isFree()) {
                wrapped(graphics, Component.translatable("gui.workorder.no_storage").withStyle(ChatFormatting.YELLOW), 6, y, headerWidth, textColor);
            }
        }

        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            browser.extract(graphics, font, mouseX, mouseY, blueprintId);
        }
        if (showsMaterials()) {
            MaterialGrid.extract(graphics, font, 6, materialsTop(), BlueprintBrowser.WIDTH, mouseX, mouseY, materialsNeeded(),
                    item -> isFree() ? -1 : stored(item));
        }
        if (showsCatalog()) {
            Block selected = sectionNaming ? sectionIcon : block;
            catalog.extract(graphics, font, mouseX, mouseY, selected,
                    stack -> isFree() || sectionNaming ? -1 : stored(stack.getItem()));
        }

        // controls right above the toolbar, what the selection would do above them
        int infoY = bottomUiTop() - 12;
        if (showHelp) {
            List<List<PlanningHelp.Hint>> rows = hints();
            for (int i = rows.size() - 1; i >= 0; i--) {
                graphics.centeredText(font, PlanningHelp.line(rows.get(i)), width / 2, infoY, grey);
                infoY -= 10;
            }
            infoY -= 3;
        }
        for (Component line : describeSelection()) {
            graphics.centeredText(font, line, width / 2, infoY, textColor);
            infoY -= 11;
        }
        if (status != null && statusTicks > 0) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(status, width - 40);
            for (int i = lines.size() - 1; i >= 0; i--) {
                graphics.centeredText(font, lines.get(i), width / 2, infoY, textColor);
                infoY -= 10;
            }
        }
        if (naming) {
            graphics.centeredText(font, Component.translatable("gui.workorder.capture.prompt"), width / 2, height / 2 + 16, textColor);
        }
        if (sectionNaming) {
            graphics.centeredText(font, Component.translatable("gui.workorder.section.prompt"), width / 2, height / 2 + 16, textColor);
        }

        if (showOrders) {
            extractOrdersPanel(graphics);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** Draws text wrapped to {@code maxWidth}; returns the y below it. */
    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, maxWidth)) {
            graphics.text(font, line, x, y, color);
            y += 10;
        }
        return y + 1;
    }

    /** The help lines for what the player is doing right now, the camera controls last. */
    private List<List<PlanningHelp.Hint>> hints() {
        List<PlanningHelp.Hint> first = new ArrayList<>();
        if (naming || sectionNaming) {
            return List.of(List.of(PlanningHelp.hint("enter", "save"), PlanningHelp.hint("esc", "cancel")));
        }
        PlanningTool tool = selection.getTool();
        if (heldPiece != null) {
            first.add(PlanningHelp.hint("left_click", "drop"));
            first.add(PlanningHelp.hint("r", "turn"));
            first.add(PlanningHelp.hint("scroll", "raise_sink"));
            first.add(PlanningHelp.hint("delete", "remove_piece"));
        } else if (selection.isActive()) {
            first.add(PlanningHelp.hint("mouse", "span"));
            first.add(PlanningHelp.hint("scroll", "stretch"));
            first.add(PlanningHelp.hint("left_click", "set_area"));
            first.add(PlanningHelp.hint(tool == PlanningTool.DIG ? "middle_click" : "esc", "cancel"));
        } else if (tool == PlanningTool.DIG) {
            first.add(PlanningHelp.hint("left_click", "start_area"));
            first.add(PlanningHelp.hint("middle_click", "remove_area"));
        } else if (tool == PlanningTool.BUILD) {
            switch (shape) {
                case BOX, HOLLOW -> first.add(PlanningHelp.hint("left_click", "start_area"));
                case LINE -> {
                    first.add(PlanningHelp.hint("left_click", lineStart == null ? "line_start" : "line_end"));
                    strokeHints(first);
                }
                case BRUSH -> {
                    first.add(PlanningHelp.hint("left_drag", "paint"));
                    first.add(PlanningHelp.hint("alt_drag", "erase"));
                    first.add(PlanningHelp.hint("brackets", "brush_size", brushSize));
                    strokeHints(first);
                }
                case ERASE -> {
                    first.add(PlanningHelp.hint("left_drag", "erase"));
                    first.add(PlanningHelp.hint("brackets", "brush_size", brushSize));
                }
                case REPEAT, MIRROR -> {
                    if (copySource == null) {
                        first.add(PlanningHelp.hint("left_click", "pick_copy"));
                    } else {
                        first.add(PlanningHelp.hint("mouse", shape == PlanningWands.Shape.REPEAT ? "repeat_drag" : "mirror_side"));
                        if (shape == PlanningWands.Shape.REPEAT) {
                            first.add(PlanningHelp.hint("scroll", "rise"));
                        }
                        first.add(PlanningHelp.hint("left_click", "place"));
                        first.add(PlanningHelp.hint("esc", "cancel"));
                    }
                }
            }
            if (shape != PlanningWands.Shape.REPEAT && shape != PlanningWands.Shape.MIRROR && shape != PlanningWands.Shape.ERASE) {
                first.add(PlanningHelp.hint("r", "turn"));
                first.add(PlanningHelp.hint("f", "flip"));
                first.add(PlanningHelp.hint("middle_click", "pick_block"));
            }
        } else if (tool == PlanningTool.BLUEPRINT) {
            first.add(PlanningHelp.hint("left_click", "place"));
            first.add(PlanningHelp.hint("r", "turn"));
            first.add(PlanningHelp.hint("scroll", "raise_sink"));
            first.add(PlanningHelp.hint("left_on_piece", "pick_piece"));
        } else if (captureBox != null) {
            first.add(PlanningHelp.hint("face_scroll", "push_face"));
            first.add(PlanningHelp.hint("enter", "save"));
            first.add(PlanningHelp.hint("esc", "discard"));
        } else {
            first.add(PlanningHelp.hint("left_click", "start_area"));
        }
        if (!draft.isEmpty() && heldPiece == null && !selection.isActive()) {
            first.add(PlanningHelp.hint("enter", "confirm_draft"));
        }
        first.add(PlanningHelp.hint("tab", "orders"));
        return List.of(first, PlanningHelp.movement());
    }

    /** What can be done with a brush stroke or the lines drawn so far, before it becomes a piece. */
    private void strokeHints(List<PlanningHelp.Hint> hints) {
        if (!stroke.isEmpty()) {
            hints.add(PlanningHelp.hint("scroll", "extrude"));
            hints.add(PlanningHelp.hint("t", "top_block"));
            hints.add(PlanningHelp.hint("g", strokeLevel ? "follow_ground" : "level_top"));
            hints.add(PlanningHelp.hint("n", "new_stroke"));
        }
    }

    /** Lines about what the current selection or preview would do, drawn bottom-up. */
    private List<Component> describeSelection() {
        PlanningTool tool = selection.getTool();
        List<Component> lines = new ArrayList<>();
        if (heldPiece != null) {
            WorkArea area = heldPiece.area();
            lines.add(Component.translatable("gui.workorder.piece.held", heldPiece.label(), area.sizeX(), area.sizeY(), area.sizeZ())
                    .withStyle(ChatFormatting.YELLOW));
            return lines;
        }
        if (tool == PlanningTool.BLUEPRINT) {
            return describePreview();
        }
        if (tool == PlanningTool.CAPTURE && captureBox != null) {
            if (!touching.isEmpty()) {
                lines.add(Component.translatable("gui.workorder.capture.touching", touching.size()).withStyle(ChatFormatting.GOLD));
            }
            lines.add(Component.translatable("gui.workorder.capture.size", captureBox.sizeX(), captureBox.sizeY(), captureBox.sizeZ()));
            return lines;
        }
        if (tool == PlanningTool.BUILD) {
            return describeBuild();
        }
        Optional<WorkArea> area = selection.area();
        if (area.isEmpty()) {
            if (tool == PlanningTool.DIG && demolish) {
                lines.add(Component.translatable("gui.workorder.demolish.on").withStyle(ChatFormatting.RED));
            }
            return lines;
        }
        WorkArea box = area.get();
        if (tool == PlanningTool.CAPTURE) {
            lines.add(Component.translatable("gui.workorder.capture.size", box.sizeX(), box.sizeY(), box.sizeZ()));
            return lines;
        }
        boolean tooBig = data != null && data.maxVolume() > 0 && box.volume() > data.maxVolume();
        ClientLevel level = minecraft.level;
        if (demolish && level != null && box.volume() <= 32768) {
            int containers = countFilledContainers(level, box);
            if (containers > 0) {
                lines.add(Component.translatable("gui.workorder.demolish.containers", containers).withStyle(ChatFormatting.RED));
            }
        }
        int changes = level == null || box.volume() > 32768 ? -1 : countDigs(level, box);
        MutableComponent size = Component.translatable("gui.workorder.size", box.sizeX(), box.sizeY(), box.sizeZ(),
                changes >= 0 ? changes : box.volume());
        lines.add(tooBig ? Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED) : size);
        return lines;
    }

    private List<Component> describeBuild() {
        List<Component> lines = new ArrayList<>();
        if (shape == PlanningWands.Shape.ERASE) {
            lines.add(Component.translatable("gui.workorder.erase", brushSize).withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (block == null) {
            lines.add(Component.translatable("gui.workorder.pick_block").withStyle(ChatFormatting.YELLOW));
            return lines;
        }
        Map<BlockPos, BlockState> planned = null;
        Optional<WorkArea> box = selection.area();
        if (box.isPresent() && !shape.copies() && boxState != null) {
            planned = shape == PlanningWands.Shape.HOLLOW ? PlanningWands.hollow(box.get(), boxState) : PlanningWands.box(box.get(), boxState);
            lines.add(Component.translatable("gui.workorder.size", box.get().sizeX(), box.get().sizeY(), box.get().sizeZ(), planned.size()));
        } else if (!stroke.isEmpty()) {
            planned = PlanningWands.extrude(stroke, strokeHeight, strokeTop, strokeLevel);
            lines.add(Component.translatable("gui.workorder.stroke", stroke.size(), strokeHeight, planned.size()));
            lines.add(Component.translatable(strokeLevel ? "gui.workorder.stroke.level" : "gui.workorder.stroke.follow").withStyle(ChatFormatting.GRAY));
            if (strokeTop != null) {
                lines.add(Component.translatable("gui.workorder.stroke.top", strokeTop.getBlock().getName()).withStyle(ChatFormatting.GRAY));
            }
        } else if (copyPreview != null) {
            planned = copyPreview;
            lines.add(Component.translatable(shape == PlanningWands.Shape.REPEAT ? "gui.workorder.repeat" : "gui.workorder.mirror",
                    copyPreview.size(), repeatRise));
        } else if (lineStart != null && hovered != null && lineState != null) {
            planned = PlanningWands.line(lineStart, hovered, lineState);
            lines.add(Component.translatable("gui.workorder.line", planned.size()));
        }
        if (planned != null && planned.size() > maxPieceBlocks()) {
            lines.add(Component.translatable("gui.workorder.too_big", maxPieceBlocks()).withStyle(ChatFormatting.RED));
        }
        if (!shape.copies()) {
            if (isFree()) {
                lines.add(Component.translatable("gui.workorder.material.free", blockName()).withStyle(ChatFormatting.AQUA));
            } else {
                int needed = planned == null ? 1 : planned.size();
                int inStorage = stored(block.asItem());
                lines.add(Component.translatable("gui.workorder.material", blockName(), needed, inStorage)
                        .withStyle(inStorage >= needed ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        return lines;
    }

    private List<Component> describePreview() {
        List<Component> lines = new ArrayList<>();
        if (blueprintId == null) {
            lines.add(Component.translatable(data != null && data.blueprints().isEmpty()
                    ? "gui.workorder.blueprints.empty" : "gui.workorder.blueprint.none").withStyle(ChatFormatting.YELLOW));
            return lines;
        }
        if (!blueprints.containsKey(blueprintId)) {
            lines.add(Component.translatable("gui.workorder.loading").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (preview == null) {
            return lines;
        }
        if (blueprintLift < 0) {
            lines.add(Component.translatable("gui.workorder.blueprint.basement", -blueprintLift).withStyle(ChatFormatting.GREEN));
        } else if (blueprintLift > 0) {
            lines.add(Component.translatable("gui.workorder.blueprint.lift", "+" + blueprintLift).withStyle(ChatFormatting.GRAY));
        }

        // the materials themselves are in the grid on the left
        Blueprint.Materials materials = preview.shape().materials();
        if (!materials.unobtainable().isEmpty()) {
            lines.add(Component.translatable("gui.workorder.blueprint.unobtainable",
                    materials.unobtainable().iterator().next().getName()).withStyle(ChatFormatting.RED));
        }
        int obstructions = countObstructions(preview);
        if (obstructions > 0) {
            lines.add(Component.translatable("gui.workorder.blueprint.obstructions", obstructions).withStyle(ChatFormatting.GOLD));
        }
        WorkArea area = preview.area();
        lines.add(Component.translatable("gui.workorder.blueprint.summary", preview.label(),
                area.sizeX(), area.sizeY(), area.sizeZ(), materials.total()));
        return lines;
    }

    private int countObstructions(PlanningDraft.Piece piece) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return 0;
        }
        int obstructions = 0;
        for (Map.Entry<BlockPos, BlockState> entry : piece.placed().entrySet()) {
            BlockState current = level.getBlockState(entry.getKey());
            if (!current.isAir() && !current.canBeReplaced() && !current.is(entry.getValue().getBlock())) {
                obstructions++;
            }
        }
        return obstructions;
    }

    /** Blocks the dig selection would actually take out. */
    private int countDigs(ClientLevel level, WorkArea area) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (WorkOrderBlocks.needsDigging(level, pos, demolish)) {
                count++;
            }
        }
        return count;
    }

    /** Containers with something inside that demolishing would destroy. */
    private int countFilledContainers(ClientLevel level, WorkArea area) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container && !container.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** How many orders fit into the panel. */
    private int visibleOrderRows() {
        int rows = data == null ? 0 : data.orders().size();
        int room = Math.max(0, (bottomUiTop() - 30 - RIGHT_PANEL_TOP - ORDERS_FIRST_ROW_OFFSET) / ORDER_ROW_HEIGHT);
        return Math.min(rows, room);
    }

    private int ordersPanelBottom() {
        return RIGHT_PANEL_TOP + ORDERS_FIRST_ROW_OFFSET + Math.max(1, visibleOrderRows()) * ORDER_ROW_HEIGHT + 4;
    }

    private void extractOrdersPanel(GuiGraphicsExtractor graphics) {
        int x = width - PANEL_WIDTH - 6;
        graphics.fill(x - 4, RIGHT_PANEL_TOP, x + PANEL_WIDTH + 2, ordersPanelBottom(), 0xA0000000);
        graphics.text(font, Component.translatable("gui.workorder.orders.title"), x, RIGHT_PANEL_TOP + 3, 0xFFFFD27F);
        int firstRow = RIGHT_PANEL_TOP + ORDERS_FIRST_ROW_OFFSET;
        if (data == null || data.orders().isEmpty()) {
            graphics.text(font, Component.translatable("gui.workorder.orders.empty"), x, firstRow + 2, 0xFFB0B0B0);
            return;
        }
        int shown = visibleOrderRows();
        int y = firstRow;
        for (WorkOrdersResponse.OrderInfo order : data.orders().subList(0, shown)) {
            int color = order.paused() ? WorkOrderOverlay.PAUSED_COLOR
                    : order.waiting() ? WorkOrderOverlay.WAITING_COLOR
                    : WorkOrderOverlay.colorOf(order.type());
            graphics.text(font, describe(order), x, y + 2, color);
            Component state = order.paused() ? Component.translatable("gui.workorder.order.paused")
                    : order.waiting() ? Component.translatable("gui.workorder.order.waiting")
                    : Component.translatable("gui.workorder.order.remaining", order.remaining());
            graphics.text(font, state, x, y + 12, 0xFFB0B0B0);
            // progress bar
            graphics.fill(x, y + 21, x + PANEL_WIDTH - 2, y + 23, 0xFF404040);
            graphics.fill(x, y + 21, x + Math.round((PANEL_WIDTH - 2) * order.progress()), y + 23, color);
            y += ORDER_ROW_HEIGHT;
        }
        if (shown < data.orders().size()) {
            graphics.text(font, Component.translatable("gui.workorder.orders.more", data.orders().size() - shown), x, y - 2, 0xFFB0B0B0);
        }
    }

    private Component describe(WorkOrdersResponse.OrderInfo order) {
        int percent = Math.round(order.progress() * 100.0F);
        return switch (order.type()) {
            case DIG -> Component.translatable(order.demolish() ? "gui.workorder.order.demolish" : "gui.workorder.order.dig", order.id(), percent);
            case BLUEPRINT -> {
                if (order.block().isEmpty()) {
                    yield Component.translatable("gui.workorder.order.plan", order.id(), percent);
                }
                Identifier id = Identifier.tryParse(order.block());
                yield Component.translatable("gui.workorder.order.blueprint", order.id(), percent,
                        blueprintName(id, id == null ? order.block() : BlueprintLibrary.displayName(id)));
            }
            case BUILD -> {
                Component name = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(order.block()))
                        .map(Block::getName)
                        .map(Component.class::cast)
                        .orElse(Component.literal(order.block()));
                yield Component.translatable("gui.workorder.order.build", order.id(), percent, name);
            }
        };
    }

    // ---------------------------------------------------------------- input

    private boolean isOverUi(double mouseX, double mouseY) {
        if (mouseY >= bottomUiTop() - 2) {
            return true;
        }
        if (mouseY <= TOP_ROW_Y + 22 && mouseX >= width - 200) {
            return true;
        }
        if ((!draft.isEmpty() || !stroke.isEmpty()) && mouseY <= DRAFT_ROW_Y + 22 && mouseX >= width - 250) {
            return true;
        }
        if (selection.getTool() == PlanningTool.BLUEPRINT && browser.isOver(mouseX, mouseY)) {
            return true;
        }
        if (showsMaterials() && mouseX <= 6 + BlueprintBrowser.WIDTH && mouseY >= materialsTop()) {
            return true;
        }
        if (showsCatalog() && catalog.isOver(mouseX, mouseY)) {
            return true;
        }
        return showOrders && mouseX >= width - PANEL_WIDTH - 10 && mouseY >= RIGHT_PANEL_TOP && mouseY <= ordersPanelBottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean right = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        boolean browsing = selection.getTool() == PlanningTool.BLUEPRINT && (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || right);
        // an open menu takes the click first; otherwise the widgets (like the search boxes) do
        if (browsing && browser.closeMenuOnClick(event.x(), event.y())) {
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (browsing && browser.mouseClicked(event.x(), event.y(), right)) {
            clearFocus();
            return true;
        }
        if (showsCatalog() && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && catalog.mouseClicked(event.x(), event.y())) {
            clearFocus();
            return true;
        }
        clearFocus();
        if (naming || sectionNaming) {
            // clicks outside the prompt do nothing until it is answered
            return true;
        }
        closeArmedTicks = 0;
        if (right) {
            // the right button only ever looks around, so it never gets in the way of a half drawn area
            rotating = true;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && !isOverUi(event.x(), event.y())) {
            if (selection.getTool() == PlanningTool.DIG) {
                cancelOrRemoveDig();
            } else {
                pickBlock();
            }
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || isOverUi(event.x(), event.y())) {
            return false;
        }
        onLeftClick();
        return true;
    }

    /** Digging: the middle button drops the area being drawn, or else takes back the planned area under the cursor. */
    private void cancelOrRemoveDig() {
        if (selection.isActive()) {
            selection.clear();
        } else if (hovered != null && draft.removeDigAt(hovered)) {
            rebuildWidgets();
        }
    }

    private void onLeftClick() {
        PlanningTool tool = selection.getTool();
        if (heldPiece != null) {
            dropHeld();
            return;
        }
        if (hoveredPiece != null && !selection.isActive() && lineStart == null && copySource == null && stroke.isEmpty()
            && (tool == PlanningTool.BLUEPRINT
                || tool == PlanningTool.BUILD && shape != PlanningWands.Shape.BRUSH && shape != PlanningWands.Shape.ERASE)) {
            // a piece of the draft is taken back into the hand
            Vec3[] ray = cursorRay();
            BlockPos at = PlanningMath.march(ray[0], ray[1], PICK_RANGE, pos -> draft.pieceAt(pos) == hoveredPiece).orElse(hoveredPiece.origin());
            grab(hoveredPiece, at);
            return;
        }
        switch (tool) {
            case BLUEPRINT -> placeBlueprint();
            case DIG -> clickArea(area -> {
                if (data != null && data.maxVolume() > 0 && area.volume() > data.maxVolume() && !isCreative()) {
                    setStatus(Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED));
                    return false;
                }
                draft.addDig(area, demolish);
                rebuildWidgets();
                return true;
            });
            case CAPTURE -> clickArea(area -> {
                captureBox = area;
                rebuildWidgets();
                return true;
            });
            case BUILD -> clickBuild();
        }
    }

    /** First click starts a box, the second one hands it to {@code onArea}, which says whether it was taken. */
    private void clickArea(java.util.function.Predicate<WorkArea> onArea) {
        if (selection.isActive()) {
            Optional<WorkArea> area = selection.area();
            if (area.isPresent() && onArea.test(area.get())) {
                selection.clear();
            }
        } else if (hovered != null) {
            selection.start(hovered);
        }
    }

    private void clickBuild() {
        if (block == null && !shape.copies() && shape != PlanningWands.Shape.ERASE) {
            setStatus(Component.translatable("gui.workorder.pick_block").withStyle(ChatFormatting.RED));
            return;
        }
        switch (shape) {
            case BOX, HOLLOW -> {
                if (!selection.isActive()) {
                    boxState = placing;
                }
                clickArea(area -> {
                    if (boxState == null) {
                        return false;
                    }
                    addPiece(shape == PlanningWands.Shape.HOLLOW ? PlanningWands.hollow(area, boxState) : PlanningWands.box(area, boxState),
                            blockName());
                    boxState = null;
                    return true;
                });
            }
            case LINE -> {
                if (hovered == null) {
                    return;
                }
                if (lineStart == null) {
                    lineStart = hovered;
                    lineState = placing;
                } else if (lineState != null) {
                    // The lines join the stroke, so they raise with the scroll wheel like the brush
                    Map<BlockPos, BlockState> segment = PlanningWands.line(lineStart, hovered, lineState);
                    if (stroke.size() + segment.size() > maxPieceBlocks()) {
                        setStatus(Component.translatable("gui.workorder.too_big", maxPieceBlocks()).withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (stroke.isEmpty()) {
                        strokeHeight = 1;
                        strokeTop = null;
                    }
                    segment.forEach((pos, state) -> {
                        stroke.put(pos, state);
                        strokeColumns.putIfAbsent(column(pos.getX(), pos.getZ()), pos);
                    });
                    // a polyline: the next line starts where this one ended
                    lineStart = hovered;
                    rebuildWidgets();
                }
            }
            case ERASE -> {
                erasing = true;
                erase();
                rebuildWidgets();
            }
            case BRUSH -> {
                if (hovered == null) {
                    return;
                }
                if (stroke.isEmpty()) {
                    strokeHeight = 1;
                    strokeTop = null;
                }
                painting = true;
                lastPainted = null;
                paint(isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT));
                rebuildWidgets();
            }
            case REPEAT, MIRROR -> {
                if (copySource != null) {
                    if (copyPreview != null) {
                        addPiece(copyPreview, Component.translatable("gui.workorder.shape." + shape.name().toLowerCase(java.util.Locale.ROOT)));
                    }
                    copySource = null;
                    copyArea = null;
                    copyPreview = null;
                    return;
                }
                ClientLevel level = minecraft.level;
                clickArea(area -> {
                    if (level == null) {
                        return false;
                    }
                    Map<BlockPos, BlockState> source = PlanningWands.copy(level, area, draft::stateAt);
                    if (source.isEmpty()) {
                        setStatus(Component.translatable("gui.workorder.copy.empty").withStyle(ChatFormatting.YELLOW));
                        return true;
                    }
                    copySource = source;
                    copyArea = area;
                    repeatRise = 0;
                    return true;
                });
            }
        }
    }

    private void placeBlueprint() {
        if (blueprintId == null) {
            setStatus(Component.translatable("gui.workorder.blueprint.none").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (preview == null) {
            setStatus(Component.translatable("gui.workorder.blueprint.loading").withStyle(ChatFormatting.YELLOW));
            return;
        }
        draft.addPiece(preview);
        preview = null;
        setStatus(Component.translatable("gui.workorder.draft.added", draft.pieces().getLast().label()).withStyle(ChatFormatting.AQUA));
        rebuildWidgets();
    }

    /** Like the game's pick block: the block under the cursor becomes the one to build with. */
    private void pickBlock() {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        Vec3[] ray = cursorRay();
        BlockHitResult target = minecraft.level.clip(new ClipContext(ray[0], ray[0].add(ray[1].scale(PICK_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (target.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Block picked = minecraft.level.getBlockState(target.getBlockPos()).getBlock();
        if (picked.asItem() == Items.AIR) {
            return;
        }
        block = picked;
        BlockCatalog.noteUsed(picked);
        if (selection.getTool() != PlanningTool.BUILD) {
            setTool(PlanningTool.BUILD);
        }
        setStatus(Component.translatable("gui.workorder.picked", picked.getName()).withStyle(ChatFormatting.GREEN));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rotating = false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && painting) {
            painting = false;
            lastPainted = null;
            rebuildWidgets();
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && erasing) {
            erasing = false;
            rebuildWidgets();
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!Double.isNaN(lastMouseX) && camera != null && !naming && (rotating || isControlDown())) {
            camera.rotate((mouseX - lastMouseX) * LOOK_SENSITIVITY * 2.0, (mouseY - lastMouseY) * LOOK_SENSITIVITY * 2.0);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (painting) {
            paint(isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT));
        }
        if (erasing) {
            erase();
        }
        super.mouseMoved(mouseX, mouseY);
    }

    private boolean isControlDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean isKeyDown(int key) {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow(), key);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selection.getTool() == PlanningTool.BLUEPRINT && browser.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (showsCatalog() && catalog.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (naming || sectionNaming) {
            return true;
        }
        if (!raise(scrollY > 0 ? 1 : -1) && camera != null) {
            camera.moveBy(0, scrollY * SCROLL_CLIMB, 0);
        }
        return true;
    }

    /**
     * A step up or down for whatever is being shaped: the held piece, the selection's height, the blueprint, the
     * captured face, the brush stroke's extrusion, the repeat's rise. Scrolling and Page Up/Down both do this; false
     * when nothing takes it.
     */
    private boolean raise(int step) {
        if (heldPiece != null) {
            heldLift += step;
        } else if (selection.isActive()) {
            selection.adjustExtent(step);
        } else if (selection.getTool() == PlanningTool.BLUEPRINT) {
            liftBlueprint(step);
        } else if (selection.getTool() == PlanningTool.CAPTURE && captureBox != null && captureFace != null) {
            pushFace(captureFace, step);
        } else if (selection.getTool() == PlanningTool.BUILD && !stroke.isEmpty() && !painting) {
            int next = strokeHeight + step;
            if (next == 0) {
                next = step;
            }
            strokeHeight = Math.clamp(next, -MAX_EXTRUDE, MAX_EXTRUDE);
            rebuildWidgets();
        } else if (selection.getTool() == PlanningTool.BUILD && copySource != null && shape == PlanningWands.Shape.REPEAT) {
            repeatRise = Math.clamp(repeatRise + step, -MAX_RISE, MAX_RISE);
        } else {
            return false;
        }
        return true;
    }

    private void liftBlueprint(int step) {
        Blueprint blueprint = blueprintId == null ? null : blueprints.get(blueprintId);
        int deepest = -(blueprint == null ? 0 : blueprint.sizeY()) - MAX_LIFT;
        blueprintLift = Math.clamp(blueprintLift + step, deepest, MAX_LIFT);
    }

    /** Moves one face of the capture box out (positive) or in, never past the opposite face. */
    private void pushFace(Direction face, int step) {
        WorkArea box = captureBox;
        if (box == null) {
            return;
        }
        int minX = box.minX(), minY = box.minY(), minZ = box.minZ(), maxX = box.maxX(), maxY = box.maxY(), maxZ = box.maxZ();
        switch (face) {
            case WEST -> minX = Math.min(maxX, minX - step);
            case EAST -> maxX = Math.max(minX, maxX + step);
            case DOWN -> minY = Math.min(maxY, minY - step);
            case UP -> maxY = Math.max(minY, maxY + step);
            case NORTH -> minZ = Math.min(maxZ, minZ - step);
            case SOUTH -> maxZ = Math.max(minZ, maxZ + step);
        }
        captureBox = new WorkArea(minX, minY, minZ, maxX, maxY, maxZ);
        touching = minecraft.level == null ? List.of() : findTouching(minecraft.level, captureBox);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (naming || sectionNaming) {
            // typing a name: only Enter and Escape mean something here
            if (event.isEscape()) {
                cancelNaming();
                cancelSectionNaming();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (naming) {
                    saveCapture();
                } else {
                    saveSection();
                }
                return true;
            }
            return super.keyPressed(event);
        }
        if (catalog.isSearchFocused() || browser.isSearchFocused()) {
            if (event.isEscape() || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                clearFocus();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.isEscape()) {
            if (escapeStep()) {
                return true;
            }
            onClose();
            return true;
        }
        Movement movement = movementOf(event);
        if (movement != null) {
            held.add(movement);
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (selection.getTool() == PlanningTool.CAPTURE && captureBox != null) {
                    startNaming();
                } else {
                    commit();
                }
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                toggleOrders();
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                toggleHelp();
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                rotate();
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                flipped = !flipped;
                setStatus(Component.translatable(flipped ? "gui.workorder.flipped" : "gui.workorder.unflipped").withStyle(ChatFormatting.GRAY));
                return true;
            }
            case GLFW.GLFW_KEY_T -> {
                if (!stroke.isEmpty() && placing != null) {
                    strokeTop = strokeTop == null ? placing : null;
                }
                return true;
            }
            case GLFW.GLFW_KEY_G -> {
                strokeLevel = !strokeLevel;
                rebuildWidgets();
                return true;
            }
            case GLFW.GLFW_KEY_N -> {
                finishStroke();
                lineStart = null;
                lineState = null;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                // 1, 3 or 5 blocks across
                brushSize = Math.clamp(brushSize + (event.key() == GLFW.GLFW_KEY_RIGHT_BRACKET ? 2 : -2), 1, MAX_BRUSH);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                raise(event.key() == GLFW.GLFW_KEY_PAGE_UP ? 1 : -1);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                PlanningDraft.Piece piece = heldPiece != null ? heldPiece : hoveredPiece;
                if (piece != null) {
                    draft.removePiece(piece);
                    dropHeld();
                    rebuildWidgets();
                }
                return true;
            }
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4 -> {
                setTool(PlanningTool.values()[event.key() - GLFW.GLFW_KEY_1]);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    /** Escape undoes one step of what is going on; false when there was nothing left, so the screen closes. */
    private boolean escapeStep() {
        if (browser.closeMenu()) {
            return true;
        }
        if (heldPiece != null) {
            dropHeld();
            return true;
        }
        if (selection.isActive()) {
            selection.clear();
            boxState = null;
            return true;
        }
        if (lineStart != null || copySource != null) {
            resetShapeState();
            return true;
        }
        if (!stroke.isEmpty()) {
            finishStroke();
            return true;
        }
        if (captureBox != null) {
            discardCapture();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        Movement movement = movementOf(event);
        if (movement != null) {
            held.remove(movement);
            return true;
        }
        return super.keyReleased(event);
    }

    private @Nullable Movement movementOf(KeyEvent event) {
        var options = minecraft.options;
        if (options.keyUp.matches(event)) {
            return Movement.FORWARD;
        } else if (options.keyDown.matches(event)) {
            return Movement.BACK;
        } else if (options.keyLeft.matches(event)) {
            return Movement.LEFT;
        } else if (options.keyRight.matches(event)) {
            return Movement.RIGHT;
        } else if (options.keyJump.matches(event)) {
            return Movement.UP;
        } else if (options.keyShift.matches(event)) {
            return Movement.DOWN;
        } else if (options.keySprint.matches(event)) {
            return Movement.FAST;
        }
        return null;
    }
}
