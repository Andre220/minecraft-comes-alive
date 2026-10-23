package net.conczin.mca.client.debug;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Part of the villager editor's debug view (singleplayer): every bed around with who claims it,
 * who the village has living there and who sleeps in it, and the villagers without a bed. A
 * summary goes to the action bar.
 */
final class BedOverlay {
    private static final int RADIUS = 32;
    private static final int SCAN_INTERVAL = 20;
    private static final float TEXT_SCALE = 0.4F;
    private static final double ROW_HEIGHT = 0.18;
    private static final int FREE = 0xFF55FF55;
    private static final int CLAIMED = 0xFFFFFF55;
    private static final int SLEEPING = 0xFF55FFFF;
    private static final int PROBLEM = 0xFFFF5555;

    private record Mark(Vec3 at, int color, List<String> lines, @Nullable BlockPos bed) {
    }

    private static List<Mark> marks = List.of();
    private static long nextScan;

    private BedOverlay() {
    }

    static void draw(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        var integrated = client.getSingleplayerServer();
        ServerLevel level = integrated == null ? null : integrated.getLevel(client.level.dimension());
        if (level == null) {
            return;
        }
        if (client.level.getGameTime() >= nextScan) {
            nextScan = client.level.getGameTime() + SCAN_INTERVAL;
            try {
                scan(client, level);
            } catch (RuntimeException e) {
                marks = List.of(); // server state changed while reading
            }
        }
        for (Mark mark : marks) {
            if (mark.bed() != null) {
                Gizmos.cuboid(mark.bed(), 0.02F, GizmoStyle.stroke(mark.color(), 1.5F)).setAlwaysOnTop();
            }
            for (int i = 0; i < mark.lines().size(); i++) {
                Gizmos.billboardText(mark.lines().get(i), mark.at().add(0.0, (mark.lines().size() - 1 - i) * ROW_HEIGHT, 0.0),
                        TextGizmo.Style.forColorAndCentered(mark.color()).withScale(TEXT_SCALE)).setAlwaysOnTop();
            }
        }
    }

    private static void scan(Minecraft client, ServerLevel level) {
        BlockPos center = client.player.blockPosition();
        List<VillagerEntityMCA> villagers = level.getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(center).inflate(RADIUS * 2), VillagerEntityMCA::isAlive);

        // what the village has on record: resident -> bed
        Map<BlockPos, List<UUID>> recorded = new HashMap<>();
        Optional<Village> village = VillageManager.get(level).findNearestVillage(center, RADIUS);
        village.ifPresent(v -> v.forEachResidentHome((resident, bed) -> recorded.computeIfAbsent(head(level, bed), k -> new ArrayList<>()).add(resident)));

        record Bed(BlockPos head, List<String> memory, List<String> record, boolean reserved, @Nullable String sleeper) {
        }
        List<Bed> list = new ArrayList<>();
        Map<String, Integer> appearances = new HashMap<>();
        PoiManager poi = level.getPoiManager();
        for (BlockPos bed : poi.findAll(type -> type.is(PoiTypes.HOME), pos -> true, center, RADIUS, PoiManager.Occupancy.ANY).toList()) {
            BlockPos head = head(level, bed);
            List<String> memory = new ArrayList<>();
            String sleeper = null;
            for (VillagerEntityMCA villager : villagers) {
                Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
                if (home.filter(h -> h.dimension() == level.dimension() && head(level, h.pos()).equals(head)).isPresent()) {
                    memory.add(villager.getName().getString());
                }
                if (villager.getSleepingPos().map(p -> head(level, p)).filter(head::equals).isPresent()) {
                    sleeper = villager.getName().getString();
                }
            }
            List<String> record = recorded.getOrDefault(head, List.of()).stream().map(id -> name(level, id)).toList();
            // the Minecraft side: a bed's ticket is taken by whoever claimed it
            boolean reserved = poi.getCountInRange(type -> type.is(PoiTypes.HOME), bed, 0, PoiManager.Occupancy.HAS_SPACE) == 0;
            java.util.Set<String> names = new java.util.HashSet<>(memory);
            names.addAll(record);
            names.forEach(n -> appearances.merge(n, 1, Integer::sum));
            list.add(new Bed(head, memory, record, reserved, sleeper));
        }

        List<Mark> found = new ArrayList<>();
        int beds = list.size();
        int free = 0;
        for (Bed bed : list) {
            List<String> lines = new ArrayList<>();
            int color;
            if (bed.memory().isEmpty() && bed.record().isEmpty() && !bed.reserved()) {
                lines.add("Cama livre");
                color = FREE;
                free++;
            } else {
                color = CLAIMED;
                lines.add("memória: " + (bed.memory().isEmpty() ? "ninguém" : String.join(", ", bed.memory())));
                lines.add("registro da vila: " + (bed.record().isEmpty() ? "ninguém" : String.join(", ", bed.record())));
                lines.add(bed.reserved() ? "reservada" : "sem reserva");
                if (!new java.util.HashSet<>(bed.memory()).equals(new java.util.HashSet<>(bed.record()))) {
                    lines.add("memória e registro discordam");
                    color = PROBLEM;
                }
                if (bed.reserved() && bed.memory().isEmpty() && bed.record().isEmpty()) {
                    lines.add("reserva presa: ninguém dorme aqui");
                    color = PROBLEM;
                }
                if (bed.memory().size() > 1) {
                    lines.add(bed.memory().size() + " aldeões disputam esta cama");
                    color = PROBLEM;
                }
                java.util.Set<String> names = new java.util.TreeSet<>(bed.memory());
                names.addAll(bed.record());
                for (String name : names) {
                    if (appearances.getOrDefault(name, 0) > 1) {
                        lines.add(name + " aparece em " + appearances.get(name) + " camas");
                        color = PROBLEM;
                    }
                }
            }
            if (bed.sleeper() != null) {
                lines.add("dormindo: " + bed.sleeper());
                if (color != PROBLEM) {
                    color = SLEEPING;
                }
            }
            found.add(new Mark(Vec3.atCenterOf(bed.head()).add(0.0, 0.8, 0.0), color, lines, bed.head()));
        }

        int homeless = 0;
        for (VillagerEntityMCA villager : villagers) {
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) || villager.distanceToSqr(client.player) > RADIUS * RADIUS) {
                continue;
            }
            homeless++;
            String line = villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.SHELTER_BED) ? "sem cama (abrigado numa casa)" : "sem cama";
            found.add(new Mark(villager.position().add(0.0, villager.getBbHeight() + 0.6, 0.0), PROBLEM, List.of(line), null));
        }
        marks = found;
        client.player.sendOverlayMessage(Component.literal("Camas: " + beds + " (" + free + " livres) · aldeões sem cama: " + homeless));
    }

    private static BlockPos head(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(state.getValue(BedBlock.FACING));
        }
        return pos.immutable();
    }

    private static String name(ServerLevel level, UUID id) {
        return FamilyTree.get(level).getOrEmpty(id).map(FamilyTreeNode::getName).filter(Objects::nonNull).orElse("?");
    }
}
