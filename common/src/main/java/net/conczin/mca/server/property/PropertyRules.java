package net.conczin.mca.server.property;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.expansion.economy.EconomyProperty;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PropertyManager;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who owns beds, job sites, containers, doors and trapdoors, and who may use or break them.
 * <p>
 * Players own what they place (stored in {@link PropertyManager}) and lock it with a padlock. Villagers' property
 * is derived: the bed they claimed, the job site they hold, and the home building around their bed
 * with the containers, doors and trapdoors inside, shared by everyone sleeping there. A home's
 * doors lock while all of its residents around are inside. Forcing locks and breaking villagers' things
 * are crimes, see {@link PropertyCrimes}.
 */
public final class PropertyRules {
    public enum Kind {BED, JOB_SITE, CONTAINER, DOOR}

    /** The owners of a block: a player (with lock), or the villagers it belongs to. */
    public record Owners(Kind kind, BlockPos pos, @Nullable UUID player, boolean locked, List<UUID> villagers,
                         @Nullable Village village, @Nullable VillageBuilding home) {
    }

    private PropertyRules() {
    }

    public static boolean isEnabled() {
        return Config.getInstance().villagerProperty;
    }

    public static @Nullable Kind kindOf(BlockState state) {
        if (state.is(BlockTags.BEDS)) {
            return Kind.BED;
        }
        if (state.is(TagsMCA.Blocks.OWNABLE) && (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)) {
            return Kind.DOOR;
        }
        if (state.is(TagsMCA.Blocks.OWNABLE)) {
            return Kind.CONTAINER;
        }
        if (PoiTypes.forState(state).filter(poi -> poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()) {
            return Kind.JOB_SITE;
        }
        return null;
    }

    /** The position property is stored at: the head of a bed, the lower half of a door. */
    public static BlockPos canonical(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(state.getValue(BedBlock.FACING));
        }
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    public static @Nullable Owners ownersAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Kind kind = kindOf(state);
        BlockPos at = canonical(pos, state);
        PropertyManager manager = PropertyManager.get(level);
        if (kind == null) {
            manager.remove(at);
            return null;
        }

        List<UUID> villagers = new ArrayList<>();
        Village village = VillageManager.get(level).findNearestVillage(at, 2).orElse(null);
        VillageBuilding home = null;
        if (kind == Kind.BED) {
            BlockPos foot = at.relative(state.getValue(BedBlock.FACING).getOpposite());
            if (village != null) {
                village.forEachResidentHome((resident, bed) -> {
                    if (bed.equals(at) || bed.equals(foot)) {
                        villagers.add(resident);
                    }
                });
            }
            // Also villagers nearby whose memory holds this bed, when the village has no record of it.
            for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(at).inflate(64.0), VillagerEntityMCA::isAlive)) {
                if (!villagers.contains(villager.getUUID()) && villager.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(memory -> memory.dimension() == level.dimension() && (memory.pos().equals(at) || memory.pos().equals(foot))).isPresent()) {
                    villagers.add(villager.getUUID());
                }
            }
        } else if (kind == Kind.JOB_SITE) {
            GlobalPos site = GlobalPos.of(level.dimension(), at);
            for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(at).inflate(64.0), VillagerEntityMCA::isAlive)) {
                if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site::equals).isPresent()) {
                    villagers.add(villager.getUUID());
                }
            }
        } else {
            home = village == null ? null : homeAt(village, at, kind == Kind.DOOR);
            List<UUID> workers = home == null && village != null && kind == Kind.CONTAINER
                    ? EconomyProperty.workshopOwners(level, village, at) : List.of(); // expansion: economy
            if (home != null) {
                villagers.addAll(residents(village, home));
            } else if (!workers.isEmpty()) {
                villagers.addAll(workers);
            } else {
                // No scanned building: the area around a nearby villager's bed counts as its home.
                for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(at).inflate(32.0), VillagerEntityMCA::isAlive)) {
                    Optional<GlobalPos> bed = villager.getBrain().getMemory(MemoryModuleType.HOME);
                    if (bed.isPresent() && bed.get().dimension() == level.dimension()) {
                        BedArea area = new BedArea(bed.get().pos());
                        if (isInside(area, at, kind == Kind.DOOR ? 1 : 0)) {
                            if (home == null) {
                                home = area;
                            }
                            villagers.add(villager.getUUID());
                        }
                    }
                }
            }
        }

        PropertyManager.Entry entry = manager.get(at);
        // A bed or job site a villager claimed is theirs, even when a player placed it for them.
        if (entry != null && !(villagers.isEmpty() || kind == Kind.CONTAINER || kind == Kind.DOOR)) {
            entry = null;
        }
        if (entry != null) {
            return new Owners(kind, at, entry.owner(), entry.locked(), List.of(), village, null);
        }
        return villagers.isEmpty() ? null : new Owners(kind, at, null, false, villagers, village, home);
    }

    /**
     * The home around a bed: the whole physical structure (all its rooms), or the room or external
     * building when the bed is in no structure.
     */
    public static Optional<VillageBuilding> homeOf(Village village, BlockPos bed) {
        Optional<Building> room = village.getBuildingAt(bed);
        Optional<VillageBuilding> structure = room.flatMap(village::getStructureFor).map(s -> s);
        if (structure.isPresent()) {
            return structure;
        }
        // Structures only "contain" their walkable floor cells, so match the bed by their box.
        return village.getStructures().values().stream()
                .filter(s -> isInside(s, bed, 0))
                .min(Comparator.comparingLong(PropertyRules::volume))
                .<VillageBuilding>map(s -> s)
                .or(() -> room.map(r -> r));
    }

    /** Whether a position lies in a building's box, grown by a margin (doors sit in the walls). */
    public static boolean isInside(VillageBuilding building, BlockPos pos, int margin) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return pos.getX() >= Math.min(min.getX(), max.getX()) - margin && pos.getX() <= Math.max(min.getX(), max.getX()) + margin
               && pos.getY() >= Math.min(min.getY(), max.getY()) - margin && pos.getY() <= Math.max(min.getY(), max.getY()) + margin
               && pos.getZ() >= Math.min(min.getZ(), max.getZ()) - margin && pos.getZ() <= Math.max(min.getZ(), max.getZ()) + margin;
    }

    private static long volume(VillageBuilding building) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return (long) (Math.abs(max.getX() - min.getX()) + 1) * (Math.abs(max.getY() - min.getY()) + 1) * (Math.abs(max.getZ() - min.getZ()) + 1);
    }

    /** The lived-in home containing a position; doors may sit in its walls, next to it. */
    private static @Nullable VillageBuilding homeAt(Village village, BlockPos pos, boolean door) {
        Map<Integer, VillageBuilding> homes = new HashMap<>();
        village.forEachResidentHome((resident, bed) -> homeOf(village, bed).ifPresent(home -> homes.putIfAbsent(home.getId(), home)));
        VillageBuilding best = null;
        for (VillageBuilding home : homes.values()) {
            if (isInside(home, pos, door ? 1 : 0) && (best == null || volume(home) < volume(best))) {
                best = home;
            }
        }
        return best;
    }

    private static List<UUID> residents(Village village, VillageBuilding home) {
        List<UUID> residents = new ArrayList<>();
        village.forEachResidentHome((resident, bed) -> {
            if (homeOf(village, bed).filter(h -> h.getId() == home.getId() && h.getClass() == home.getClass()).isPresent()) {
                residents.add(resident);
            }
        });
        return residents;
    }

    /** A home is locked while every resident is loaded, alive and inside it. */
    public static boolean isLocked(ServerLevel level, Owners owners) {
        VillageBuilding home = owners.home();
        if (home == null) {
            return false;
        }
        // Residents: the village's record, corrected by what nearby villagers remember as their bed
        // (someone who moved out no longer counts, someone the record missed does).
        Set<UUID> residents = new HashSet<>(owners.villagers());
        AABB around = AABB.encapsulatingFullBlocks(home.getPos0(), home.getPos1()).inflate(48.0);
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, around, VillagerEntityMCA::isAlive)) {
            Optional<GlobalPos> bed = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (bed.isEmpty()) {
                continue;
            }
            if (bed.get().dimension() == level.dimension() && isInside(home, bed.get().pos(), 0)) {
                residents.add(villager.getUUID());
            } else {
                residents.remove(villager.getUUID());
            }
        }
        // Locked once every resident around is inside; residents away (not loaded) do not hold it open.
        int present = 0;
        for (UUID resident : residents) {
            if (level.getEntity(resident) instanceof VillagerEntityMCA villager && villager.isAlive()) {
                if (!isInside(home, villager.blockPosition(), 0)) {
                    return false;
                }
                present++;
            }
        }
        return present > 0;
    }

    private static boolean bypasses(ServerPlayer player) {
        return player.isCreative() || Config.getInstance().propertyOpsBypass
                                      && player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(2)));
    }

    /** Spouse, family, mayor of the village, or (except for beds) a good friend of an owner. */
    private static boolean isWelcome(ServerPlayer player, ServerLevel level, Owners owners) {
        FamilyTree tree = FamilyTree.get(level);
        for (UUID owner : owners.villagers()) {
            Optional<FamilyTreeNode> node = tree.getOrEmpty(owner);
            if (node.filter(n -> n.partner().equals(player.getUUID()) || n.isRelative(player.getUUID())).isPresent()) {
                return true;
            }
            if (owners.kind() != Kind.BED && level.getEntity(owner) instanceof VillagerEntityMCA villager
                && villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= Config.getInstance().propertyFriendHearts) {
                return true;
            }
        }
        return owners.village() != null && Tasks.getRank(owners.village(), player).isAtLeast(Rank.MAYOR);
    }

    public static Component ownerName(ServerLevel level, Owners owners) {
        UUID id = owners.player() != null ? owners.player() : owners.villagers().getFirst();
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        return FamilyTree.get(level).getOrEmpty(id).map(FamilyTreeNode::getName).<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("property.someone"));
    }

    /** Every lived-in home of a village with its residents, as owners of its doors. */
    public static List<Owners> homesOf(Village village) {
        Map<String, VillageBuilding> homes = new HashMap<>();
        Map<String, List<UUID>> residents = new HashMap<>();
        village.forEachResidentHome((resident, bed) -> homeOf(village, bed).ifPresent(home -> {
            String key = home.getClass().getSimpleName() + home.getId();
            homes.putIfAbsent(key, home);
            residents.computeIfAbsent(key, ignored -> new ArrayList<>()).add(resident);
        }));
        List<Owners> result = new ArrayList<>();
        homes.forEach((key, home) -> result.add(new Owners(Kind.DOOR, home.getPos0(), null, false, residents.get(key), village, home)));
        return result;
    }

    /**
     * The home of a villager whose village has no building around its bed: a box around the bed,
     * six blocks across each way and three up and down.
     */
    public record BedArea(BlockPos bed) implements VillageBuilding {
        @Override
        public int getId() {
            return bed.hashCode();
        }

        @Override
        public BlockPos getPos0() {
            return bed.offset(-6, -3, -6);
        }

        @Override
        public BlockPos getPos1() {
            return bed.offset(6, 3, 6);
        }

        @Override
        public BlockPos getCenter() {
            return bed;
        }

        @Override
        public boolean containsPos(net.minecraft.core.Vec3i pos) {
            return isInside(this, new BlockPos(pos), 0);
        }
    }

    /** The other half of a double chest, or null. */
    public static @Nullable BlockPos chestPartner(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state));
        }
        return null;
    }

    /**
     * The owners that rule a block. A double chest opens as one container, so its more protected
     * half rules it.
     */
    public static @Nullable Owners rulingOwners(ServerLevel level, BlockPos pos) {
        Owners owners = ownersAt(level, pos);
        BlockPos partner = chestPartner(level, pos);
        if (partner == null) {
            return owners;
        }
        Owners other = ownersAt(level, partner);
        if (owners == null || other != null && !isPadlocked(level, owners) && isPadlocked(level, other)) {
            return other;
        }
        return owners;
    }

    /**
     * Whether a padlock keeps strangers out: a player's padlock, a villager's container (unless a
     * crowbar broke its lock), a villager's home door while everyone is inside.
     */
    public static boolean isPadlocked(ServerLevel level, Owners owners) {
        if (owners.player() != null) {
            return owners.locked();
        }
        boolean broken = PropertyManager.get(level).isLockBroken(owners.pos(), level.getGameTime());
        return switch (owners.kind()) {
            case CONTAINER -> !broken;
            case DOOR -> !broken && isLocked(level, owners);
            default -> false;
        };
    }

    /** A crowbar destroys the padlock: a player's is gone, a villager replaces theirs after a day. */
    public static void breakPadlock(ServerLevel level, Owners owners) {
        PropertyManager manager = PropertyManager.get(level);
        BlockPos partner = chestPartner(level, owners.pos());
        for (BlockPos pos : partner == null ? List.of(owners.pos()) : List.of(owners.pos(), partner)) {
            if (owners.player() != null) {
                manager.set(pos, owners.player(), false);
            } else {
                manager.breakLock(pos, level.getGameTime() + 24000);
            }
        }
    }

    private static boolean isLockable(Kind kind) {
        return kind == Kind.CONTAINER || kind == Kind.DOOR;
    }

    /**
     * Before a player uses a block. Returns true when the use is handled or denied (the caller
     * cancels the normal use). A padlock in hand locks one's own (or an unowned) container or door;
     * sneaking with an empty hand takes one's padlock back.
     */
    public static boolean onUse(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack, InteractionHand hand) {
        if (!isEnabled()) {
            return false;
        }
        boolean padlockInHand = stack.is(ItemsMCA.PADLOCK);
        if (player.isSecondaryUseActive() && !stack.isEmpty() && !PropertyCrimes.isBurglaryTool(stack) && !padlockInHand) {
            return false; // using the item against the block, not the block itself
        }
        Kind kind = kindOf(level.getBlockState(pos));
        if (kind == null) {
            return false;
        }
        Owners owners = rulingOwners(level, pos);

        if (padlockInHand && isLockable(kind) && (owners == null || player.getUUID().equals(owners.player()))) {
            attachPadlock(player, level, pos, owners, stack);
            return true;
        }
        if (owners == null) {
            return false;
        }

        if (owners.player() != null) {
            if (owners.player().equals(player.getUUID())) {
                if (player.isSecondaryUseActive() && stack.isEmpty() && owners.locked()) {
                    removePadlock(player, level, owners);
                    return true;
                }
                return false;
            }
            if (!owners.locked() || bypasses(player)) {
                return false;
            }
            denyOrForce(player, level, pos, owners, stack, hand, Component.translatable("property.denied", ownerName(level, owners)));
            return true;
        }

        if (isLockable(kind) && !isPadlocked(level, owners) || bypasses(player) || isWelcome(player, level, owners)) {
            return false;
        }
        denyOrForce(player, level, pos, owners, stack, hand, Component.translatable(
                kind == Kind.DOOR ? "property.locked_home" : "property.denied", ownerName(level, owners)));
        return true;
    }

    private static void attachPadlock(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable Owners owners, ItemStack stack) {
        if (owners != null && owners.locked()) {
            player.sendOverlayMessage(Component.translatable("property.padlock.already"));
            return;
        }
        PropertyManager manager = PropertyManager.get(level);
        BlockPos at = canonical(pos, level.getBlockState(pos));
        BlockPos partner = chestPartner(level, at);
        for (BlockPos lockPos : partner == null ? List.of(at) : List.of(at, partner)) {
            manager.set(lockPos, player.getUUID(), true);
        }
        stack.consume(1, player);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.sendOverlayMessage(Component.translatable("property.padlock.attached"));
        resync(player, level, pos);
    }

    private static void removePadlock(ServerPlayer player, ServerLevel level, Owners owners) {
        PropertyManager manager = PropertyManager.get(level);
        BlockPos partner = chestPartner(level, owners.pos());
        for (BlockPos lockPos : partner == null ? List.of(owners.pos()) : List.of(owners.pos(), partner)) {
            PropertyManager.Entry entry = manager.get(lockPos);
            if (entry != null && entry.owner().equals(player.getUUID())) {
                manager.set(lockPos, entry.owner(), false);
            }
        }
        ItemStack padlock = new ItemStack(ItemsMCA.PADLOCK);
        if (!player.getInventory().add(padlock)) {
            player.drop(padlock, false);
        }
        level.playSound(null, owners.pos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.sendOverlayMessage(Component.translatable("property.padlock.removed"));
    }

    /**
     * Before a player breaks a block. Returns true when denied: another player's padlocked block.
     * Breaking a villager's things is allowed, but it is a crime (see PropertyCrimes). Breaking
     * one's own padlocked block gives the padlock back.
     */
    public static boolean onBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!isEnabled()) {
            return false;
        }
        Owners owners = ownersAt(level, pos);
        if (owners == null) {
            return false;
        }
        if (owners.player() != null) {
            if (owners.player().equals(player.getUUID())) {
                if (owners.locked()) {
                    Block.popResource(level, pos, new ItemStack(ItemsMCA.PADLOCK));
                    PropertyManager.get(level).remove(owners.pos());
                }
                return false;
            }
            if (!owners.locked() || bypasses(player)) {
                return false;
            }
            deny(player, level, pos, Component.translatable("property.denied", ownerName(level, owners)));
            return true;
        }
        if (bypasses(player) || isWelcome(player, level, owners)) {
            return false;
        }
        // Allowed, but it is a crime: loud, and costly if anyone sees it.
        PropertyCrimes.commit(player, level, pos, owners, PropertyCrimes.LOUD);
        return false;
    }

    /**
     * After a player places a block: it is theirs, unlocked until they put a padlock on it. Not in
     * creative mode, which is for building (a chest placed in a villager's home then belongs to
     * the residents).
     */
    public static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!isEnabled() || player.isCreative()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (kindOf(state) != null) {
            PropertyManager.get(level).set(canonical(pos, state), player.getUUID(), false);
        }
    }

    /** Whether a villager may take from or put into a container (its home's, or an unlocked player's). */
    public static boolean canVillagerUse(VillagerEntityMCA villager, ServerLevel level, BlockPos pos) {
        if (!isEnabled()) {
            return true;
        }
        Owners owners = ownersAt(level, pos);
        if (owners == null) {
            return true;
        }
        if (owners.player() != null) {
            return !owners.locked();
        }
        return owners.villagers().contains(villager.getUUID());
    }

    /** Containers and home doors can be forced open (see PropertyCrimes); anything else is just denied. */
    private static void denyOrForce(ServerPlayer player, ServerLevel level, BlockPos pos, Owners owners, ItemStack stack,
                                    InteractionHand hand, Component message) {
        if (PropertyCrimes.canForce(owners) && PropertyCrimes.tryForce(player, level, pos, owners, stack, hand)) {
            resync(player, level, pos);
            return;
        }
        deny(player, level, pos, message);
    }

    private static void deny(ServerPlayer player, ServerLevel level, BlockPos pos, Component message) {
        player.sendOverlayMessage(message);
        resync(player, level, pos);
    }

    private static void resync(ServerPlayer player, ServerLevel level, BlockPos pos) {
        // The client may have predicted the use (a door swinging open): send the real state back.
        for (BlockPos near : List.of(pos, pos.above(), pos.below())) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, near));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos.relative(direction)));
        }
    }
}
