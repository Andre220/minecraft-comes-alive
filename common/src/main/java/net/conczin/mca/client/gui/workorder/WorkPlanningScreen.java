package net.conczin.mca.client.gui.workorder;

import net.conczin.mca.client.gui.widget.ItemButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.CreateWorkOrderMessage;
import net.conczin.mca.network.c2s.ManageWorkOrderMessage;
import net.conczin.mca.network.c2s.WorkOrdersRequest;
import net.conczin.mca.network.s2c.WorkOrdersResponse;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrderBlocks;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plan how the village grows, MineFortress style: fly a free camera over the village, pick
 * dig or build, drag out an area with the mouse and the village's builders and miners do
 * the work.
 */
public class WorkPlanningScreen extends Screen {
    private static final int REFRESH_INTERVAL = 40;
    private static final double PICK_RANGE = 160.0;
    private static final float LOOK_SENSITIVITY = 0.3F;
    private static final double SCROLL_CLIMB = 2.0;
    private static final int TOOLBAR_Y_OFFSET = 26;
    private static final int PANEL_WIDTH = 190;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_PALETTE = 12;
    private static final int STATUS_TICKS = 100;
    /** Offered to Villager Editor holders even if nobody has them, since their orders are free. */
    private static final List<Block> EDITOR_BLOCKS = List.of(
            Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.DIRT, Blocks.SMOOTH_STONE
    );

    private enum Movement {FORWARD, BACK, LEFT, RIGHT, UP, DOWN, FAST}

    private final PlanningSelection selection = new PlanningSelection();
    private final Set<Movement> held = EnumSet.noneOf(Movement.class);
    private final List<BlockState> palette = new ArrayList<>();
    private @Nullable PlanningCamera camera;
    private @Nullable WorkOrdersResponse data;
    private @Nullable BlockState block;
    private @Nullable BlockPos hovered;
    private boolean showOrders;
    private boolean rotating;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private int ticks;
    private @Nullable Component status;
    private int statusTicks;

    public WorkPlanningScreen() {
        super(Component.translatable("gui.workorder.title"));
    }

    /** Server data arrived; only matters while the planning screen is open. */
    public static void receive(WorkOrdersResponse response) {
        if (Minecraft.getInstance().gui.screen() instanceof WorkPlanningScreen screen) {
            screen.setData(response);
        }
    }

    private void setData(WorkOrdersResponse response) {
        boolean ordersChanged = data == null || data.orders().size() != response.orders().size()
                                || data.villageId() != response.villageId();
        data = response;
        rebuildPalette();
        if (ordersChanged || showOrders) {
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
        if (showOrders) {
            buildOrdersPanel();
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
        int y = height - TOOLBAR_Y_OFFSET;
        int buttonWidth = 56;
        int paletteWidth = selection.getType() == WorkOrderType.BUILD ? palette.size() * 22 + 6 : 0;
        int total = buttonWidth * 2 + 4 + paletteWidth + 6 + 80 + 4 + 50;
        int x = Math.max(4, (width - total) / 2);

        ButtonWidget dig = addRenderableWidget(new ButtonWidget(x, y, buttonWidth, 20,
                Component.translatable("gui.workorder.dig"), b -> setTool(WorkOrderType.DIG),
                Component.translatable("gui.workorder.dig.tooltip")));
        dig.active = selection.getType() != WorkOrderType.DIG;
        x += buttonWidth + 4;
        ButtonWidget build = addRenderableWidget(new ButtonWidget(x, y, buttonWidth, 20,
                Component.translatable("gui.workorder.build"), b -> setTool(WorkOrderType.BUILD),
                Component.translatable("gui.workorder.build.tooltip")));
        build.active = selection.getType() != WorkOrderType.BUILD;
        x += buttonWidth + 6;

        if (selection.getType() == WorkOrderType.BUILD) {
            for (BlockState state : palette) {
                ItemStack icon = new ItemStack(state.getBlock().asItem());
                MutableComponent tooltip = state.getBlock().getName().copy();
                if (data != null && data.getAccess() != WorkOrderPermissions.Access.EDITOR) {
                    tooltip.append("\n").append(Component.translatable("gui.workorder.storage_count", stored(state))
                            .withStyle(ChatFormatting.GRAY));
                }
                ItemButtonWidget button = addRenderableWidget(new ItemButtonWidget(x, y, 20, tooltip, icon, b -> {
                    block = state;
                    rebuildWidgets();
                }));
                button.active = state != block;
                x += 22;
            }
            x += 6;
        }

        int orderCount = data == null ? 0 : data.orders().size();
        addRenderableWidget(new ButtonWidget(x, y, 80, 20, Component.translatable("gui.workorder.orders", orderCount), b -> {
            showOrders = !showOrders;
            rebuildWidgets();
        }, Component.translatable("gui.workorder.orders.tooltip")));
        x += 84;
        addRenderableWidget(new ButtonWidget(x, y, 50, 20, Component.translatable("gui.done"), b -> onClose()));
    }

    private void buildOrdersPanel() {
        if (data == null) {
            return;
        }
        int x = width - PANEL_WIDTH - 6;
        int y = 34;
        boolean canManage = data.getAccess().allowed();
        for (WorkOrdersResponse.OrderInfo order : data.orders()) {
            if (y + ROW_HEIGHT > height - TOOLBAR_Y_OFFSET - 8) {
                break;
            }
            int buttonX = x + PANEL_WIDTH - 66;
            addRenderableWidget(new ButtonWidget(buttonX, y, 20, 20, Component.literal("◎"),
                    b -> focus(order.area()), Component.translatable("gui.workorder.focus")));
            ButtonWidget pause = addRenderableWidget(new ButtonWidget(buttonX + 22, y, 20, 20,
                    Component.literal(order.paused() ? "▶" : "II"),
                    b -> manage(order, order.paused() ? ManageWorkOrderMessage.RESUME : ManageWorkOrderMessage.PAUSE),
                    Component.translatable(order.paused() ? "gui.workorder.resume" : "gui.workorder.pause")));
            ButtonWidget cancel = addRenderableWidget(new ButtonWidget(buttonX + 44, y, 20, 20,
                    Component.literal("✖"), b -> manage(order, ManageWorkOrderMessage.CANCEL),
                    Component.translatable("gui.workorder.cancel")));
            pause.active = canManage;
            cancel.active = canManage;
            y += ROW_HEIGHT;
        }
    }

    private void setTool(WorkOrderType type) {
        selection.setType(type);
        if (type == WorkOrderType.BUILD && block == null && !palette.isEmpty()) {
            block = palette.getFirst();
        }
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

    private int stored(BlockState state) {
        if (data == null) {
            return 0;
        }
        Item item = state.getBlock().asItem();
        return data.stored(BuiltInRegistries.ITEM.getKey(item).toString());
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
        Optional<WorkArea> area = selection.area();
        if (area.isEmpty()) {
            return;
        }
        if (data != null && data.maxVolume() > 0 && area.get().volume() > data.maxVolume()) {
            setStatus(Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED));
            return;
        }
        WorkOrderType type = selection.getType();
        if (type == WorkOrderType.BUILD && block == null) {
            setStatus(Component.translatable("gui.workorder.pick_block").withStyle(ChatFormatting.RED));
            return;
        }
        WorkArea box = area.get();
        Network.sendToServer(CreateWorkOrderMessage.of(type,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
                type == WorkOrderType.BUILD ? block : null));
        selection.clear();
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
        Vec3[] ray = cursorRay();
        Vec3 origin = ray[0];
        Vec3 direction = ray[1];

        BlockHitResult hit = level.clip(new ClipContext(origin, origin.add(direction.scale(PICK_RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        BlockPos anchor = selection.getAnchor();
        if (anchor != null) {
            // while dragging, follow the cursor on the selection's own layer
            double planeY = selection.getType() == WorkOrderType.DIG ? anchor.getY() + 1.0 : anchor.getY();
            Optional<Vec3> onPlane = PlanningMath.intersectHorizontal(origin, direction, planeY, PICK_RANGE);
            if (onPlane.isPresent()) {
                selection.update(BlockPos.containing(onPlane.get().x, anchor.getY(), onPlane.get().z));
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                selection.update(hit.getBlockPos());
            }
            hovered = null;
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            hovered = selection.getType() == WorkOrderType.DIG ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
        } else {
            hovered = null;
        }
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

        int forward = (held.contains(Movement.FORWARD) ? 1 : 0) - (held.contains(Movement.BACK) ? 1 : 0);
        int strafe = (held.contains(Movement.LEFT) ? 1 : 0) - (held.contains(Movement.RIGHT) ? 1 : 0);
        int vertical = (held.contains(Movement.UP) ? 1 : 0) - (held.contains(Movement.DOWN) ? 1 : 0);
        camera.tick(forward, strafe, vertical, held.contains(Movement.FAST));

        updateCursor(level);
        draw(level);

        if (++ticks % REFRESH_INTERVAL == 0) {
            requestData();
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    private void draw(ClientLevel level) {
        if (data != null) {
            Optional<WorkArea> area = selection.area();
            for (WorkOrdersResponse.OrderInfo order : data.orders()) {
                boolean highlighted = hovered != null && order.area().contains(hovered);
                WorkOrderOverlay.drawOrder(order, highlighted);
            }
            area.ifPresent(box -> WorkOrderOverlay.drawSelection(level, box, selection.getType(),
                    block, data.maxVolume() <= 0 || box.volume() <= data.maxVolume()));
        }
        if (hovered != null) {
            WorkOrderOverlay.drawHover(hovered, WorkOrderOverlay.colorOf(selection.getType()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int textColor = 0xFFFFFFFF;
        int grey = 0xFFB0B0B0;
        int y = 6;

        // header: village, permission, workers
        if (data == null) {
            graphics.text(font, Component.translatable("gui.workorder.loading"), 6, y, grey);
        } else if (!data.hasVillage()) {
            graphics.text(font, Component.translatable("gui.workorder.no_village").withStyle(ChatFormatting.YELLOW), 6, y, textColor);
        } else {
            graphics.text(font, Component.translatable("gui.workorder.village", data.villageName()).withStyle(ChatFormatting.GOLD), 6, y, textColor);
            y += 11;
            Component access = switch (data.getAccess()) {
                case EDITOR -> Component.translatable("gui.workorder.access.editor").withStyle(ChatFormatting.AQUA);
                case RANK -> Component.translatable("gui.workorder.access.rank").withStyle(ChatFormatting.GREEN);
                case DENIED -> Component.translatable("gui.workorder.access.denied", Component.translatable(data.minRank())).withStyle(ChatFormatting.RED);
            };
            graphics.text(font, access, 6, y, textColor);
            y += 11;
            MutableComponent workers = Component.translatable("gui.workorder.workers", data.builders(), data.miners());
            graphics.text(font, data.builders() + data.miners() == 0 ? workers.withStyle(ChatFormatting.YELLOW) : workers, 6, y, grey);
            if (!data.hasStorage()) {
                y += 11;
                graphics.text(font, Component.translatable("gui.workorder.no_storage").withStyle(ChatFormatting.YELLOW), 6, y, textColor);
            }
        }

        // controls right above the toolbar, what the selection would do above them
        int infoY = height - TOOLBAR_Y_OFFSET - 12;
        Component help = selection.isActive()
                ? Component.translatable("gui.workorder.help.selecting", Component.translatable(
                selection.getType() == WorkOrderType.DIG ? "gui.workorder.depth" : "gui.workorder.height"))
                : Component.translatable("gui.workorder.help.idle");
        graphics.centeredText(font, help, width / 2, infoY, grey);
        infoY -= 13;
        for (Component line : describeSelection()) {
            graphics.centeredText(font, line, width / 2, infoY, textColor);
            infoY -= 11;
        }
        if (status != null && statusTicks > 0) {
            graphics.centeredText(font, status, width / 2, infoY, textColor);
        }

        if (showOrders) {
            extractOrdersPanel(graphics);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private List<Component> describeSelection() {
        List<Component> lines = new ArrayList<>();
        Optional<WorkArea> area = selection.area();
        if (area.isEmpty()) {
            if (selection.getType() == WorkOrderType.BUILD && palette.isEmpty()) {
                lines.add(Component.translatable("gui.workorder.no_blocks").withStyle(ChatFormatting.YELLOW));
            }
            return lines;
        }
        WorkArea box = area.get();
        boolean tooBig = data != null && data.maxVolume() > 0 && box.volume() > data.maxVolume();
        ClientLevel level = minecraft.level;
        int changes = level == null || box.volume() > 32768 ? -1 : countChanges(level, box);

        // drawn bottom-up
        if (selection.getType() == WorkOrderType.BUILD && block != null && changes >= 0) {
            if (data != null && data.getAccess() == WorkOrderPermissions.Access.EDITOR) {
                lines.add(Component.translatable("gui.workorder.material.free", block.getBlock().getName()).withStyle(ChatFormatting.AQUA));
            } else {
                int inStorage = stored(block);
                lines.add(Component.translatable("gui.workorder.material", block.getBlock().getName(), changes, inStorage)
                        .withStyle(inStorage >= changes ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        MutableComponent size = Component.translatable("gui.workorder.size", box.sizeX(), box.sizeY(), box.sizeZ(),
                changes >= 0 ? changes : box.volume());
        lines.add(tooBig ? Component.translatable("gui.workorder.too_big", data.maxVolume()).withStyle(ChatFormatting.RED) : size);
        return lines;
    }

    /** Blocks the selection would actually dig or place. */
    private int countChanges(ClientLevel level, WorkArea area) {
        int count = 0;
        for (int y : area.layerOrder(true)) {
            for (BlockPos pos : area.layer(y)) {
                boolean changes = selection.getType() == WorkOrderType.DIG
                        ? WorkOrderBlocks.needsDigging(level, pos)
                        : block != null && WorkOrderBlocks.needsBuilding(level, pos, block);
                if (changes) {
                    count++;
                }
            }
        }
        return count;
    }

    private void extractOrdersPanel(GuiGraphicsExtractor graphics) {
        int x = width - PANEL_WIDTH - 6;
        int top = 20;
        int rows = data == null ? 0 : data.orders().size();
        int bottom = Math.min(top + 14 + rows * ROW_HEIGHT + 4, height - TOOLBAR_Y_OFFSET - 4);
        graphics.fill(x - 4, top, x + PANEL_WIDTH + 2, Math.max(bottom, top + 28), 0xA0000000);
        graphics.text(font, Component.translatable("gui.workorder.orders.title"), x, top + 3, 0xFFFFD27F);
        if (data == null || data.orders().isEmpty()) {
            graphics.text(font, Component.translatable("gui.workorder.orders.empty"), x, top + 16, 0xFFB0B0B0);
            return;
        }
        int y = 34;
        for (WorkOrdersResponse.OrderInfo order : data.orders()) {
            if (y + ROW_HEIGHT > height - TOOLBAR_Y_OFFSET - 8) {
                break;
            }
            int color = order.paused() ? WorkOrderOverlay.PAUSED_COLOR
                    : order.waiting() ? WorkOrderOverlay.WAITING_COLOR
                    : WorkOrderOverlay.colorOf(order.type());
            graphics.text(font, describe(order), x, y + 2, color);
            Component state = order.paused() ? Component.translatable("gui.workorder.order.paused")
                    : order.waiting() ? Component.translatable("gui.workorder.order.waiting")
                    : Component.translatable("gui.workorder.order.remaining", order.remaining());
            graphics.text(font, state, x, y + 12, 0xFFB0B0B0);
            // progress bar
            int barWidth = PANEL_WIDTH - 72;
            graphics.fill(x, y + 21, x + barWidth, y + 22, 0xFF404040);
            graphics.fill(x, y + 21, x + Math.round(barWidth * order.progress()), y + 22, color);
            y += ROW_HEIGHT;
        }
    }

    private Component describe(WorkOrdersResponse.OrderInfo order) {
        int percent = Math.round(order.progress() * 100.0F);
        if (order.type() == WorkOrderType.DIG) {
            return Component.translatable("gui.workorder.order.dig", order.id(), percent);
        }
        Component name = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(order.block()))
                .map(Block::getName)
                .map(Component.class::cast)
                .orElse(Component.literal(order.block()));
        return Component.translatable("gui.workorder.order.build", order.id(), percent, name);
    }

    // ---------------------------------------------------------------- input

    private boolean isOverUi(double mouseX, double mouseY) {
        if (mouseY >= height - TOOLBAR_Y_OFFSET - 2) {
            return true;
        }
        return showOrders && mouseX >= width - PANEL_WIDTH - 10 && mouseY >= 20
               && mouseY <= 34 + (data == null ? 0 : data.orders().size()) * ROW_HEIGHT + 4;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
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
        if (data == null || !data.hasVillage()) {
            setStatus(Component.translatable("gui.workorder.no_village").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        if (!data.getAccess().allowed()) {
            setStatus(Component.translatable("gui.workorder.access.denied", Component.translatable(data.minRank())).withStyle(ChatFormatting.RED));
            return true;
        }
        if (selection.isActive()) {
            confirm();
        } else if (hovered != null) {
            if (selection.getType() == WorkOrderType.BUILD && block == null) {
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
        if (!Double.isNaN(lastMouseX) && camera != null && (rotating || isControlDown())) {
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
        if (selection.isActive()) {
            selection.adjustHeight(scrollY > 0 ? 1 : -1);
        } else if (camera != null) {
            camera.moveBy(0, scrollY * SCROLL_CLIMB, 0);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
            case GLFW.GLFW_KEY_1 -> {
                setTool(WorkOrderType.DIG);
                return true;
            }
            case GLFW.GLFW_KEY_2 -> {
                setTool(WorkOrderType.BUILD);
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
