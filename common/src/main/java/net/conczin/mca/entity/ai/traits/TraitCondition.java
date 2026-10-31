package net.conczin.mca.entity.ai.traits;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A situation a trait cares about, e.g. a loner enjoying being alone. Conditions are checked
 * on demand through a shared {@link Context}, so the nearby entities and blocks are looked up
 * at most once per check, and only when some trait of the villager needs them.
 */
public enum TraitCondition {
    ALONE(c -> c.villagers(8.0).isEmpty()),
    IN_COMPANY(c -> c.villagers(6.0).size() >= 2),
    OUTDOORS(c -> c.level().canSeeSky(c.villager.blockPosition().above())),
    RAINING(c -> c.level().isRaining() && c.level().canSeeSky(c.villager.blockPosition().above())),
    AT_HOME(c -> c.villager.getNeeds().isAtHome()),
    WORKING(c -> c.villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE) == Activity.WORK),
    NEAR_ANIMALS(c -> c.entities(8.0).stream().anyMatch(e -> e instanceof Animal)),
    NEAR_CHILDREN(c -> c.villagers(8.0).stream().anyMatch(v -> isChild(v.getAgeState()))),
    NEAR_SPOUSE(c -> c.partner().map(id -> c.villagers(8.0).stream().anyMatch(v -> v.getUUID().equals(id))).orElse(false)),
    NEAR_PLANTS(c -> c.blocks().plants),
    NEAR_GRAVES(c -> c.blocks().graves),
    NEAR_FIRE(c -> c.blocks().fire),
    FIGHTING(c -> c.villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)),
    UNDERGROUND(c -> c.villager.getY() < c.level().getSeaLevel() && !c.level().canSeeSky(c.villager.blockPosition().above())),
    WEALTHY(c -> c.villager.getInventory().countItem(Items.EMERALD) >= 5),
    HUNGRY(c -> c.villager.getNeeds().hasNeeds() && c.villager.getNeeds().get(Needs.Need.HUNGER) < 30.0F),
    LONELY(c -> c.villager.getNeeds().hasNeeds() && c.villager.getNeeds().get(Needs.Need.SOCIAL) < 30.0F),
    /** Kleptomaniacs: content after swiping something, itchy after days without (known on the server only). */
    SWIPED_LATELY(c -> within(TraitBehaviors.sinceLastSwipe(c.villager), 0, TraitBehaviors.SWIPE_CONTENT)),
    ITCHING_TO_SWIPE(c -> TraitBehaviors.sinceLastSwipe(c.villager) > TraitBehaviors.SWIPE_ITCH),
    /** Erratic villagers: some days up, some days down, different for each villager. */
    GOOD_DAY(c -> c.moodSwing() == 0),
    BAD_DAY(c -> c.moodSwing() == 1);

    private final Predicate<Context> test;

    TraitCondition(Predicate<Context> test) {
        this.test = test;
    }

    public boolean test(Context context) {
        return context.cached(this);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The situation in words, e.g. "alone", for effect lines. */
    public Component getName() {
        return Component.translatable("trait.condition." + id());
    }

    private static boolean within(long value, long min, long max) {
        return value >= min && value <= max;
    }

    private static boolean isChild(AgeState age) {
        return age == AgeState.TODDLER || age == AgeState.CHILD;
    }

    /** What the conditions of one villager look at, looked up lazily and kept for one check. */
    public static final class Context {
        private static final int BLOCK_RADIUS = 3;

        private final VillagerEntityMCA villager;
        private final Boolean[] results = new Boolean[values().length];
        private @Nullable List<LivingEntity> entities;
        private @Nullable Blocks blocks;

        public Context(VillagerEntityMCA villager) {
            this.villager = villager;
        }

        private boolean cached(TraitCondition condition) {
            Boolean result = results[condition.ordinal()];
            if (result == null) {
                result = condition.test.test(this);
                results[condition.ordinal()] = result;
            }
            return result;
        }

        private Level level() {
            return villager.level();
        }

        private List<LivingEntity> entities(double range) {
            if (entities == null) {
                entities = level().getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(8.0),
                        e -> e != villager && e.isAlive());
            }
            double rangeSqr = range * range;
            return entities.stream().filter(e -> e.distanceToSqr(villager) < rangeSqr).toList();
        }

        private List<VillagerEntityMCA> villagers(double range) {
            return entities(range).stream().filter(VillagerEntityMCA.class::isInstance).map(VillagerEntityMCA.class::cast).toList();
        }

        private Optional<UUID> partner() {
            return villager.getRelationships().getPartnerUUID();
        }

        private int moodSwing() {
            long day = level().getOverworldClockTime() / 24000L;
            return (int) Math.floorMod(day + villager.getUUID().getLeastSignificantBits(), 3L);
        }

        private Blocks blocks() {
            if (blocks == null) {
                blocks = new Blocks();
                BlockPos feet = villager.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-BLOCK_RADIUS, -1, -BLOCK_RADIUS), feet.offset(BLOCK_RADIUS, 1, BLOCK_RADIUS))) {
                    BlockState state = level().getBlockState(pos);
                    if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)) {
                        blocks.plants = true;
                    } else if (state.getBlock() instanceof TombstoneBlock) {
                        blocks.graves = true;
                    }
                    if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && state.getValueOrElse(BlockStateProperties.LIT, false)
                        || state.getValueOrElse(BlockStateProperties.LIT, false) && state.is(net.minecraft.world.level.block.Blocks.FURNACE)) {
                        blocks.fire = true;
                    }
                }
            }
            return blocks;
        }
    }

    private static final class Blocks {
        private boolean plants;
        private boolean graves;
        private boolean fire;
    }
}
