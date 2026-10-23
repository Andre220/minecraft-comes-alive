package net.conczin.mca.server.property;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crimes against property and the villagers who notice them.
 * <p>
 * A player forces a padlock open by sneaking and holding use (slow, quiet), with a lockpick
 * (slower, silent, used up; the padlock stays) or a crowbar (instant, loud, wears down and destroys
 * the padlock), or breaks a villager's things (loud).
 * Villagers who <b>see</b> it become witnesses: within range (shorter when sneaking or in the dark),
 * in front of them and with a clear line of sight (glass, panes, fences and leaves do not hide it).
 * Villagers who only <b>hear</b> it shout and come to look; if they find the player there, they
 * witness it too. Witnesses shout, cost hearts with themselves and the owner, alert guards (warning,
 * then attack) and note the crime in the civil registry.
 */
public final class PropertyCrimes {
    public static final int QUIET = 3;
    public static final int LOUD = 8;
    private static final int SILENT = 0;
    private static final double VIEW_CONE_COS = 0.5; // 120 degrees
    private static final double ALWAYS_NOTICED = 1.5;
    private static final int MAX_SHOUTS = 2;
    private static final long INVESTIGATION_TICKS = 400;
    private static final double INVESTIGATION_REACH = 8.0;
    private static final long FORCE_GAP_TICKS = 10;

    private record Forcing(BlockPos pos, long start, long last, boolean lockpick, long lastSightCheck) {
    }

    private record Investigation(UUID player, BlockPos pos, PropertyRules.Owners owners, long until) {
    }

    private static final Map<UUID, Forcing> FORCING = new HashMap<>();
    /** The progress bar (a boss bar: text above, colored bar below) of each player forcing a lock. */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final int SIGHT_CHECK_TICKS = 20;
    private static final Map<UUID, Investigation> INVESTIGATIONS = new HashMap<>();

    private PropertyCrimes() {
    }

    /** Containers and home doors can be forced; beds and job sites cannot. */
    public static boolean canForce(PropertyRules.Owners owners) {
        return owners.kind() == PropertyRules.Kind.CONTAINER || owners.kind() == PropertyRules.Kind.DOOR;
    }

    public static boolean isBurglaryTool(ItemStack stack) {
        return stack.is(ItemsMCA.CROWBAR) || stack.is(ItemsMCA.LOCKPICK);
    }

    /**
     * A denied use of a forceable block: tries to force it with the item in hand. Returns false
     * when the player is not trying to force it (the caller shows the ownership message).
     */
    public static boolean tryForce(ServerPlayer player, ServerLevel level, BlockPos pos, PropertyRules.Owners owners,
                                   ItemStack stack, InteractionHand hand) {
        var config = Config.getInstance();
        if (stack.is(ItemsMCA.CROWBAR)) {
            stack.hurtAndBreak(10, player, hand);
            level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 0.8F, 1.2F);
            // The crowbar destroys the padlock; a lockpick or bare hands leave it in place.
            PropertyRules.breakPadlock(level, owners);
            open(player, level, pos);
            commit(player, level, pos, owners, LOUD);
            return true;
        }
        // Sneaking also sends a use for the empty off hand: it must not count as forcing by hand.
        if (hand == InteractionHand.OFF_HAND) {
            return isBurglaryTool(player.getMainHandItem()) || player.isSecondaryUseActive() && player.getMainHandItem().isEmpty();
        }
        boolean lockpick = stack.is(ItemsMCA.LOCKPICK);
        if (!lockpick && !(player.isSecondaryUseActive() && stack.isEmpty())) {
            return false;
        }

        float seconds = lockpick ? config.lockpickSeconds : config.forceOpenSeconds;
        long time = level.getGameTime();
        Forcing forcing = FORCING.get(player.getUUID());
        if (forcing == null || !forcing.pos().equals(owners.pos()) || forcing.lockpick() != lockpick || time - forcing.last() > FORCE_GAP_TICKS) {
            forcing = new Forcing(owners.pos(), time, time, lockpick, time);
        }

        // Anyone looking catches the player in the act.
        long lastSightCheck = forcing.lastSightCheck();
        if (time - lastSightCheck >= SIGHT_CHECK_TICKS) {
            lastSightCheck = time;
            if (seenBy(player, level)) {
                stopForcing(player);
                commit(player, level, pos, owners, SILENT);
                return true;
            }
        }

        float progress = (time - forcing.start()) / (seconds * 20.0F);
        if (progress < 1.0F) {
            FORCING.put(player.getUUID(), new Forcing(forcing.pos(), forcing.start(), time, lockpick, lastSightCheck));
            showBar(player, lockpick, progress);
            if (!lockpick && time % 10 == 0) {
                level.playSound(null, pos, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.3F, 0.6F);
            }
            return true;
        }

        stopForcing(player);
        if (lockpick) {
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
        open(player, level, pos);
        commit(player, level, pos, owners, lockpick ? SILENT : QUIET);
        return true;
    }

    private static void showBar(ServerPlayer player, boolean lockpick, float progress) {
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
                Component.translatable(lockpick ? "crime.lockpicking" : "crime.forcing"),
                lockpick ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
        bar.setName(Component.translatable(lockpick ? "crime.lockpicking" : "crime.forcing"));
        bar.setColor(lockpick ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        bar.setProgress(progress);
        bar.addPlayer(player);
    }

    private static void stopForcing(ServerPlayer player) {
        FORCING.remove(player.getUUID());
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** Clears the bar of players who let go of the use button. */
    public static void tickForcing(ServerLevel level) {
        long time = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            Forcing forcing = FORCING.get(player.getUUID());
            if (forcing != null && time - forcing.last() > FORCE_GAP_TICKS) {
                stopForcing(player);
            }
        }
    }

    private static boolean seenBy(ServerPlayer player, ServerLevel level) {
        double range = Config.getInstance().crimeSightRange;
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(range), VillagerEntityMCA::isAlive)) {
            if (sees(villager, player)) {
                return true;
            }
        }
        return false;
    }

    /** Uses the block as if it were not locked: opens the container, swings the door. */
    private static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** A crime happened: villagers who see it witness it, villagers who hear it come to look. */
    public static void commit(ServerPlayer player, ServerLevel level, BlockPos pos, PropertyRules.Owners owners, int noise) {
        double range = Math.max(Config.getInstance().crimeSightRange, noise);
        List<VillagerEntityMCA> nearby = level.getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(range), VillagerEntityMCA::isAlive);
        Set<UUID> witnesses = new HashSet<>();
        int shouts = 0;
        for (VillagerEntityMCA villager : nearby) {
            if (sees(villager, player)) {
                witness(villager, player, owners, shouts++ < MAX_SHOUTS);
                witnesses.add(villager.getUUID());
            } else if (noise > 0 && villager.distanceToSqr(Vec3.atCenterOf(pos)) <= noise * noise) {
                hear(villager, player, level, pos, owners, noise, shouts++ < MAX_SHOUTS);
            }
        }
        if (!witnesses.isEmpty()) {
            punish(player, level, owners, witnesses);
        }
    }

    private static void witness(VillagerEntityMCA villager, ServerPlayer player, PropertyRules.Owners owners, boolean shout) {
        boolean owner = owners.villagers().contains(villager.getUUID());
        if (shout) {
            villager.sendChatToAllAround(owner ? "villager.crime.owner" : "villager.crime.witness");
        }
        if (!owner) {
            villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-Config.getInstance().crimeWitnessHearts);
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        INVESTIGATIONS.remove(villager.getUUID());
    }

    /** Once per crime: the owners learn of it, guards are alerted, the registry notes it. */
    private static void punish(ServerPlayer player, ServerLevel level, PropertyRules.Owners owners, Set<UUID> witnesses) {
        for (UUID owner : owners.villagers()) {
            if (level.getEntity(owner) instanceof VillagerEntityMCA villager) {
                villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-Config.getInstance().crimeOwnerHearts);
            }
        }
        for (UUID id : witnesses) {
            if (level.getEntity(id) instanceof VillagerEntityMCA witness) {
                for (VillagerEntityMCA guard : level.getEntitiesOfClass(VillagerEntityMCA.class, witness.getBoundingBox().inflate(32.0), VillagerEntityMCA::isGuard)) {
                    guard.reportCrime(player);
                }
                break;
            }
        }
        if (owners.village() != null) {
            owners.village().getCivilRegistry().ifPresent(registry -> registry.addText(
                    Component.translatable("civil_registry.theft", player.getName(), PropertyRules.ownerName(level, owners))));
        }
        player.sendOverlayMessage(Component.translatable(witnesses.size() == 1 ? "crime.seen.one" : "crime.seen.many", witnesses.size()));
    }

    private static void hear(VillagerEntityMCA villager, ServerPlayer player, ServerLevel level, BlockPos pos,
                             PropertyRules.Owners owners, int noise, boolean shout) {
        if (villager.isSleeping()) {
            // Only a loud noise close by wakes a sleeper.
            if (noise < LOUD || villager.distanceToSqr(Vec3.atCenterOf(pos)) > 6 * 6) {
                return;
            }
            villager.stopSleeping();
        }
        if (shout) {
            villager.sendChatToAllAround("villager.crime.heard");
        }
        INVESTIGATIONS.put(villager.getUUID(), new Investigation(player.getUUID(), pos.immutable(), owners, level.getGameTime() + INVESTIGATION_TICKS));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.6F, 2));
    }

    /** A villager who heard something walks over; finding the player there makes it a witness. */
    public static void tickInvestigation(VillagerEntityMCA villager) {
        if (villager.tickCount % 10 != 0 || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        Investigation investigation = INVESTIGATIONS.get(villager.getUUID());
        if (investigation == null) {
            return;
        }
        if (level.getGameTime() > investigation.until() || !villager.isAlive()) {
            INVESTIGATIONS.remove(villager.getUUID());
            return;
        }
        if (level.getPlayerByUUID(investigation.player()) instanceof ServerPlayer player
            && player.distanceToSqr(Vec3.atCenterOf(investigation.pos())) < INVESTIGATION_REACH * INVESTIGATION_REACH
            && sees(villager, player)) {
            witness(villager, player, investigation.owners(), true);
            punish(player, level, investigation.owners(), Set.of(villager.getUUID()));
            return;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(investigation.pos())) > 4.0
            && villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty()) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(investigation.pos(), 0.6F, 2));
        }
    }

    /** Whether a villager sees the player: awake, in range, in front of it and with a clear view. */
    public static boolean sees(VillagerEntityMCA villager, ServerPlayer player) {
        if (villager.isSleeping() || player.isInvisible() || player.isSpectator()) {
            return false;
        }
        double range = Config.getInstance().crimeSightRange;
        if (player.isShiftKeyDown()) {
            range *= 0.5;
        }
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) < 7) {
            range *= 0.5;
        }
        double distanceSqr = villager.distanceToSqr(player);
        if (distanceSqr > range * range) {
            return false;
        }
        Vec3 eye = villager.getEyePosition();
        if (distanceSqr > ALWAYS_NOTICED * ALWAYS_NOTICED) {
            Vec3 toPlayer = player.getEyePosition().subtract(eye).normalize();
            if (villager.getViewVector(1.0F).dot(toPlayer) < VIEW_CONE_COS) {
                return false;
            }
        }
        return clearView(villager, eye, player.getEyePosition()) || clearView(villager, eye, player.position().add(0.0, player.getBbHeight() * 0.4, 0.0));
    }

    /** A line of sight that windows, fences and leaves do not block. */
    private static boolean clearView(VillagerEntityMCA villager, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from).normalize().scale(0.05);
        Vec3 start = from;
        for (int i = 0; i < 16; i++) {
            BlockHitResult hit = villager.level().clip(new ClipContext(start, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, villager));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (!seeThrough(villager.level().getBlockState(hit.getBlockPos()))) {
                return false;
            }
            // Continue from just past the see-through block.
            BlockPos through = hit.getBlockPos();
            start = hit.getLocation();
            for (int step = 0; step < 40 && BlockPos.containing(start).equals(through); step++) {
                start = start.add(direction);
            }
        }
        return false;
    }

    private static boolean seeThrough(BlockState state) {
        var block = state.getBlock();
        return block instanceof TransparentBlock && !(block instanceof TintedGlassBlock)
               || block instanceof IronBarsBlock
               || block instanceof FenceBlock
               || state.is(BlockTags.LEAVES);
    }
}
