package net.conczin.mca.expansion.minds.routine;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.registry.ParticleTypesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Free time with a purpose: instead of walking to random spots, a villager looks at what its
 * surroundings offer (see {@link OpportunityScanner}), picks what suits it best right now (its
 * needs, what is close, what it did lately) and does it for a while: sits on a bench, chats with a
 * friend, reads at the lectern, listens to music, warms up at the fire, pets the cat...
 * If nothing is around, it at least goes and stands with the others.
 */
public class IdleRoutineTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 4000;
    private static final int WALK_TIMEOUT = 400;
    private static final double REACH_SQR = 1.6 * 1.6;
    private static final int RECENT = 3;
    private static final int NEED_TICK = 20;
    private static final double BAND_RANGE = 10.0;

    private enum Phase {WALK, DO}

    private final Deque<Opportunity.Kind> recent = new ArrayDeque<>();
    private @Nullable Opportunity current;
    private @Nullable Phase phase;
    private int ticks;
    private int duration;
    /** The item the routine put in the villager's hands, taken away again at the end. */
    private @Nullable ItemStack held;
    private boolean ridesPartner;

    public IdleRoutineTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT
        ), MAX_DURATION);
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerRoutines;
    }

    public @Nullable Opportunity current() {
        return current;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!enabled() || villager.isSleeping() || villager.getVillagerBrain().isPanicking() || villager.getVehicle() instanceof Player) {
            return false;
        }
        if (Seats.isSitting(villager)) {
            Seats.standUp(villager); // left seated by a reload: start over
        } else if (villager.getVehicle() != null && OpportunityTypes.forEntity(villager.getVehicle()) instanceof OpportunityTypes.Type seat
                   && seat.kind() == Opportunity.Kind.SIT) {
            villager.stopRiding(); // still on another mod's chair after a reload
        }
        current = choose(level, villager, OpportunityScanner.scan(level, villager));
        return current != null;
    }

    /** The most attractive opportunity: appeal, need, closeness and variety weighed together, with a bit of chance. */
    @Nullable Opportunity choose(ServerLevel level, VillagerEntityMCA villager, List<Opportunity> opportunities) {
        Needs needs = villager.getNeeds();
        Opportunity best = null;
        double bestScore = 0;
        for (Opportunity opportunity : opportunities) {
            double score = opportunity.appeal() * opportunity.kind().weight();
            if (needs.hasNeeds()) {
                // the lower the need, the more it pulls: from 1x when full to 3x when empty
                score *= 1.0 + 2.0 * (1.0 - needs.get(opportunity.kind().need()) / Needs.MAX);
            }
            score /= 1.0 + Math.sqrt(opportunity.spot().distSqr(villager.blockPosition())) / 8.0;
            if (recent.contains(opportunity.kind())) {
                score *= 0.35;
            }
            score *= 0.6 + villager.getRandom().nextDouble() * 0.8;
            if (score > bestScore) {
                bestScore = score;
                best = opportunity;
            }
        }
        return best;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (current == null) {
            return;
        }
        phase = Phase.WALK;
        ticks = 0;
        Opportunity.Kind kind = current.kind();
        duration = kind.minTicks() + villager.getRandom().nextInt(Math.max(1, kind.maxTicks() - kind.minTicks()));
        recent.addLast(kind);
        while (recent.size() > RECENT) {
            recent.removeFirst();
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long time) {
        return current != null && phase != null
               && !villager.getVillagerBrain().isPanicking()
               && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
               && villager.getBrain().getActiveNonCoreActivity().filter(a -> a == Activity.IDLE).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long time) {
        if (current == null || phase == null) {
            return;
        }
        ticks++;
        if (phase == Phase.WALK) {
            BlockPos target = current.partner() != null && current.kind() != Opportunity.Kind.SIT
                    ? OpportunityScanner.besides(level, current.partner().blockPosition(), villager.blockPosition())
                    : current.spot();
            if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(target)) <= REACH_SQR) {
                arrive(level, villager);
            } else if (ticks > WALK_TIMEOUT || current.partner() != null && !current.partner().isAlive()) {
                phase = null;
            } else {
                villager.moveTowardsPersistent(target, 0.5F, 0);
            }
            return;
        }
        doIt(level, villager);
        if (ticks >= duration) {
            phase = null;
        }
    }

    private void arrive(ServerLevel level, VillagerEntityMCA villager) {
        phase = Phase.DO;
        ticks = 0;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        if (current == null) {
            return;
        }
        if (current.kind() == Opportunity.Kind.SIT) {
            Entity chair = current.partner();
            if (chair != null) {
                // furniture of another mod: ride it like a player would, and leave it there afterwards
                ridesPartner = chair.isAlive() && chair.getPassengers().isEmpty() && villager.startRiding(chair, true, true);
                if (!ridesPartner) {
                    phase = null;
                    return;
                }
            } else if (!Seats.sit(level, villager, current.spot())) {
                phase = null; // someone was faster
                return;
            }
        }
        if (current.kind() == Opportunity.Kind.FEAST && current.action() == null && !Feasts.takeServing(level, villager, current.lookAt())) {
            phase = null; // all eaten up
            return;
        }
        if (current.hold() != null && villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty()) {
            held = current.hold().copy();
            if (current.kind() == Opportunity.Kind.PLAY) {
                joinBand(level, villager, held);
            }
            villager.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        if (current.action() != null) {
            current.action().start(level, villager, current);
        }
    }

    private void doIt(ServerLevel level, VillagerEntityMCA villager) {
        Opportunity opportunity = current;
        if (opportunity == null) {
            return;
        }
        Entity partner = opportunity.partner();
        if (Seats.isSitting(villager) && villager.getVehicle() != null) {
            villager.setYBodyRot(villager.getVehicle().getYRot()); // sitting the right way round on the seat
        }
        if (partner != null) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(partner, true));
        } else if (opportunity.lookAt() != null) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(opportunity.lookAt()));
        } else {
            lookAtSomeone(level, villager);
        }
        switch (opportunity.kind()) {
            case CHAT -> {
                if (partner instanceof VillagerEntityMCA friend) {
                    friend.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(villager, true));
                }
                if (ticks % 80 == 0) {
                    villager.swing(InteractionHand.MAIN_HAND);
                    particle(level, villager.position().add(0, villager.getBbHeight() + 0.4, 0), ParticleTypesMCA.POS_INTERACTION);
                }
            }
            case INSPECT -> {
                // turning it over in the hands
                if (held != null && ticks % 40 == 0) {
                    boolean inMain = villager.getMainHandItem() == held;
                    villager.setItemInHand(inMain ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    villager.setItemInHand(inMain ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, held);
                    villager.swing(inMain ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                }
            }
            case FEAST -> {
                if (ticks % 40 == 0) {
                    villager.swing(InteractionHand.MAIN_HAND);
                }
            }
            case PET -> {
                if (partner != null && villager.distanceToSqr(partner) > 4.0) {
                    villager.moveTowardsPersistent(partner.blockPosition(), 0.4F, 1);
                } else if (partner != null && ticks % 50 == 0) {
                    villager.swing(InteractionHand.MAIN_HAND);
                    particle(level, partner.position().add(0, partner.getBbHeight() + 0.2, 0), ParticleTypes.HEART);
                }
            }
            case NEAR_OTHERS -> {
                if (partner != null && villager.distanceToSqr(partner) > 9.0) {
                    villager.moveTowardsPersistent(OpportunityScanner.besides(level, partner.blockPosition(), villager.blockPosition()), 0.4F, 1);
                }
            }
            default -> {
            }
        }
        if (opportunity.particle() != null && ticks % 60 == 30) {
            Vec3 at = partner != null ? partner.position().add(0, partner.getBbHeight() + 0.3, 0)
                    : opportunity.lookAt() != null ? Vec3.atCenterOf(opportunity.lookAt()).add(0, 0.8, 0)
                    : villager.position().add(0, villager.getBbHeight() + 0.3, 0);
            particle(level, at, opportunity.particle());
        }
        if (opportunity.action() != null) {
            opportunity.action().tick(level, villager, opportunity, ticks);
        }
        Needs needs = villager.getNeeds();
        if (needs.hasNeeds() && ticks % NEED_TICK == 0) {
            needs.add(opportunity.kind().need(), 0.4F);
        }
    }

    /**
     * Someone nearby already playing? Then play along: take over the state of their instrument (what
     * is played and since when), so the music stays in time. Works for any mod that keeps it on the item.
     */
    static void joinBand(ServerLevel level, VillagerEntityMCA villager, ItemStack instrument) {
        OpportunityTypes.Type type = OpportunityTypes.forItem(instrument);
        if (type == null) {
            return;
        }
        level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(BAND_RANGE),
                        v -> v != villager && v.isAlive() && type.matches(v.getMainHandItem()))
                .stream().findFirst()
                .ifPresent(leader -> instrument.applyComponents(leader.getMainHandItem().getComponentsPatch()));
    }

    private static void lookAtSomeone(ServerLevel level, VillagerEntityMCA villager) {
        level.getEntitiesOfClass(VillagerEntityMCA.class, villager.getBoundingBox().inflate(8), v -> v != villager && v.isAlive())
                .stream().findFirst()
                .ifPresent(other -> villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(other, true)));
    }

    private static void particle(ServerLevel level, Vec3 at, ParticleOptions particle) {
        level.sendParticles(particle, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long time) {
        Seats.standUp(villager);
        if (ridesPartner) {
            if (current != null && villager.getVehicle() == current.partner()) {
                villager.stopRiding();
            }
            ridesPartner = false;
        }
        if (held != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (villager.getItemInHand(hand) == held) {
                    villager.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
            held = null;
        }
        if (current != null && current.action() != null) {
            current.action().stop(level, villager, current);
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        current = null;
        phase = null;
    }
}
