package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.conczin.mca.Config;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Planning work orders on behalf of a player, shared by the command and the planning screen.
 */
public final class WorkOrderService {
    /** How far around the village centre builders and miners are looked for. */
    private static final double WORKER_SEARCH_RADIUS = 128.0;

    private WorkOrderService() {
    }

    /** The created order and a message for the player, or why nothing was created. */
    public record Result(@Nullable WorkOrder order, @Nullable Component error, Component message) {
        static Result failure(Component error) {
            return new Result(null, error, error);
        }
    }

    /** Blocks planned one by one may be more than a box of the same work: a wall around the village is long but thin. */
    public static int maxInlineBlocks() {
        return Math.max(1, Config.getInstance().workOrderMaxVolume) * 4;
    }

    /** A dig area of a confirmed draft. */
    public record DigArea(WorkArea area, boolean demolish) {
    }

    /** A blueprint of a confirmed draft. */
    public record BlueprintPlacement(Identifier id, BlockPos origin, int rotation) {
    }

    /**
     * The planning screen's draft was confirmed: every dig area, every blueprint and the blocks placed one by one
     * become orders (in creative, they are done at once). Returns one message per part.
     */
    public static List<Component> commit(ServerPlayer player, List<DigArea> digs, List<BlueprintPlacement> blueprints,
                                         Map<BlockPos, BlockState> blocks) {
        List<Component> messages = new ArrayList<>();
        for (DigArea dig : digs) {
            messages.add(create(player, WorkOrderType.DIG, dig.area(), null, dig.demolish()).message());
        }
        for (BlueprintPlacement placement : blueprints) {
            messages.add(placeBlueprint(player, placement.id(), placement.origin(), placement.rotation()).message());
        }
        if (!blocks.isEmpty()) {
            messages.add(createInline(player, blocks).message());
        }
        messages.removeIf(message -> message.getString().isEmpty());
        return messages;
    }

    /** Blocks placed one by one, with their exact states. */
    public static Result createInline(ServerPlayer player, Map<BlockPos, BlockState> blocks) {
        if (!WorkOrderPermissions.enabled()) {
            return Result.failure(Component.translatable("workorder.error.disabled"));
        }
        if (player.isCreative()) {
            CreativeWork.addPlan(player.level(), blocks, player);
            return new Result(null, null, blocks.size() > Config.getInstance().workOrderBlocksPerTick || CreativeWork.busy()
                    ? Component.translatable("workorder.working_now", blocks.size()).withStyle(ChatFormatting.GRAY)
                    : Component.empty());
        }
        ServerLevel level = player.level();
        WorkArea area = WorkArea.around(blocks.keySet());
        Optional<Village> found = VillageManager.get(level).findNearestVillage(area.center(), Village.BORDER_MARGIN);
        if (found.isEmpty()) {
            return Result.failure(Component.translatable("workorder.error.no_village"));
        }
        Village village = found.get();
        WorkOrderPermissions.Access access = WorkOrderPermissions.check(player, village);
        if (!access.allowed()) {
            return Result.failure(noPermission(village));
        }
        VillageWorkOrders.CreateResult created = village.getWorkOrders().createInline(level, blocks,
                player.getUUID(), player.getGameProfile().name(), access.free());
        WorkOrder order = created.order();
        if (order == null) {
            return Result.failure(created.error() != null ? created.error() : Component.translatable("workorder.error.nothing_to_do"));
        }
        return new Result(order, null, createdMessage(level, village, order));
    }

    /** "Order #3 created", plus hints about missing builders and materials. */
    private static MutableComponent createdMessage(ServerLevel level, Village village, WorkOrder order) {
        MutableComponent message = Component.translatable("workorder.created",
                order.getId(), describeWork(order), village.getName(), order.getInitialWork()).withStyle(ChatFormatting.GREEN);
        if (order.isFree()) {
            message.append(" ").append(Component.translatable("workorder.created.free").withStyle(ChatFormatting.AQUA));
        }
        if (countWorkers(level, village, order.getType()) == 0) {
            message.append("\n").append(Component.translatable("workorder.hint.no_builders").withStyle(ChatFormatting.YELLOW));
        }
        if (!order.isFree()) {
            Map<Item, Integer> missing = VillageWorkOrders.missingMaterials(level, order);
            Map<Item, Integer> stock = VillageStorage.contents(level, village);
            long lacking = missing.entrySet().stream().filter(entry -> stock.getOrDefault(entry.getKey(), 0) < entry.getValue()).count();
            if (lacking > 0) {
                message.append("\n").append(Component.translatable("blueprint.hint.materials", lacking, missing.size()).withStyle(ChatFormatting.YELLOW));
            }
        }
        return message;
    }

    public static Result create(ServerPlayer player, WorkOrderType type, WorkArea area, @Nullable BlockState block) {
        return create(player, type, area, block, false);
    }

    public static Result create(ServerPlayer player, WorkOrderType type, WorkArea area, @Nullable BlockState block, boolean demolish) {
        if (!WorkOrderPermissions.enabled()) {
            return Result.failure(Component.translatable("workorder.error.disabled"));
        }
        if (player.isCreative()) {
            return applyNow(player, type, area, block, demolish);
        }
        ServerLevel level = player.level();
        Optional<Village> found = VillageManager.get(level).findNearestVillage(area.center(), Village.BORDER_MARGIN);
        if (found.isEmpty()) {
            return Result.failure(Component.translatable("workorder.error.no_village"));
        }
        Village village = found.get();
        WorkOrderPermissions.Access access = WorkOrderPermissions.check(player, village);
        if (!access.allowed()) {
            return Result.failure(noPermission(village));
        }

        VillageWorkOrders.CreateResult created = village.getWorkOrders().create(level, type, area, block,
                player.getUUID(), player.getGameProfile().name(), access.free(), demolish);
        WorkOrder order = created.order();
        if (order == null) {
            return Result.failure(created.error() != null ? created.error() : Component.translatable("workorder.error.nothing_to_do"));
        }

        MutableComponent message = Component.translatable("workorder.created",
                order.getId(), describeWork(order), village.getName(), order.getInitialWork()).withStyle(ChatFormatting.GREEN);
        if (order.isFree()) {
            message.append(" ").append(Component.translatable("workorder.created.free").withStyle(ChatFormatting.AQUA));
        }
        if (countWorkers(level, village, type) == 0) {
            message.append("\n").append(Component.translatable(type == WorkOrderType.DIG
                    ? "workorder.hint.no_miners" : "workorder.hint.no_builders").withStyle(ChatFormatting.YELLOW));
        }
        Optional<Item> material = order.getMaterial();
        if (material.isPresent()) {
            if (!VillageStorage.exists(village)) {
                message.append("\n").append(Component.translatable("workorder.hint.no_storage").withStyle(ChatFormatting.YELLOW));
            } else {
                int stored = VillageStorage.count(level, village, material.get());
                if (stored < order.getInitialWork()) {
                    message.append("\n").append(Component.translatable("workorder.hint.low_material",
                            material.get().getDefaultInstance().getHoverName(), stored, order.getInitialWork()).withStyle(ChatFormatting.YELLOW));
                }
            }
        } else if (type == WorkOrderType.DIG && !VillageStorage.exists(village)) {
            message.append("\n").append(Component.translatable("workorder.hint.no_storage_dig").withStyle(ChatFormatting.YELLOW));
        }
        return new Result(order, null, message);
    }

    /**
     * Creative: the area is dug out or built right away, village or not, with the same rules as the workers (nothing
     * with a block entity is dug, no fluids; building fills what is empty or replaceable). Large areas are done a slice
     * per tick (see {@link CreativeWork}); the player hears when it is finished.
     */
    public static Result applyNow(ServerPlayer player, WorkOrderType type, WorkArea area, @Nullable BlockState block) {
        return applyNow(player, type, area, block, false);
    }

    public static Result applyNow(ServerPlayer player, WorkOrderType type, WorkArea area, @Nullable BlockState block, boolean demolish) {
        int max = Config.getInstance().workOrderMaxVolume;
        if (area.volume() > max) {
            return Result.failure(Component.translatable("workorder.error.too_big", area.volume(), max));
        }
        if (type == WorkOrderType.BUILD && block == null) {
            return Result.failure(Component.translatable("workorder.error.no_block"));
        }
        CreativeWork.add(player.level(), type, area, block, demolish, player);
        return new Result(null, null, CreativeWork.isLarge(area) || CreativeWork.busy()
                ? Component.translatable("workorder.working_now", area.volume()).withStyle(ChatFormatting.GRAY)
                : Component.empty());
    }

    /**
     * Saves what the player built inside the area as a blueprint. Inside a village the usual
     * planning rights apply; elsewhere only a Villager Editor allows it.
     */
    public static Result capture(ServerPlayer player, WorkArea area, String name) {
        return capture(player, area, name, "");
    }

    /** Like {@link #capture(ServerPlayer, WorkArea, String)}, sorting the new blueprint into a section (empty: the default one). */
    public static Result capture(ServerPlayer player, WorkArea area, String name, String section) {
        if (!WorkOrderPermissions.enabled()) {
            return Result.failure(Component.translatable("workorder.error.disabled"));
        }
        Optional<Village> village = VillageManager.get(player.level()).findNearestVillage(area.center(), Village.BORDER_MARGIN);
        boolean allowed = village.map(v -> WorkOrderPermissions.check(player, v).allowed())
                .orElseGet(() -> WorkOrderPermissions.holdsEditor(player));
        if (!allowed) {
            return Result.failure(village.map(WorkOrderService::noPermission)
                    .orElseGet(() -> Component.translatable("blueprint.error.no_permission")));
        }
        BlueprintLibrary.CaptureResult result = BlueprintLibrary.capture(player.level(), area, name, player.getGameProfile().name());
        if (result.blueprint() == null) {
            return Result.failure(result.error() != null ? result.error() : Component.translatable("blueprint.error.save"));
        }
        Blueprint blueprint = result.blueprint();
        if (!section.isEmpty()) {
            BlueprintCatalog.get(player.level().getServer()).assign(blueprint.id(), section);
        }
        return new Result(null, null, Component.translatable(result.replaced() ? "blueprint.replaced" : "blueprint.saved",
                blueprint.name(), blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.solidBlocks()).withStyle(ChatFormatting.GREEN));
    }

    /** Plans a saved blueprint with its lowest corner at {@code origin}. */
    public static Result placeBlueprint(ServerPlayer player, Identifier id, BlockPos origin, int rotation) {
        if (!WorkOrderPermissions.enabled()) {
            return Result.failure(Component.translatable("workorder.error.disabled"));
        }
        Optional<Blueprint> blueprint = BlueprintLibrary.get(player.level().getServer(), id);
        if (blueprint.isEmpty()) {
            return Result.failure(Component.translatable("blueprint.error.unknown", BlueprintLibrary.displayName(id)));
        }
        if (player.isCreative()) {
            // creative: built at once, no village, builders or materials needed
            if (!BlueprintLibrary.placeNow(player.level(), id, blueprint.get(), origin, rotation)) {
                return Result.failure(Component.translatable("blueprint.error.unknown", BlueprintLibrary.displayName(id)));
            }
            return new Result(null, null, Component.translatable("blueprint.placed", BlueprintLibrary.displayName(id)).withStyle(ChatFormatting.GREEN));
        }
        WorkArea area = blueprint.get().area(origin, rotation);
        Optional<Village> found = VillageManager.get(player.level()).findNearestVillage(area.center(), Village.BORDER_MARGIN);
        if (found.isEmpty()) {
            return Result.failure(Component.translatable("workorder.error.no_village"));
        }
        Village village = found.get();
        WorkOrderPermissions.Access access = WorkOrderPermissions.check(player, village);
        if (!access.allowed()) {
            return Result.failure(noPermission(village));
        }
        VillageWorkOrders.CreateResult created = village.getWorkOrders().createBlueprint(player.level(), blueprint.get(), origin, rotation,
                player.getUUID(), player.getGameProfile().name(), access.free());
        WorkOrder order = created.order();
        if (order == null) {
            return Result.failure(created.error() != null ? created.error() : Component.translatable("workorder.error.nothing_to_do"));
        }
        return new Result(order, null, createdMessage(player.level(), village, order));
    }

    /**
     * Sorting, hiding and deleting blueprints: in creative, with a Villager Editor, or with the right to plan for the
     * village the player stands in.
     */
    public static boolean canManageBlueprints(ServerPlayer player) {
        if (!WorkOrderPermissions.enabled()) {
            return false;
        }
        if (player.isCreative() || WorkOrderPermissions.holdsEditor(player)) {
            return true;
        }
        return VillageManager.get(player.level()).findNearestVillage(player.blockPosition(), Village.BORDER_MARGIN)
                .map(village -> WorkOrderPermissions.check(player, village).allowed())
                .orElse(false);
    }

    public static Component noPermission(Village village) {
        return Component.translatable("workorder.error.no_permission",
                Component.translatable("gui.village.rank." + WorkOrderPermissions.minRank().name().toLowerCase(Locale.ROOT)),
                village.getName());
    }

    /** "Dig" or "Build with Stone". */
    public static MutableComponent describeWork(WorkOrder order) {
        BlockState block = order.getBlock();
        if (order.getType() == WorkOrderType.BLUEPRINT && order.getBlueprint() != null) {
            Identifier id = order.getBlueprint();
            return Component.translatable("workorder.type.blueprint_of",
                    Component.translatableWithFallback("blueprint.name." + id.getPath().replace('/', '.'), BlueprintLibrary.displayName(id)));
        }
        if (order.getType() == WorkOrderType.BLUEPRINT && order.isInline()) {
            return Component.translatable("workorder.type.plan");
        }
        if (order.getType() == WorkOrderType.DIG && order.isDemolish()) {
            return Component.translatable("workorder.type.demolish");
        }
        if (order.getType() == WorkOrderType.BUILD && block != null) {
            return Component.translatable("workorder.type.build_with", block.getBlock().getName());
        }
        return Component.translatable("workorder.type." + order.getType().id());
    }

    /** Villagers around the village who would take an order of this type. */
    public static int countWorkers(ServerLevel level, Village village, WorkOrderType type) {
        AABB box = new AABB(village.getCenter().getX(), village.getCenter().getY(), village.getCenter().getZ(),
                village.getCenter().getX() + 1, village.getCenter().getY() + 1, village.getCenter().getZ() + 1)
                .inflate(WORKER_SEARCH_RADIUS);
        return level.getEntitiesOfClass(VillagerEntityMCA.class, box, villager -> isWorkerFor(villager, type)).size();
    }

    public static boolean isWorkerFor(VillagerEntityMCA villager, WorkOrderType type) {
        if (villager.getProfession() == ProfessionsMCA.BUILDER) {
            return true;
        }
        return type == WorkOrderType.DIG && villager.getProfession() == ProfessionsMCA.MINER;
    }
}
