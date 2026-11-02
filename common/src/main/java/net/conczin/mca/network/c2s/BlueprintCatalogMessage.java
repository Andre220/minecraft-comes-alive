package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.workorder.BlueprintCatalog;
import net.conczin.mca.server.world.data.workorder.BlueprintLibrary;
import net.conczin.mca.server.world.data.workorder.WorkOrderService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sorting the blueprint library from the planning screen: sections, hiding and deleting blueprints. Which fields
 * matter depends on the action.
 */
public record BlueprintCatalogMessage(int action, String blueprint, String section, String name, String icon) implements HandleablePayload {
    public static final int CREATE_SECTION = 0;
    public static final int DELETE_SECTION = 1;
    public static final int MOVE = 2;
    public static final int HIDE = 3;
    public static final int SHOW = 4;
    public static final int DELETE = 5;

    public static final CustomPacketPayload.Type<BlueprintCatalogMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("blueprint_catalog"));
    public static final StreamCodec<FriendlyByteBuf, BlueprintCatalogMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlueprintCatalogMessage::action,
            ByteBufCodecs.stringUtf8(128), BlueprintCatalogMessage::blueprint,
            ByteBufCodecs.stringUtf8(64), BlueprintCatalogMessage::section,
            ByteBufCodecs.stringUtf8(64), BlueprintCatalogMessage::name,
            ByteBufCodecs.stringUtf8(128), BlueprintCatalogMessage::icon,
            BlueprintCatalogMessage::new
    );

    public static BlueprintCatalogMessage createSection(String name, String icon) {
        return new BlueprintCatalogMessage(CREATE_SECTION, "", "", name, icon);
    }

    public static BlueprintCatalogMessage ofSection(int action, String section) {
        return new BlueprintCatalogMessage(action, "", section, "", "");
    }

    public static BlueprintCatalogMessage ofBlueprint(int action, String blueprint, String section) {
        return new BlueprintCatalogMessage(action, blueprint, section, "", "");
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (!WorkOrderService.canManageBlueprints(player)) {
            player.sendSystemMessage(Component.translatable("blueprint.error.no_permission").withStyle(ChatFormatting.RED));
            return;
        }
        MinecraftServer server = player.level().getServer();
        BlueprintCatalog catalog = BlueprintCatalog.get(server);
        Identifier id = Identifier.tryParse(blueprint);
        switch (action) {
            case CREATE_SECTION -> {
                Identifier iconId = Identifier.tryParse(icon);
                String iconName = iconId != null && BuiltInRegistries.ITEM.containsKey(iconId) ? iconId.toString() : "minecraft:chest";
                if (catalog.addSection(name, iconName) == null) {
                    player.sendSystemMessage(Component.translatable("blueprint.section.error", BlueprintCatalog.MAX_SECTIONS).withStyle(ChatFormatting.RED));
                }
            }
            case DELETE_SECTION -> catalog.removeSection(section);
            case MOVE -> {
                if (id != null) {
                    catalog.assign(id, section);
                }
            }
            case HIDE, SHOW -> {
                if (id != null) {
                    catalog.setHidden(id, action == HIDE);
                }
            }
            case DELETE -> {
                if (id != null) {
                    boolean deleted = BlueprintLibrary.delete(server, id);
                    player.sendSystemMessage(deleted
                            ? Component.translatable("blueprint.deleted", BlueprintLibrary.displayName(id)).withStyle(ChatFormatting.GREEN)
                            : Component.translatable("blueprint.error.delete", BlueprintLibrary.displayName(id)).withStyle(ChatFormatting.RED));
                }
            }
            default -> {
                return;
            }
        }
        WorkOrdersRequest.sendResponse(player, player.blockPosition());
    }

    @Override
    public CustomPacketPayload.Type<BlueprintCatalogMessage> type() {
        return TYPE;
    }
}
