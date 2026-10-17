package net.conczin.mca.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.VillageWorkOrders;
import net.conczin.mca.server.world.data.workorder.WorkArea;
import net.conczin.mca.server.world.data.workorder.WorkOrder;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.conczin.mca.server.world.data.workorder.WorkOrderType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * {@code /mca workorder}: plan, list and cancel the work orders of the village you are in.
 */
public class WorkOrderCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal(MCA.MOD_ID).then(Commands.literal("workorder")
                .then(Commands.literal("dig")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(ctx -> create(ctx, WorkOrderType.DIG, null)))))
                .then(Commands.literal("build")
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("block", BlockStateArgument.block(context))
                                                .executes(ctx -> create(ctx, WorkOrderType.BUILD,
                                                        BlockStateArgument.getBlock(ctx, "block").getState()))))))
                .then(Commands.literal("list").executes(WorkOrderCommand::list))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(WorkOrderCommand::cancel)))
                .then(Commands.literal("pause")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> setPaused(ctx, true))))
                .then(Commands.literal("resume")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> setPaused(ctx, false))))
                .then(Commands.literal("clear").executes(WorkOrderCommand::clear))
        ));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, WorkOrderType type, @Nullable BlockState block) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        WorkOrderService.Result result = WorkOrderService.create(player, type, WorkArea.of(from, to), block);
        if (result.error() != null) {
            ctx.getSource().sendFailure(result.error());
            return 0;
        }
        ctx.getSource().sendSuccess(result::message, false);
        return 1;
    }

    private static Optional<Village> villageOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Village> village = VillageManager.get(player.level()).findNearestVillage(player.blockPosition(), Village.BORDER_MARGIN);
        if (village.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("workorder.error.no_village"));
        }
        return village;
    }

    /** The village the player stands in, if they may manage its orders. */
    private static Optional<Village> managedVillage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Village> village = villageOf(ctx);
        if (village.isPresent()) {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!WorkOrderPermissions.check(player, village.get()).allowed()) {
                ctx.getSource().sendFailure(WorkOrderService.noPermission(village.get()));
                return Optional.empty();
            }
        }
        return village;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Village> village = villageOf(ctx);
        if (village.isEmpty()) {
            return 0;
        }
        VillageWorkOrders orders = village.get().getWorkOrders();
        if (orders.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("workorder.list.empty", village.get().getName()), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("workorder.list.header", village.get().getName())
                .withStyle(ChatFormatting.GOLD), false);
        for (WorkOrder order : orders.getOrders()) {
            ctx.getSource().sendSuccess(() -> describe(order), false);
        }
        return orders.getOrders().size();
    }

    static MutableComponent describe(WorkOrder order) {
        WorkArea area = order.getArea();
        MutableComponent what = WorkOrderService.describeWork(order);
        MutableComponent line = Component.translatable("workorder.list.entry",
                order.getId(),
                what,
                area.minX() + " " + area.minY() + " " + area.minZ(),
                area.maxX() + " " + area.maxY() + " " + area.maxZ(),
                Math.round(order.getProgress() * 100.0F)
        );
        if (order.isPaused()) {
            line.append(Component.translatable("workorder.list.paused").withStyle(ChatFormatting.GRAY));
        } else if (order.isWaitingForMaterial()) {
            order.getMaterial().ifPresent(item -> line.append(Component.translatable("workorder.list.waiting",
                    item.getDefaultInstance().getHoverName()).withStyle(ChatFormatting.YELLOW)));
        }
        return line;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Village> village = managedVillage(ctx);
        if (village.isEmpty()) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(ctx, "id");
        if (!village.get().getWorkOrders().cancel(id)) {
            ctx.getSource().sendFailure(Component.translatable("workorder.error.unknown", id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("workorder.cancelled", id), false);
        return 1;
    }

    private static int setPaused(CommandContext<CommandSourceStack> ctx, boolean paused) throws CommandSyntaxException {
        Optional<Village> village = managedVillage(ctx);
        if (village.isEmpty()) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(ctx, "id");
        if (!village.get().getWorkOrders().setPaused(id, paused)) {
            ctx.getSource().sendFailure(Component.translatable("workorder.error.unknown", id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(paused ? "workorder.paused" : "workorder.resumed", id), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Village> village = managedVillage(ctx);
        if (village.isEmpty()) {
            return 0;
        }
        int count = village.get().getWorkOrders().clear();
        ctx.getSource().sendSuccess(() -> Component.translatable("workorder.cleared", count), false);
        return count;
    }
}
