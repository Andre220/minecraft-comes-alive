package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrder;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the planning screen shows about the village the player plans for: its work
 * orders, what the storage holds, who may plan and who will do the work.
 */
public record WorkOrdersResponse(
        int villageId,
        String villageName,
        int access,
        String minRank,
        boolean hasStorage,
        int builders,
        int miners,
        int maxVolume,
        List<OrderInfo> orders,
        Map<String, Integer> storage
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<WorkOrdersResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("work_orders_response"));
    public static final StreamCodec<FriendlyByteBuf, WorkOrdersResponse> STREAM_CODEC = StreamCodec.of(
            (buf, message) -> message.write(buf),
            WorkOrdersResponse::read
    );
    /** Most different blocks of the storage reported to the client. */
    private static final int MAX_STORAGE_ENTRIES = 256;

    /** A work order as the client sees it. {@code block} is a block id, empty for digging. */
    public record OrderInfo(int id, WorkOrderType type, WorkArea area, String block, float progress,
                            int remaining, boolean paused, boolean waiting, boolean free) {
        static OrderInfo of(WorkOrder order) {
            BlockState state = order.getBlock();
            return new OrderInfo(
                    order.getId(),
                    order.getType(),
                    order.getArea(),
                    state == null ? "" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    order.getProgress(),
                    order.getRemainingWork(),
                    order.isPaused(),
                    order.isWaitingForMaterial(),
                    order.isFree()
            );
        }

        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(type.ordinal());
            buf.writeVarIntArray(area.toArray());
            buf.writeUtf(block);
            buf.writeFloat(progress);
            buf.writeVarInt(remaining);
            buf.writeBoolean(paused);
            buf.writeBoolean(waiting);
            buf.writeBoolean(free);
        }

        static OrderInfo read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            WorkOrderType type = WorkOrderType.values()[Math.floorMod(buf.readVarInt(), WorkOrderType.values().length)];
            WorkArea area = WorkArea.fromArray(buf.readVarIntArray(6));
            return new OrderInfo(id, type, area, buf.readUtf(), buf.readFloat(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public static WorkOrdersResponse none() {
        return new WorkOrdersResponse(-1, "", WorkOrderPermissions.Access.DENIED.ordinal(),
                rankKey(), false, 0, 0, 0, List.of(), Map.of());
    }

    public static WorkOrdersResponse of(ServerPlayer player, Village village) {
        ServerLevel level = player.level();
        List<OrderInfo> orders = village.getWorkOrders().getOrders().stream().map(OrderInfo::of).toList();

        Map<String, Integer> storage = new HashMap<>();
        BlockPos center = new BlockPos(village.getCenter());
        for (BlockPos pos : VillageStorage.containers(level, village, center)) {
            Container container = VillageStorage.getContainer(level, pos).orElse(null);
            if (container == null) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.getItem() instanceof BlockItem && (storage.size() < MAX_STORAGE_ENTRIES || storage.containsKey(key(stack)))) {
                    storage.merge(key(stack), stack.getCount(), Integer::sum);
                }
            }
        }

        return new WorkOrdersResponse(
                village.getId(),
                village.getName(),
                WorkOrderPermissions.check(player, village).ordinal(),
                rankKey(),
                VillageStorage.exists(village),
                countProfession(level, village, true),
                countProfession(level, village, false),
                Config.getInstance().workOrderMaxVolume,
                orders,
                storage
        );
    }

    private static String key(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String rankKey() {
        return "gui.village.rank." + WorkOrderPermissions.minRank().name().toLowerCase(Locale.ROOT);
    }

    private static int countProfession(ServerLevel level, Village village, boolean builders) {
        // builders also dig, so the miner count only adds the dedicated ones
        int all = WorkOrderService.countWorkers(level, village, builders ? WorkOrderType.BUILD : WorkOrderType.DIG);
        if (builders) {
            return all;
        }
        return all - WorkOrderService.countWorkers(level, village, WorkOrderType.BUILD);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villageId);
        buf.writeUtf(villageName);
        buf.writeVarInt(access);
        buf.writeUtf(minRank);
        buf.writeBoolean(hasStorage);
        buf.writeVarInt(builders);
        buf.writeVarInt(miners);
        buf.writeVarInt(maxVolume);
        buf.writeCollection(orders, (b, order) -> order.write(b));
        buf.writeMap(storage, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
    }

    private static WorkOrdersResponse read(FriendlyByteBuf buf) {
        int villageId = buf.readVarInt();
        String villageName = buf.readUtf();
        int access = buf.readVarInt();
        String minRank = buf.readUtf();
        boolean hasStorage = buf.readBoolean();
        int builders = buf.readVarInt();
        int miners = buf.readVarInt();
        int maxVolume = buf.readVarInt();
        List<OrderInfo> orders = new ArrayList<>(buf.readList(OrderInfo::read));
        Map<String, Integer> storage = buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        return new WorkOrdersResponse(villageId, villageName, access, minRank, hasStorage, builders, miners, maxVolume, orders, storage);
    }

    public boolean hasVillage() {
        return villageId >= 0;
    }

    public WorkOrderPermissions.Access getAccess() {
        WorkOrderPermissions.Access[] values = WorkOrderPermissions.Access.values();
        return values[Math.floorMod(access, values.length)];
    }

    public int stored(String itemId) {
        return storage.getOrDefault(itemId, 0);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleWorkOrders(this);
    }

    @Override
    public CustomPacketPayload.Type<WorkOrdersResponse> type() {
        return TYPE;
    }
}
