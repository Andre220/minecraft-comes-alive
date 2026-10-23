package net.conczin.mca.client.debug;

import net.conczin.mca.KeyBindings;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.conczin.mca.server.property.PropertyRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world debug view, active while holding the villager editor: the last villager under the
 * crosshair shows its look and AI data above its head, plus its path and destination in
 * singleplayer, and the places it remembers (bed, job site, meeting point, home building). The
 * freeze key toggles its AI. Beds around show who sleeps where (see {@link BedOverlay}). The full
 * data is in {@code VillagerDebugScreen}.
 */
public final class VillagerDebugTool {
    private static final double MAX_DISTANCE = 48.0;
    private static final float TEXT_SCALE = 0.4F;
    private static final double ROW_HEIGHT = 0.18;
    private static final int PATH_COLOR = 0xFF55FF88;
    private static final int TARGET_COLOR = 0xFFFF5555;

    private static int selectedId = -1;

    private VillagerDebugTool() {
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            selectedId = -1;
            return;
        }
        if (!player.getMainHandItem().is(ItemsMCA.VILLAGER_EDITOR) && !player.getOffhandItem().is(ItemsMCA.VILLAGER_EDITOR)) {
            // Presses outside the tool must not pile up for later.
            while (KeyBindings.DEBUG_FREEZE.consumeClick()) {
            }
            return;
        }

        if (client.crosshairPickEntity instanceof Villager target) {
            selectedId = target.getId();
        }
        Villager villager = client.level.getEntity(selectedId) instanceof Villager v && v.isAlive() ? v : null;
        if (villager != null && villager.distanceTo(player) > MAX_DISTANCE) {
            villager = null;
        }

        @Nullable Mob server = villager == null ? null : VillagerDebugInfo.serverMob(villager);
        if (villager != null && KeyBindings.DEBUG_FREEZE.consumeClick()) {
            player.sendSystemMessage(VillagerDebugInfo.toggleFreeze(server));
        }

        try (var ignored = client.collectPerTickGizmos()) {
            drawProperty(client);
            BedOverlay.draw(client);
            if (villager == null) {
                return;
            }
            // the look and AI data live in the editor's Inspect tab; over the head it was unreadable
            if (server != null) {
                drawPath(villager, server);
                drawPointsOfInterest(villager, server);
            }
        }
    }

    /** Marks the places the villager remembers, with a line from its head to each one. */
    private static void drawPointsOfInterest(Villager villager, Mob server) {
        Vec3 eyes = villager.getEyePosition();
        for (VillagerDebugInfo.PointOfInterest point : VillagerDebugInfo.pointsOfInterest(server)) {
            if (point.pos().dimension() != villager.level().dimension()) {
                continue;
            }
            BlockPos pos = point.pos().pos();
            int fill = (point.color() & 0x00FFFFFF) | 0x40000000;
            Gizmos.cuboid(pos, 0.02F, GizmoStyle.strokeAndFill(point.color(), 2.0F, fill)).setAlwaysOnTop();
            Gizmos.line(eyes, Vec3.atCenterOf(pos), point.color(), 1.0F).setAlwaysOnTop();
            String block = villager.level().getBlockState(pos).getBlock().getName().getString();
            Gizmos.billboardText(point.label() + ": " + block, Vec3.atCenterOf(pos).add(0.0, 0.9, 0.0),
                    TextGizmo.Style.forColorAndCentered(point.color()).withScale(TEXT_SCALE)).setAlwaysOnTop();
        }

        Building building = VillagerDebugInfo.homeBuilding(server);
        if (building != null) {
            AABB box = new AABB(Vec3.atLowerCornerOf(building.getPos0()), Vec3.atLowerCornerOf(building.getPos1()).add(1.0, 1.0, 1.0));
            Gizmos.cuboid(box, GizmoStyle.stroke(VillagerDebugInfo.HOME_BUILDING_COLOR, 2.0F));
            Gizmos.billboardText("Home: " + building.getType(), new Vec3(box.getCenter().x, box.maxY + 0.5, box.getCenter().z),
                    TextGizmo.Style.forColorAndCentered(VillagerDebugInfo.HOME_BUILDING_COLOR).withScale(TEXT_SCALE * 1.5F)).setAlwaysOnTop();
        }
    }

    /** Who owns the block under the crosshair, as the server sees it (singleplayer). */
    private static void drawProperty(Minecraft client) {
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || client.level == null) {
            return;
        }
        var integrated = client.getSingleplayerServer();
        ServerLevel level = integrated == null ? null : integrated.getLevel(client.level.dimension());
        if (level == null || !PropertyRules.isEnabled()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        List<String> lines = new ArrayList<>();
        try {
            PropertyRules.Kind kind = PropertyRules.kindOf(level.getBlockState(pos));
            if (kind == null) {
                return;
            }
            PropertyRules.Owners owners = PropertyRules.ownersAt(level, pos);
            if (owners == null) {
                lines.add(kind + ": no owner");
            } else if (owners.player() != null) {
                lines.add(kind + ": player " + PropertyRules.ownerName(level, owners).getString() + (owners.locked() ? " (locked)" : " (unlocked)"));
            } else {
                lines.add(kind + ": " + owners.villagers().size() + " villager(s), first " + PropertyRules.ownerName(level, owners).getString());
                if (owners.home() != null) {
                    lines.add("home " + owners.home().getClass().getSimpleName() + " #" + owners.home().getId()
                              + " " + owners.home().getPos0().toShortString() + " to " + owners.home().getPos1().toShortString()
                              + (PropertyRules.isLocked(level, owners) ? "  LOCKED (all inside)" : ""));
                }
            }
        } catch (RuntimeException e) {
            lines.add("property: " + e.getClass().getSimpleName());
        }
        Vec3 base = Vec3.atCenterOf(pos).add(0.0, 0.9, 0.0);
        for (int i = 0; i < lines.size(); i++) {
            Gizmos.billboardText(lines.get(i), base.add(0.0, (lines.size() - 1 - i) * ROW_HEIGHT, 0.0),
                    TextGizmo.Style.forColorAndCentered(0xFFFFAA55).withScale(TEXT_SCALE)).setAlwaysOnTop();
        }
    }

    private static void drawPath(Villager villager, Mob server) {
        try {
            Path path = server.getNavigation().getPath();
            if (path != null && path.getNodeCount() > 0) {
                Vec3 previous = null;
                for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
                    Vec3 node = Vec3.atBottomCenterOf(path.getNode(i).asBlockPos()).add(0.0, 0.1, 0.0);
                    if (previous == null) {
                        Gizmos.arrow(villager.position().add(0.0, 0.1, 0.0), node, PATH_COLOR);
                    } else {
                        Gizmos.line(previous, node, PATH_COLOR);
                    }
                    previous = node;
                }
                Gizmos.cuboid(path.getTarget(), 0.05F, GizmoStyle.stroke(TARGET_COLOR));
            }
            server.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
                BlockPos pos = target.getTarget().currentBlockPosition();
                Gizmos.cuboid(pos, 0.1F, GizmoStyle.stroke(TARGET_COLOR, 1.5F));
            });
        } catch (RuntimeException ignored) {
            // Server-side state changed while reading; skip this tick.
        }
    }
}
