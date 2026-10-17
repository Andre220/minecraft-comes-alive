package net.conczin.mca.server.world.data.workorder;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
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

    public static Result create(ServerPlayer player, WorkOrderType type, WorkArea area, @Nullable BlockState block) {
        if (!WorkOrderPermissions.enabled()) {
            return Result.failure(Component.translatable("workorder.error.disabled"));
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
                player.getUUID(), player.getGameProfile().name(), access.free());
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

    public static Component noPermission(Village village) {
        return Component.translatable("workorder.error.no_permission",
                Component.translatable("gui.village.rank." + WorkOrderPermissions.minRank().name().toLowerCase(Locale.ROOT)),
                village.getName());
    }

    /** "Dig" or "Build with Stone". */
    public static MutableComponent describeWork(WorkOrder order) {
        BlockState block = order.getBlock();
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
