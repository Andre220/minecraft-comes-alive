package net.conczin.mca.server.property;

import net.conczin.mca.mixin.ItemDisplayAccessor;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.PropertyManager;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows padlocks in the world: an item display of the padlock item on every padlocked chest
 * latch, barrel lid, door handle and trapdoor near players. It covers players' padlocks, villagers'
 * containers (unless their lock was broken) and the doors of homes locked for the night. The
 * displays are plain vanilla entities, so they reach every client without extra syncing; this keeps
 * them in line with the property rules every two seconds.
 */
public final class PadlockDisplays {
    private static final String TAG = "mca_padlock";
    private static final int INTERVAL = 40;
    private static final double RANGE = 40.0;
    private static final double SURFACE = 0.02;

    private record Placement(Vec3 pos, float yaw, float pitch) {
        String key() {
            return String.format(Locale.ROOT, "%s:%d,%d,%d,%d", TAG, Math.round(pos.x * 100), Math.round(pos.y * 100), Math.round(pos.z * 100), Math.round(yaw));
        }
    }

    private PadlockDisplays() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 0) {
            return;
        }
        boolean enabled = PropertyRules.isEnabled();
        for (ServerPlayer player : level.players()) {
            Map<String, Placement> wanted = enabled ? wanted(level, player.blockPosition()) : new HashMap<>();
            AABB area = player.getBoundingBox().inflate(RANGE + 8.0);
            for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, area, e -> e.entityTags().contains(TAG))) {
                String key = display.entityTags().stream().filter(tag -> tag.startsWith(TAG + ":")).findFirst().orElse("");
                if (wanted.remove(key) == null) {
                    display.discard();
                }
            }
            wanted.values().forEach(placement -> spawn(level, placement));
        }
    }

    private static void spawn(ServerLevel level, Placement placement) {
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return;
        }
        ((ItemDisplayAccessor) display).mca$setItemStack(new ItemStack(ItemsMCA.PADLOCK));
        ((ItemDisplayAccessor) display).mca$setItemTransform(ItemDisplayContext.FIXED);
        display.setPos(placement.pos());
        display.setYRot(placement.yaw());
        display.setXRot(placement.pitch());
        display.addTag(TAG);
        display.addTag(placement.key());
        level.addFreshEntity(display);
    }

    /** Where padlocks should show around a position. */
    private static Map<String, Placement> wanted(ServerLevel level, BlockPos center) {
        Map<String, Placement> wanted = new HashMap<>();
        double rangeSqr = RANGE * RANGE;

        // Players' padlocks.
        PropertyManager.get(level).entries().forEach((packed, entry) -> {
            BlockPos pos = BlockPos.of(packed);
            if (entry.locked() && pos.distSqr(center) < rangeSqr) {
                add(wanted, level, pos);
            }
        });

        // Villagers' chests and barrels.
        int minX = SectionPos.blockToSectionCoord(center.getX() - Mth.floor(RANGE));
        int maxX = SectionPos.blockToSectionCoord(center.getX() + Mth.floor(RANGE));
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - Mth.floor(RANGE));
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + Mth.floor(RANGE));
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity blockEntity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (!(blockEntity instanceof Container) || pos.distSqr(center) >= rangeSqr
                        || PropertyRules.kindOf(blockEntity.getBlockState()) != PropertyRules.Kind.CONTAINER) {
                        continue;
                    }
                    PropertyRules.Owners owners = PropertyRules.ownersAt(level, pos);
                    if (owners != null && owners.player() == null && PropertyRules.isPadlocked(level, owners)) {
                        add(wanted, level, pos);
                    }
                }
            }
        }

        // Doors and trapdoors of homes locked for the night.
        VillageManager.get(level).findVillages(village -> village.isWithinBorder(center, (int) RANGE)).forEach(village -> {
            for (PropertyRules.Owners home : PropertyRules.homesOf(village)) {
                if (home.home() == null || !PropertyRules.isLocked(level, home)) {
                    continue;
                }
                BlockPos min = home.home().getPos0();
                BlockPos max = home.home().getPos1();
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.min(min, max).offset(-1, -1, -1), BlockPos.max(min, max).offset(1, 1, 1))) {
                    BlockState state = level.getBlockState(pos);
                    if (PropertyRules.kindOf(state) == PropertyRules.Kind.DOOR && PropertyRules.canonical(pos, state).equals(pos)
                        && !PropertyManager.get(level).isLockBroken(pos, level.getGameTime())) {
                        add(wanted, level, pos.immutable());
                    }
                }
            }
        });
        return wanted;
    }

    private static void add(Map<String, Placement> wanted, ServerLevel level, BlockPos pos) {
        for (Placement placement : placements(level, pos)) {
            wanted.put(placement.key(), placement);
        }
    }

    /** Where a padlock sits on a block: the chest latch, the barrel lid, the door handle (both sides), the trapdoor. */
    private static List<Placement> placements(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            Direction facing = state.getValue(ChestBlock.FACING);
            // In front of the latch, which sticks out a pixel past the chest body, and centered on it.
            Vec3 front = Vec3.atCenterOf(pos).add(facing.getStepX() * (0.5 + SURFACE), 0.0, facing.getStepZ() * (0.5 + SURFACE));
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction toPartner = ChestBlock.getConnectedDirection(state);
                front = front.add(toPartner.getStepX() * 0.5, 0.0, toPartner.getStepZ() * 0.5);
            }
            return List.of(new Placement(front, facing.toYRot(), 0.0F));
        }
        if (state.getBlock() instanceof BarrelBlock) {
            Direction facing = state.getValue(BarrelBlock.FACING);
            Vec3 face = Vec3.atCenterOf(pos).add(facing.getStepX() * (0.5 + SURFACE), facing.getStepY() * (0.5 + SURFACE), facing.getStepZ() * (0.5 + SURFACE));
            float pitch = facing == Direction.UP ? -90.0F : facing == Direction.DOWN ? 90.0F : 0.0F;
            float yaw = facing.getAxis().isHorizontal() ? facing.toYRot() : 0.0F;
            return List.of(new Placement(face, yaw, pitch));
        }
        if (state.getBlock() instanceof DoorBlock) {
            if (state.getValue(DoorBlock.OPEN) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                return List.of();
            }
            AABB panel = state.getShape(level, pos).bounds().move(pos);
            Direction facing = state.getValue(DoorBlock.FACING);
            Direction handle = state.getValue(DoorBlock.HINGE) == net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                    ? facing.getClockWise() : facing.getCounterClockWise();
            Vec3 middle = panel.getCenter().add(handle.getStepX() * 0.3, pos.getY() + 0.95 - panel.getCenter().y, handle.getStepZ() * 0.3);
            double half = (facing.getAxis() == Direction.Axis.X ? panel.getXsize() : panel.getZsize()) / 2.0 + SURFACE;
            return List.of(
                    new Placement(middle.add(facing.getStepX() * half, 0.0, facing.getStepZ() * half), facing.toYRot(), 0.0F),
                    new Placement(middle.add(-facing.getStepX() * half, 0.0, -facing.getStepZ() * half), facing.getOpposite().toYRot(), 0.0F));
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            if (state.getValue(TrapDoorBlock.OPEN)) {
                return List.of();
            }
            AABB panel = state.getShape(level, pos).bounds().move(pos);
            return List.of(new Placement(new Vec3(panel.getCenter().x, panel.maxY + SURFACE, panel.getCenter().z), 0.0F, -90.0F));
        }
        return List.of();
    }
}
