package net.conczin.mca.expansion.minds.routine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Looks around a villager for what its surroundings offer: seats, people to chat with, the bell,
 * lecterns and bookshelves, music, fire, animals, flowers and crops, water, home.
 */
public final class OpportunityScanner {
    private static final int RADIUS = 12;
    private static final int BELOW = 3;
    private static final int ABOVE = 3;
    private static final int ENTITY_RADIUS = 16;
    /** Opportunities of one kind kept from a scan, nearest first. */
    private static final int PER_KIND = 3;

    private OpportunityScanner() {
    }

    public static List<Opportunity> scan(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        Map<Opportunity.Kind, List<Opportunity>> found = new EnumMap<>(Opportunity.Kind.class);
        Village village = villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager)).orElse(null);

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-RADIUS, -BELOW, -RADIUS), origin.offset(RADIUS, ABOVE, RADIUS))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockPos at = pos.immutable();
            if (Seats.isSeat(level, at)) {
                add(found, new Opportunity(Opportunity.Kind.SIT, at, null, null, 1.0F));
            } else if (OpportunityTypes.forBlock(state) instanceof OpportunityTypes.Type type
                       && (type.kind() != Opportunity.Kind.FEAST || Feasts.hasServing(state))) {
                // data driven: lecterns, music, fires, flowers, other mods' blocks (shelves often sit in the
                // walls, so rooms are checked where the villager stands)
                if (type.contents()) {
                    // what lies on it: instruments, food, things to look at
                    standNear(level, at).ifPresent(spot -> ContainerOpportunities.offer(level, villager, at, spot, type,
                            type.appealFor(roomAt(village, spot), villager.getProfession()), opportunity -> add(found, opportunity)));
                } else {
                    standNear(level, at).ifPresent(spot -> add(found, new Opportunity(type.kind(), spot, at, null,
                            type.appealFor(roomAt(village, spot), villager.getProfession()), type.holdStack(), type.particle(), null)));
                }
            } else if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                standNear(level, at).ifPresent(spot -> add(found, new Opportunity(Opportunity.Kind.ADMIRE, spot, at, null, 1.0F)));
            } else if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()
                       && level.getBlockState(at.above()).isAir()) {
                standNear(level, at).ifPresent(spot -> add(found, new Opportunity(Opportunity.Kind.WATER, spot, at, null, 1.0F)));
            }
        }

        // people: chat with idle villagers, spouses first
        VillagerEntityMCA spouse = villager.getRelationships().getPartner().orElse(null) instanceof VillagerEntityMCA s ? s : null;
        for (VillagerEntityMCA other : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(origin).inflate(ENTITY_RADIUS),
                v -> v != villager && v.isAlive() && !v.isSleeping() && v.getAgeState() != AgeState.BABY)) {
            boolean idle = other.getBrain().getActiveNonCoreActivity().filter(a -> a == Activity.IDLE || a == Activity.MEET).isPresent();
            float appeal = (other == spouse ? 2.0F : 1.0F) * (idle ? 1.0F : 0.3F);
            BlockPos spot = besides(level, other.blockPosition(), origin);
            add(found, new Opportunity(Opportunity.Kind.CHAT, spot, other.blockPosition().above(), other, appeal));
            add(found, new Opportunity(Opportunity.Kind.NEAR_OTHERS, spot, other.blockPosition().above(), other, 1.0F));
            // someone making music: come and listen
            if (OpportunityTypes.forItem(other.getMainHandItem()) instanceof OpportunityTypes.Type music && music.kind() == Opportunity.Kind.PLAY) {
                add(found, new Opportunity(Opportunity.Kind.LISTEN, spot, other.blockPosition().above(), other, 1.6F, null, null, null));
            }
        }

        // what the villager carries: an instrument is played wherever it is
        for (OpportunityTypes.Type type : OpportunityTypes.itemTypes()) {
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack carried = villager.getInventory().getItem(slot);
                if (type.matches(carried)) {
                    add(found, new Opportunity(type.kind(), origin, null, null, type.appealFor(roomAt(village, origin), villager.getProfession()),
                            new ItemStack(carried.getItem()), type.particle(), null));
                    break;
                }
            }
        }

        // animals: pet cats and dogs, look at the herd
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(origin).inflate(ENTITY_RADIUS), Animal::isAlive)) {
            float appeal = animal instanceof TamableAnimal ? 1.5F : 0.7F;
            add(found, new Opportunity(Opportunity.Kind.PET, besides(level, animal.blockPosition(), origin), animal.blockPosition(), animal, appeal));
        }

        // things: chairs to sit on, items on display (data driven, from vanilla and other mods)
        if (OpportunityTypes.hasEntityTypes()) {
            for (Entity entity : level.getEntities(villager, new AABB(origin).inflate(ENTITY_RADIUS), Entity::isAlive)) {
                OpportunityTypes.Type type = OpportunityTypes.forEntity(entity);
                if (type == null) {
                    continue;
                }
                float appeal = type.appealFor(roomAt(village, entity.blockPosition()), villager.getProfession());
                if (type.kind() == Opportunity.Kind.SIT) {
                    if (entity.getPassengers().isEmpty()) {
                        add(found, new Opportunity(Opportunity.Kind.SIT, entity.blockPosition(), null, entity, appeal, null, type.particle(), null));
                    }
                } else if (type.kind() == Opportunity.Kind.INSPECT) {
                    ItemStack shown = displayed(entity);
                    if (OpportunityTypes.forItem(shown) instanceof OpportunityTypes.Type played) {
                        // an instrument on display: take a copy and play it
                        add(found, new Opportunity(played.kind(), besides(level, entity.blockPosition(), origin), entity.blockPosition(), null,
                                Math.max(appeal, played.appealFor(roomAt(village, entity.blockPosition()), villager.getProfession())),
                                new ItemStack(shown.getItem()), played.particle(), null));
                    } else if (!shown.isEmpty()) {
                        add(found, new Opportunity(Opportunity.Kind.INSPECT, besides(level, entity.blockPosition(), origin),
                                entity.blockPosition(), entity, appeal, shown.copyWithCount(1), type.particle(), null));
                    }
                } else {
                    add(found, new Opportunity(type.kind(), besides(level, entity.blockPosition(), origin), entity.blockPosition(),
                            entity, appeal, type.holdStack(), type.particle(), null));
                }
            }
        }

        // integrations that need code
        Opportunities.provide(level, villager, opportunity -> add(found, opportunity));

        // the bell, and home
        villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).filter(p -> p.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .flatMap(bell -> standNear(level, bell).map(spot -> new Opportunity(Opportunity.Kind.GATHER, spot, bell, null, 1.0F)))
                .ifPresent(o -> add(found, o));
        villager.getResidency().getHome().filter(p -> p.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .flatMap(bed -> standNear(level, bed).map(spot -> new Opportunity(Opportunity.Kind.HOME, spot, bed, null, 1.0F)))
                .ifPresent(o -> add(found, o));

        List<Opportunity> all = new ArrayList<>();
        found.values().forEach(all::addAll);
        return all;
    }

    private static @Nullable String roomAt(@Nullable Village village, BlockPos pos) {
        return village == null ? null : village.getBuildingAt(pos).map(Building::getType).orElse(null);
    }

    /** What an entity shows: an item frame's item, or what a stand or display holds. */
    static ItemStack displayed(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof LivingEntity living) {
            ItemStack main = living.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!main.isEmpty()) {
                return main;
            }
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST}) {
                if (!living.getItemBySlot(slot).isEmpty()) {
                    return living.getItemBySlot(slot);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void add(Map<Opportunity.Kind, List<Opportunity>> found, Opportunity opportunity) {
        List<Opportunity> list = found.computeIfAbsent(opportunity.kind(), ignored -> new ArrayList<>());
        if (list.size() < PER_KIND) {
            list.add(opportunity);
        }
    }

    /** A spot next to {@code target} where a villager can stand. */
    static Optional<BlockPos> standNear(ServerLevel level, BlockPos target) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos feet = target.relative(direction).above(dy);
                if (canStand(level, feet)) {
                    return Optional.of(feet);
                }
            }
        }
        return Optional.empty();
    }

    /** A spot about a block and a half from {@code target}, on the side facing {@code from}. */
    static BlockPos besides(ServerLevel level, BlockPos target, BlockPos from) {
        Direction side = Direction.getApproximateNearest(from.getX() - target.getX(), 0, from.getZ() - target.getZ());
        BlockPos spot = target.relative(side.getAxis() == Direction.Axis.Y ? Direction.NORTH : side);
        return canStand(level, spot) ? spot : standNear(level, target).orElse(target);
    }

    static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
               && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
               && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
               && level.getFluidState(feet).isEmpty();
    }

    static @Nullable Opportunity nearestOf(List<Opportunity> opportunities, Opportunity.Kind kind, BlockPos from) {
        return opportunities.stream().filter(o -> o.kind() == kind)
                .min(java.util.Comparator.comparingDouble(o -> o.spot().distSqr(from))).orElse(null);
    }
}
