package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.client.gui.widget.IconButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.BlueprintRequest;
import net.conczin.mca.network.c2s.CaptureBlueprintMessage;
import net.conczin.mca.network.c2s.CreateWorkOrderMessage;
import net.conczin.mca.network.c2s.ManageWorkOrderMessage;
import net.conczin.mca.network.c2s.PlaceBlueprintMessage;
import net.conczin.mca.network.c2s.WorkOrdersRequest;
import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.Blueprint;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
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
import net.minecraft.world.level.block.Blocks;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plan how the village grows, MineFortress style: fly a free camera over the village, pick a
 * tool, drag out an area with the mouse or place a blueprint, and the village's builders and
 * miners do the work. Buildings made by hand can be captured as new blueprints.
 */
public class WorkPlanningScreen extends Screen {
    private static final int REFRESH_INTERVAL = 40;
    private static final double PICK_RANGE = 160.0;
    private static final float LOOK_SENSITIVITY = 0.3F;
    private static final double SCROLL_CLIMB = 2.0;
    private static final int TOOLBAR_Y_OFFSET = 26;
    private static final int PANEL_WIDTH = 196;
    private static final int TOOL_WIDTH = 74;
    private static final int PALETTE_WIDTH = 70;
    private static final int SECOND_ROW_OFFSET = 24;
    private static final int ORDERS_TOP = 30;
    private static final int ORDERS_FIRST_ROW = 44;
    private static final int ORDER_ROW_HEIGHT = 46;
    private static final int LIST_WIDTH = 140;
    private static final int LIST_TOP = 80;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_PALETTE = 12;
    private static final int STATUS_TICKS = 100;
    private static final int MATERIAL_LINES = 4;
    /** Offered to Villager Editor holders even if nobody has them, since their orders are free. */
    private static final List<Block> EDITOR_BLOCKS = List.of(
            Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.DIRT, Blocks.SMOOTH_STONE
    );

    private enum Movement {FORWARD, BACK, LEFT, RIGHT, UP, DOWN, FAST}

    private final PlanningSelection selection = new PlanningSelection();
    private final Set<Movement> held = EnumSet.noneOf(Movement.class);
    private final List<BlockState> palette = new ArrayList<>();
    /** Blueprints fetched for previews, by id; dropped when the server reports them changed. */
    private final Map<String, Blueprint> blueprints = new HashMap<>();
    private final Set<String> requested = new LinkedHashSet<>();
    private @Nullable PlanningCamera camera;
    private @Nullable WorkOrdersResponse data;
    private @Nullable BlockState block;
    private @Nullable String blueprintId;
    private int rotation;
    private @Nullable BlockPos hovered;
    private @Nullable PlacementPreview preview;
    private @Nullable EditBox nameBox;
    private @Nullable WorkArea naming;
    private boolean showOrders;
    private boolean rotating;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private int ticks;
    private @Nullable Component status;
    private int statusTicks;

    /** A blueprint at the cursor: where its blocks go and what it takes. */
    private record PlacementPreview(Blueprint blueprint, BlockPos origin, int rotation, Map<BlockPos, BlockState> blocks,
                                    WorkArea area, Blueprint.Materials materials, int obstructions) {
    }

    public WorkPlanningScreen() {
        super(Component.translatable("gui.workorder.title"));
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
                               || !data.blueprints().equals(response.blueprints());
        if (data != null) {
            // a blueprint that was captured again must be fetched again
            for (WorkOrdersResponse.BlueprintInfo info : data.blueprints()) {
                if (!response.blueprints().contains(info)) {
                    blueprints.remove(info.id());
                }
            }
        }
        data = response;
        if (blueprintId != null && response.blueprints().stream().noneMatch(info -> info.id().equals(blueprintId))) {
            blueprintId = null;
        }
        if (blueprintId == null && !response.blueprints().isEmpty()) {
            selectBlueprint(response.blueprints().getFirst().id());
        }
        preview = null;
        rebuildPalette();
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
            rebuildPalette();
            requestData();
        }
        buildToolbar();
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            buildBlueprintList();
        }
        if (showOrders) {
            buildOrdersPanel();
        }
        if (naming != null) {
            buildNamePrompt();
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

    // ---------------------------------------------------------------- widgets

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
        if (tool == PlanningTool.BUILD && !palette.isEmpty()) {
            int fit = Math.max(1, Math.min(palette.size(), (width - 8) / (PALETTE_WIDTH + 2)));
            x = (width - fit * (PALETTE_WIDTH + 2)) / 2;
            for (BlockState state : palette.subList(0, fit)) {
                Component name = state.getBlock().getName();
                MutableComponent tooltip = name.copy();
                if (!isFree()) {
                    tooltip.append("\n").append(Component.translatable("gui.workorder.storage_count", stored(state.getBlock().asItem()))
                            .withStyle(ChatFormatting.GRAY));
                }
                addRenderableWidget(new IconButtonWidget(x, rowY, PALETTE_WIDTH, 20, new ItemStack(state.getBlock().asItem()), name, b -> {
                    block = state;
                    rebuildWidgets();
                }, tooltip).highlighted(state == block));
                x += PALETTE_WIDTH + 2;
            }
        } else if (tool == PlanningTool.BLUEPRINT) {
            addRenderableWidget(new IconButtonWidget((width - 100) / 2, rowY, 100, 20, new ItemStack(Items.COMPASS),
                    Component.translatable("gui.workorder.rotate.button", rotation * 90), b -> rotate(),
                    Component.translatable("gui.workorder.rotate")));
        }

        // top right: the orders and leaving
        int doneX = width - 6 - 70;
        addRenderableWidget(new IconButtonWidget(doneX, 6, 70, 20, new ItemStack(Items.OAK_DOOR),
                Component.translatable("gui.workorder.done"), b -> onClose(), Component.translatable("gui.workorder.done.tooltip")));
        int orderCount = data == null ? 0 : data.orders().size();
        addRenderableWidget(new IconButtonWidget(doneX - 4 - 92, 6, 92, 20, new ItemStack(Items.WRITABLE_BOOK),
                Component.translatable("gui.workorder.orders", orderCount), b -> {
            showOrders = !showOrders;
            rebuildWidgets();
        }, Component.translatable("gui.workorder.orders.tooltip")).highlighted(showOrders));
    }

    private static ItemStack iconOf(PlanningTool tool) {
        return new ItemStack(switch (tool) {
            case DIG -> Items.IRON_PICKAXE;
            case BUILD -> Items.BRICKS;
            case BLUEPRINT -> Items.MAP;
            case CAPTURE -> Items.PAINTING;
        });
    }

    private boolean hasSecondRow() {
        PlanningTool tool = selection.getTool();
        return tool == PlanningTool.BUILD && !palette.isEmpty() || tool == PlanningTool.BLUEPRINT;
    }

    /** Lowest y the floating texts and panels may use above the bottom rows. */
    private int bottomUiTop() {
        return height - TOOLBAR_Y_OFFSET - (hasSecondRow() ? SECOND_ROW_OFFSET : 0);
    }

    /** Saved blueprints down the left side while the blueprint tool is picked. */
    private void buildBlueprintList() {
        if (data == null) {
            return;
        }
        int y = LIST_TOP + 14;
        for (WorkOrdersResponse.BlueprintInfo info : data.blueprints()) {
            if (y + ROW_HEIGHT > bottomUiTop() - 40) {
                break;
            }
            ItemStack icon = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(info.icon()))
                    .map(ItemStack::new)
                    .orElseGet(() -> new ItemStack(Items.MAP));
            addRenderableWidget(new IconButtonWidget(6, y, LIST_WIDTH, 20, icon, blueprintName(info), b -> {
                selectBlueprint(info.id());
                rebuildWidgets();
            }, Component.translatable("gui.workorder.blueprint.info", info.sizeX(), info.sizeY(), info.sizeZ(), info.blocks()))
                    .highlighted(info.id().equals(blueprintId)));
            y += ROW_HEIGHT;
        }
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
        int y = ORDERS_FIRST_ROW;
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

    /** Asks for a name after a capture box was confirmed. */
    private void buildNamePrompt() {
        int boxWidth = 170;
        int x = (width - boxWidth - 142) / 2;
        int y = height / 2 + 30;
        String previous = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(font, x, y, boxWidth, 20, Component.translatable("gui.workorder.capture.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(previous);
        nameBox.setHint(Component.translatable("gui.workorder.capture.name"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(new IconButtonWidget(x + boxWidth + 4, y, 66, 20, new ItemStack(Items.NAME_TAG),
                Component.translatable("gui.workorder.capture.save"), b -> saveCapture(), null));
        addRenderableWidget(new IconButtonWidget(x + boxWidth + 72, y, 70, 20, new ItemStack(Items.BARRIER),
                Component.translatable("gui.cancel"), b -> cancelNaming(), null));
    }

    private void setTool(PlanningTool tool) {
        cancelNaming();
        selection.setTool(tool);
        preview = null;
        if (tool == PlanningTool.BUILD && block == null && !palette.isEmpty()) {
            block = palette.getFirst();
        }
        if (tool == PlanningTool.BLUEPRINT && blueprintId != null) {
            selectBlueprint(blueprintId);
        }
        rebuildWidgets();
    }

    private void selectBlueprint(String id) {
        blueprintId = id;
        preview = null;
        if (!blueprints.containsKey(id) && requested.add(id)) {
            Network.sendToServer(new BlueprintRequest(id));
        }
    }

    private void rotate() {
        rotation = (rotation + 1) % 4;
        preview = null;
        rebuildWidgets();
    }

    /** Blocks to build with: what the storage holds most of, then what the player carries. */
    private void rebuildPalette() {
        Set<Block> blocks = new LinkedHashSet<>();
        if (data != null) {
            data.storage().entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .map(entry -> BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(entry.getKey())))
                    .flatMap(Optional::stream)
                    .filter(item -> item instanceof BlockItem)
                    .map(item -> ((BlockItem) item).getBlock())
                    .forEach(blocks::add);
        }
        LocalPlayer player = minecraft.player;
        if (player != null) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (player.getInventory().getItem(slot).getItem() instanceof BlockItem item) {
                    blocks.add(item.getBlock());
                }
            }
        }
        if (data != null && data.getAccess() == WorkOrderPermissions.Access.EDITOR) {
            blocks.addAll(EDITOR_BLOCKS);
        }

        palette.clear();
        blocks.stream()
                .filter(candidate -> candidate.asItem() != net.minecraft.world.item.Items.AIR)
                .limit(MAX_PALETTE)
                .forEach(candidate -> palette.add(candidate.defaultBlockState()));
        if (block == null || !palette.contains(block)) {
            block = palette.isEmpty() ? null : palette.getFirst();
        }
    }

    private int stored(Item item) {
        if (data == null) {
            return 0;
        }
        return data.stored(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private boolean isFree() {
        return data != null && data.getAccess() == WorkOrderPermissions.Access.EDITOR;
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

    private void confirm() {
        PlanningTool tool = selection.getTool();
        if (tool == PlanningTool.BLUEPRINT) {
            placeBlueprint();
            return;
        }
        Optional<WorkArea> area = selection.area();
        if (area.isEmpty()) {
            return;
        }
        WorkArea box = area.get();
        if (tool == PlanningTool.CAPTURE) {
            naming = box;
            rebuildWidgets();
            return;
        }
        if (data != null && data.maxVolume() > 0 && box.volume() > data.maxVolume()) {
            setStatus(Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED));
            return;
        }
        if (tool == PlanningTool.BUILD && block == null) {
            setStatus(Component.translatable("gui.workorder.pick_block").withStyle(ChatFormatting.RED));
            return;
        }
        WorkOrderType type = tool.orderType();
        if (type == null) {
            return;
        }
        Network.sendToServer(CreateWorkOrderMessage.of(type,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
                tool == PlanningTool.BUILD ? block : null));
        selection.clear();
    }

    private void placeBlueprint() {
        if (blueprintId == null) {
            setStatus(Component.translatable("gui.workorder.blueprint.none").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (preview == null) {
            return;
        }
        Network.sendToServer(new PlaceBlueprintMessage(blueprintId, preview.origin(), preview.rotation()));
    }

    private void saveCapture() {
        if (naming == null || nameBox == null) {
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            setStatus(Component.translatable("gui.workorder.capture.name_missing").withStyle(ChatFormatting.RED));
            return;
        }
        Network.sendToServer(new CaptureBlueprintMessage(
                new BlockPos(naming.minX(), naming.minY(), naming.minZ()),
                new BlockPos(naming.maxX(), naming.maxY(), naming.maxZ()),
                name));
        nameBox.setValue("");
        cancelNaming();
        selection.clear();
    }

    private void cancelNaming() {
        if (naming != null) {
            naming = null;
            rebuildWidgets();
        }
    }

    private void setStatus(Component message) {
        status = message;
        statusTicks = STATUS_TICKS;
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

    private void updateCursor(ClientLevel level) {
        if (naming != null) {
            // the box is set, the player is typing its name
            hovered = null;
            return;
        }
        Vec3[] ray = cursorRay();
        Vec3 origin = ray[0];
        Vec3 direction = ray[1];

        BlockHitResult hit = level.clip(new ClipContext(origin, origin.add(direction.scale(PICK_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        PlanningTool tool = selection.getTool();
        BlockPos anchor = selection.getAnchor();
        if (anchor != null) {
            // while dragging, follow the cursor on the selection's own layer
            double planeY = tool.targetsBlock() ? anchor.getY() + 1.0 : anchor.getY();
            Optional<Vec3> onPlane = PlanningMath.intersectHorizontal(origin, direction, planeY, PICK_RANGE);
            if (onPlane.isPresent()) {
                selection.update(BlockPos.containing(onPlane.get().x, anchor.getY(), onPlane.get().z));
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                selection.update(hit.getBlockPos());
            }
            hovered = null;
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            hovered = tool.targetsBlock() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        } else {
            hovered = null;
        }
    }

    /** Where the selected blueprint would go at the cursor; rebuilt only when cursor, rotation or blueprint change. */
    private @Nullable PlacementPreview updatePreview(ClientLevel level) {
        if (selection.getTool() != PlanningTool.BLUEPRINT || hovered == null || blueprintId == null) {
            return null;
        }
        Blueprint blueprint = blueprints.get(blueprintId);
        if (blueprint == null) {
            return null;
        }
        if (preview != null && preview.blueprint() == blueprint && preview.origin().equals(hovered) && preview.rotation() == rotation) {
            return preview;
        }
        Map<BlockPos, BlockState> placed = blueprint.place(hovered, rotation);
        int obstructions = 0;
        for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
            BlockState current = level.getBlockState(entry.getKey());
            if (!current.isAir() && !current.canBeReplaced() && !current.is(entry.getValue().getBlock())) {
                obstructions++;
            }
        }
        return new PlacementPreview(blueprint, hovered, rotation, placed, blueprint.area(hovered, rotation),
                blueprint.materials(), obstructions);
    }

    // ---------------------------------------------------------------- tick and drawing

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !player.isAlive() || camera == null) {
            onClose();
            return;
        }
        if (player.hurtTime > 0) {
            player.sendSystemMessage(Component.translatable("gui.workorder.interrupted").withStyle(ChatFormatting.RED));
            onClose();
            return;
        }

        if (naming == null) {
            int forward = (held.contains(Movement.FORWARD) ? 1 : 0) - (held.contains(Movement.BACK) ? 1 : 0);
            int strafe = (held.contains(Movement.LEFT) ? 1 : 0) - (held.contains(Movement.RIGHT) ? 1 : 0);
            int vertical = (held.contains(Movement.UP) ? 1 : 0) - (held.contains(Movement.DOWN) ? 1 : 0);
            camera.tick(forward, strafe, vertical, held.contains(Movement.FAST));
        } else {
            camera.tick(0, 0, 0, false);
        }

        updateCursor(level);
        preview = updatePreview(level);
        draw(level);

        if (++ticks % REFRESH_INTERVAL == 0) {
            requestData();
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    private void draw(ClientLevel level) {
        PlanningTool tool = selection.getTool();
        if (data != null) {
            for (WorkOrdersResponse.OrderInfo order : data.orders()) {
                boolean highlighted = hovered != null && order.area().contains(hovered);
                WorkOrderOverlay.drawOrder(order, highlighted);
            }
        }
        if (naming != null) {
            WorkOrderOverlay.drawCapture(naming);
        } else if (tool == PlanningTool.BLUEPRINT) {
            if (preview != null) {
                WorkOrderOverlay.drawBlueprint(level, preview.blocks(), preview.area());
            }
        } else {
            selection.area().ifPresent(box -> {
                if (tool == PlanningTool.CAPTURE) {
                    WorkOrderOverlay.drawCapture(box);
                } else if (tool.orderType() != null) {
                    WorkOrderOverlay.drawSelection(level, box, tool.orderType(), block,
                            data == null || data.maxVolume() <= 0 || box.volume() <= data.maxVolume());
                }
            });
        }
        if (hovered != null && tool != PlanningTool.BLUEPRINT) {
            WorkOrderOverlay.drawHover(hovered, WorkOrderOverlay.colorOf(tool));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int textColor = 0xFFFFFFFF;
        int grey = 0xFFB0B0B0;
        int y = 6;

        // header: village, permission, workers; long lines wrap next to the buttons on the right
        int headerWidth = Math.max(120, width - 190);
        if (data == null) {
            y = wrapped(graphics, Component.translatable("gui.workorder.loading"), 6, y, headerWidth, grey);
        } else if (!data.hasVillage()) {
            y = wrapped(graphics, Component.translatable("gui.workorder.no_village").withStyle(ChatFormatting.YELLOW), 6, y, headerWidth, textColor);
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
            if (!data.hasStorage()) {
                wrapped(graphics, Component.translatable("gui.workorder.no_storage").withStyle(ChatFormatting.YELLOW), 6, y, headerWidth, textColor);
            }
        }

        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            graphics.text(font, Component.translatable("gui.workorder.blueprints").withStyle(ChatFormatting.GOLD), 6, LIST_TOP, textColor);
            if (data != null && data.blueprints().isEmpty()) {
                graphics.text(font, Component.translatable("gui.workorder.blueprints.empty"), 6, LIST_TOP + 14, grey);
            }
        }

        // controls right above the toolbar, what the selection would do above them
        int infoY = bottomUiTop() - 12;
        String helpKey = helpKey();
        Component dimension = Component.translatable(selection.getTool().growsDown() ? "gui.workorder.depth" : "gui.workorder.height");
        graphics.centeredText(font, Component.translatable(helpKey + ".2", dimension), width / 2, infoY, grey);
        infoY -= 10;
        graphics.centeredText(font, Component.translatable(helpKey + ".1", dimension), width / 2, infoY, grey);
        infoY -= 13;
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
        if (naming != null) {
            graphics.centeredText(font, Component.translatable("gui.workorder.capture.prompt"), width / 2, height / 2 + 16, textColor);
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

    private String helpKey() {
        if (naming != null) {
            return "gui.workorder.help.naming";
        }
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            return "gui.workorder.help.blueprint";
        }
        return selection.isActive() ? "gui.workorder.help.selecting" : "gui.workorder.help.idle";
    }

    /** Lines about what the current selection or preview would do, drawn bottom-up. */
    private List<Component> describeSelection() {
        PlanningTool tool = selection.getTool();
        if (tool == PlanningTool.BLUEPRINT) {
            return describePreview();
        }
        List<Component> lines = new ArrayList<>();
        Optional<WorkArea> area = naming != null ? Optional.of(naming) : selection.area();
        if (area.isEmpty()) {
            if (tool == PlanningTool.BUILD && palette.isEmpty()) {
                lines.add(Component.translatable("gui.workorder.no_blocks").withStyle(ChatFormatting.YELLOW));
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
        int changes = level == null || box.volume() > 32768 ? -1 : countChanges(level, box);

        if (tool == PlanningTool.BUILD && block != null && changes >= 0) {
            if (isFree()) {
                lines.add(Component.translatable("gui.workorder.material.free", block.getBlock().getName()).withStyle(ChatFormatting.AQUA));
            } else {
                int inStorage = stored(block.getBlock().asItem());
                lines.add(Component.translatable("gui.workorder.material", block.getBlock().getName(), changes, inStorage)
                        .withStyle(inStorage >= changes ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        MutableComponent size = Component.translatable("gui.workorder.size", box.sizeX(), box.sizeY(), box.sizeZ(),
                changes >= 0 ? changes : box.volume());
        lines.add(tooBig ? Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED) : size);
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

        // materials, most needed first; drawn bottom-up so add them in reverse
        Blueprint.Materials materials = preview.materials();
        List<Map.Entry<Item, Integer>> needed = materials.items().entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue(Comparator.reverseOrder()))
                .toList();
        List<Component> materialLines = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : needed.subList(0, Math.min(MATERIAL_LINES, needed.size()))) {
            Component name = entry.getKey().getDefaultInstance().getHoverName();
            if (isFree()) {
                materialLines.add(Component.translatable("gui.workorder.material.free_count", name, entry.getValue()).withStyle(ChatFormatting.AQUA));
            } else {
                int inStorage = stored(entry.getKey());
                materialLines.add(Component.translatable("gui.workorder.material", name, entry.getValue(), inStorage)
                        .withStyle(inStorage >= entry.getValue() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        if (needed.size() > MATERIAL_LINES) {
            lines.add(Component.translatable("gui.workorder.material.more", needed.size() - MATERIAL_LINES).withStyle(ChatFormatting.GRAY));
        }
        for (int i = materialLines.size() - 1; i >= 0; i--) {
            lines.add(materialLines.get(i));
        }
        if (!materials.unobtainable().isEmpty()) {
            lines.add(Component.translatable("gui.workorder.blueprint.unobtainable",
                    materials.unobtainable().iterator().next().getName()).withStyle(ChatFormatting.RED));
        }
        if (preview.obstructions() > 0) {
            lines.add(Component.translatable("gui.workorder.blueprint.obstructions", preview.obstructions()).withStyle(ChatFormatting.GOLD));
        }
        WorkArea area = preview.area();
        lines.add(Component.translatable("gui.workorder.blueprint.summary", blueprintName(preview.blueprint().id(), preview.blueprint().name()),
                area.sizeX(), area.sizeY(), area.sizeZ(), materials.total()));
        return lines;
    }

    /** Blocks the selection would actually dig or place. */
    private int countChanges(ClientLevel level, WorkArea area) {
        int count = 0;
        for (int y : area.layerOrder(true)) {
            for (BlockPos pos : area.layer(y)) {
                boolean changes = selection.getTool() == PlanningTool.DIG
                        ? WorkOrderBlocks.needsDigging(level, pos)
                        : block != null && WorkOrderBlocks.needsBuilding(level, pos, block);
                if (changes) {
                    count++;
                }
            }
        }
        return count;
    }

    /** How many orders fit into the panel. */
    private int visibleOrderRows() {
        int rows = data == null ? 0 : data.orders().size();
        int room = Math.max(0, (bottomUiTop() - 30 - ORDERS_FIRST_ROW) / ORDER_ROW_HEIGHT);
        return Math.min(rows, room);
    }

    private int ordersPanelBottom() {
        return ORDERS_FIRST_ROW + Math.max(1, visibleOrderRows()) * ORDER_ROW_HEIGHT + 4;
    }

    private void extractOrdersPanel(GuiGraphicsExtractor graphics) {
        int x = width - PANEL_WIDTH - 6;
        graphics.fill(x - 4, ORDERS_TOP, x + PANEL_WIDTH + 2, ordersPanelBottom(), 0xA0000000);
        graphics.text(font, Component.translatable("gui.workorder.orders.title"), x, ORDERS_TOP + 3, 0xFFFFD27F);
        if (data == null || data.orders().isEmpty()) {
            graphics.text(font, Component.translatable("gui.workorder.orders.empty"), x, ORDERS_FIRST_ROW + 2, 0xFFB0B0B0);
            return;
        }
        int shown = visibleOrderRows();
        int y = ORDERS_FIRST_ROW;
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
            case DIG -> Component.translatable("gui.workorder.order.dig", order.id(), percent);
            case BLUEPRINT -> {
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
        if (mouseY <= 28 && mouseX >= width - 176) {
            return true;
        }
        if (selection.getTool() == PlanningTool.BLUEPRINT && mouseX <= LIST_WIDTH + 10 && mouseY >= LIST_TOP) {
            return true;
        }
        return showOrders && mouseX >= width - PANEL_WIDTH - 10 && mouseY >= ORDERS_TOP && mouseY <= ordersPanelBottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (naming != null) {
            // clicks outside the name prompt do nothing until it is answered
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (selection.isActive()) {
                selection.clear();
            } else {
                rotating = true;
            }
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || isOverUi(event.x(), event.y())) {
            return false;
        }
        if (selection.getTool() != PlanningTool.CAPTURE && (data == null || !data.hasVillage())) {
            setStatus(Component.translatable("gui.workorder.no_village").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        if (data != null && data.hasVillage() && !data.getAccess().allowed()) {
            setStatus(Component.translatable("gui.workorder.access.denied", Component.translatable(data.minRank())).withStyle(ChatFormatting.RED));
            return true;
        }
        if (selection.getTool() == PlanningTool.BLUEPRINT) {
            placeBlueprint();
        } else if (selection.isActive()) {
            confirm();
        } else if (hovered != null) {
            if (selection.getTool() == PlanningTool.BUILD && block == null) {
                setStatus(Component.translatable("gui.workorder.pick_block").withStyle(ChatFormatting.RED));
            } else {
                selection.start(hovered);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rotating = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!Double.isNaN(lastMouseX) && camera != null && naming == null && (rotating || isControlDown())) {
            camera.rotate((mouseX - lastMouseX) * LOOK_SENSITIVITY * 2.0, (mouseY - lastMouseY) * LOOK_SENSITIVITY * 2.0);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    private boolean isControlDown() {
        var window = minecraft.getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
               || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (naming != null) {
            return true;
        }
        if (selection.isActive()) {
            selection.adjustHeight(scrollY > 0 ? 1 : -1);
        } else if (camera != null) {
            camera.moveBy(0, scrollY * SCROLL_CLIMB, 0);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (naming != null) {
            // typing a name: only Enter and Escape mean something here
            if (event.isEscape()) {
                cancelNaming();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                saveCapture();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.isEscape() && selection.isActive()) {
            selection.clear();
            return true;
        }
        Movement movement = movementOf(event);
        if (movement != null) {
            held.add(movement);
            return true;
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                confirm();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                showOrders = !showOrders;
                rebuildWidgets();
                return true;
            }
            case GLFW.GLFW_KEY_R -> {
                if (selection.getTool() == PlanningTool.BLUEPRINT) {
                    rotate();
                    return true;
                }
                return super.keyPressed(event);
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
