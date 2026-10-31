package net.conczin.mca.addon.roads.caravan;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.mixin.addon.roads.AbstractHorseAccessor;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.registry.TradeOffersMCA;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caravans: three to five villagers (a merchant leading, guards, porters) with pack animals carrying chests, roaming
 * from village to village among the ones players know. With players around they really walk, in a line, stopping for
 * the night; out of sight they travel on, and show up at their destination when a player is there. At a village
 * they stay a while and trade, then move on; with nowhere left to go they walk off and vanish. With caravans on, the
 * wandering trader still comes, only rarer.
 */
public final class Caravans {
    public static final VillagerProfession CARAVANEER = ProfessionsMCA.register("caravaneer", true, true, true,
            PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
            Int2ObjectMap.ofEntries(Int2ObjectMap.entry(1, TradeOffersMCA.CARAVANEER_LEVEL_1)));
    public static final VillagerProfession CARAVAN_GUARD = ProfessionsMCA.register("caravan_guard", false, true, true,
            PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_WEAPONSMITH);

    /** Members and animals carry "mca_caravan:<id>:<generation>"; an older generation is left over and goes away. */
    static final String TAG_PREFIX = "mca_caravan:";
    private static final long CHECK = 1200;
    /** A caravan not seen for this long is out of sight: it travels on unseen. */
    private static final long UNSEEN = 2400;
    /** Blocks walked per tick on the road, for travel out of sight. */
    private static final double ROAD_SPEED = 0.08;
    private static final double ARRIVED = 14.0;
    private static final double STAY_RADIUS = 28.0;
    private static final double STEP = 24.0;
    private static final float SPEED = 0.6F;

    private Caravans() {
    }

    public static void bootstrap() {
        CaravanTalk.bootstrap();
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerCaravans;
    }

    /** With caravans on, the wandering trader comes only one time in {@code wanderingTraderRarity}. */
    public static boolean skipWanderingTrader(RandomSource random) {
        int rarity = Config.getServerConfig().wanderingTraderRarity;
        return enabled() && rarity > 1 && random.nextInt(rarity) != 0;
    }

    // ---------------------------------------------------------------- villages

    /** From the village tick: remembers villages players visit, sends caravans on, and forms new ones. */
    public static void villageTick(ServerLevel level, Village village, long time) {
        if (!enabled()) {
            return;
        }
        CaravanData data = CaravanData.get(level);
        boolean watched = time % 200 == 0 && level.players().stream().anyMatch(village::isWithinBorder);
        if (watched) {
            data.markKnown(village.getId());
        }
        if (time % CHECK != 0) {
            return;
        }
        boolean players = level.players().stream().anyMatch(village::isWithinBorder);
        long now = level.getGameTime();
        for (CaravanData.Caravan caravan : data.all()) {
            if (caravan.at == village.getId() && now - caravan.lastSeen > UNSEEN && caravan.state != CaravanData.State.ON_ROAD) {
                goUnseen(level, data, caravan, now);
            }
            if (caravan.state == CaravanData.State.ON_ROAD && caravan.to == village.getId() && now >= caravan.until && players) {
                appear(level, data, caravan, village);
            }
        }
        if (players && data.all().size() < Config.getServerConfig().caravanMaxActive
            && level.getRandom().nextFloat() < Config.getServerConfig().caravanChancePerMinute
            && data.all().stream().noneMatch(c -> c.at == village.getId() || c.to == village.getId())) {
            form(level, data, village);
        }
    }

    /** Out of sight: a caravan that was staying moves on, one on its way keeps going; either way unseen. */
    private static void goUnseen(ServerLevel level, CaravanData data, CaravanData.Caravan caravan, long now) {
        if (caravan.state == CaravanData.State.ARRIVING || caravan.state == CaravanData.State.STAYING) {
            if (now < caravan.until) {
                return;
            }
            if (!chooseDestination(level, data, caravan)) {
                data.remove(caravan);
                return;
            }
        }
        if (caravan.state == CaravanData.State.LEAVING || caravan.to < 0) {
            data.remove(caravan);
            return;
        }
        Optional<Village> from = village(level, caravan.at);
        Optional<Village> to = village(level, caravan.to);
        if (to.isEmpty()) {
            data.remove(caravan);
            return;
        }
        double distance = from.map(v -> Math.sqrt(center(v).distSqr(center(to.get())))).orElse(500.0);
        from.ifPresent(v -> CaravanRoad.unseenDangers(level, caravan, center(v), center(to.get())));
        caravan.state = CaravanData.State.ON_ROAD;
        caravan.until = now + (long) (distance / ROAD_SPEED);
        caravan.generation++; // the people left behind in unloaded chunks go away when seen
        data.setDirty();
    }

    /** A caravan reaching a village out of sight shows up at its edge, with the same people as when last seen. */
    private static void appear(ServerLevel level, CaravanData data, CaravanData.Caravan caravan, Village village) {
        BlockPos pos = edge(level, village);
        if (pos == null) {
            return;
        }
        caravan.at = village.getId();
        caravan.to = -1;
        caravan.state = CaravanData.State.ARRIVING;
        caravan.until = level.getGameTime() + Config.getServerConfig().caravanStayTime;
        caravan.members.clear();
        caravan.animals.clear();
        List<CompoundTag> people = new ArrayList<>(caravan.snapshot);
        List<VillagerEntityMCA> spawned = new ArrayList<>();
        for (CompoundTag person : people) {
            VillagerEntityMCA villager = fromSnapshot(level, person, pos);
            if (villager != null) {
                spawned.add(villager);
            }
        }
        if (spawned.isEmpty()) {
            data.remove(caravan);
            return;
        }
        enlist(caravan, spawned);
        packAnimals(level, caravan, spawned, pos, caravan.animalTypes.isEmpty() ? null : caravan.animalTypes);
        if (!caravan.lostOnRoad.isEmpty()) {
            announce(level, village, Component.translatable("caravan.arrived_hurt", caravan.name(), caravan.lostOnRoad));
            caravan.lostOnRoad = "";
        }
        data.setDirty();
    }

    /** A new caravan, at the edge of a known village it then walks into. */
    private static void form(ServerLevel level, CaravanData data, Village village) {
        BlockPos pos = edge(level, village);
        if (pos == null) {
            return;
        }
        RandomSource random = level.getRandom();
        List<VillagerEntityMCA> people = new ArrayList<>();
        VillagerEntityMCA leader = person(level, pos, CARAVANEER);
        if (leader == null) {
            return;
        }
        people.add(leader);
        int guards = 1 + random.nextInt(2);
        for (int i = 0; i < guards; i++) {
            VillagerEntityMCA guard = person(level, pos, CARAVAN_GUARD);
            if (guard != null) {
                guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.CROSSBOW));
                guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                people.add(guard);
            }
        }
        int porters = random.nextInt(Math.min(3, 6 - people.size()));
        for (int i = 0; i < porters; i++) {
            VillagerEntityMCA porter = person(level, pos, CARAVANEER);
            if (porter != null) {
                people.add(porter);
            }
        }
        CaravanData.Caravan caravan = data.create(leader.getName().getString());
        caravan.at = village.getId();
        caravan.state = CaravanData.State.ARRIVING;
        caravan.until = level.getGameTime() + Config.getServerConfig().caravanStayTime;
        caravan.lastSeen = level.getGameTime();
        enlist(caravan, people);
        packAnimals(level, caravan, people, pos, null);
        data.setDirty();
    }

    private static @Nullable VillagerEntityMCA person(ServerLevel level, BlockPos pos, VillagerProfession profession) {
        Gender gender = Gender.getRandom();
        VillagerEntityMCA villager = gender.getVillagerType().spawn(level, pos, EntitySpawnReason.EVENT);
        if (villager == null) {
            return null;
        }
        villager.setCustomName(Component.literal(Names.pickCitizenName(gender)));
        villager.setProfession(profession);
        villager.setPersistenceRequired();
        return villager;
    }

    private static @Nullable VillagerEntityMCA fromSnapshot(ServerLevel level, CompoundTag person, BlockPos pos) {
        Optional<EntityType<?>> type = entityType(person.getStringOr("mca_type", ""));
        if (type.isEmpty() || !(type.get().create(level, EntitySpawnReason.EVENT) instanceof VillagerEntityMCA villager)) {
            return null;
        }
        villager.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), person));
        villager.setUUID(UUID.randomUUID());
        villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        clearTags(villager);
        villager.setPersistenceRequired();
        return level.addFreshEntity(villager) ? villager : null;
    }

    /** Tags the people as this generation of the caravan; the first is its leader. */
    private static void enlist(CaravanData.Caravan caravan, List<VillagerEntityMCA> people) {
        caravan.leader = people.getFirst().getUUID();
        for (VillagerEntityMCA villager : people) {
            clearTags(villager);
            villager.addTag(caravan.tag());
            caravan.members.add(villager.getUUID());
        }
    }

    private static void clearTags(Entity entity) {
        for (String tag : List.copyOf(entity.entityTags())) {
            if (tag.startsWith(TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }
    }

    /** Two to four pack animals with chests full of goods, each led by one of the people. */
    private static void packAnimals(ServerLevel level, CaravanData.Caravan caravan, List<VillagerEntityMCA> people, BlockPos pos,
                                    @Nullable List<String> types) {
        RandomSource random = level.getRandom();
        Holder<Biome> biome = level.getBiome(pos);
        List<String> kinds = types != null ? new ArrayList<>(types) : new ArrayList<>();
        if (kinds.isEmpty()) {
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                kinds.add(Goods.animalFor(biome, random));
            }
        }
        caravan.animalTypes.clear();
        caravan.animalTypes.addAll(kinds);
        for (int i = 0; i < kinds.size(); i++) {
            Optional<EntityType<?>> type = entityType(kinds.get(i));
            if (type.isEmpty() || !(type.get().create(level, EntitySpawnReason.EVENT) instanceof AbstractChestedHorse animal)) {
                continue;
            }
            animal.snapTo(pos.getX() + 0.5 + random.nextGaussian(), pos.getY(), pos.getZ() + 0.5 + random.nextGaussian(), 0.0F, 0.0F);
            animal.setTamed(true);
            animal.setChest(true);
            animal.setPersistenceRequired();
            animal.addTag(caravan.tag());
            if (!level.addFreshEntity(animal)) {
                continue;
            }
            SimpleContainer chest = ((AbstractHorseAccessor) animal).mca$getInventory();
            List<ItemStack> goods = Goods.load(biome, random);
            for (int slot = 1; slot < chest.getContainerSize() && !goods.isEmpty(); slot++) {
                chest.setItem(slot, goods.removeFirst());
            }
            animal.setLeashedTo(people.get(i % people.size()), true);
            caravan.animals.add(animal.getUUID());
        }
    }

    // ---------------------------------------------------------------- people

    /** From a caravan member's server tick, once a second: leftovers go away, the leader leads, the others follow. */
    public static void tickMember(ServerLevel level, VillagerEntityMCA villager) {
        String tag = villager.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).findFirst().orElse(null);
        if (tag == null) {
            return;
        }
        CaravanData data = CaravanData.get(level);
        CaravanData.Caravan caravan = caravanOf(data, tag);
        if (caravan == null) {
            villager.discard();
            return;
        }
        long now = level.getGameTime();
        caravan.lastSeen = now;
        if (villager.getUUID().equals(caravan.leader)) {
            lead(level, data, caravan, villager, now);
        } else {
            Entity leader = caravan.leader == null ? null : level.getEntity(caravan.leader);
            follow(level, caravan, villager, leader);
        }
    }

    /** From a pack animal's tick: one of an older generation, or of a caravan gone, goes away. */
    public static void tickAnimal(Entity animal) {
        if (!enabled() || !(animal.level() instanceof ServerLevel level)) {
            return;
        }
        animal.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).findFirst().ifPresent(tag -> {
            if (caravanOf(CaravanData.get(level), tag) == null) {
                animal.discard();
            }
        });
    }

    /** The caravan a tag names, if it is still of the current generation. */
    private static CaravanData.@Nullable Caravan caravanOf(CaravanData data, String tag) {
        String[] parts = tag.substring(TAG_PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            CaravanData.Caravan caravan = data.get(Integer.parseInt(parts[0]));
            return caravan != null && caravan.generation == Integer.parseInt(parts[1]) ? caravan : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void lead(ServerLevel level, CaravanData data, CaravanData.Caravan caravan, VillagerEntityMCA leader, long now) {
        if (now % 1200 < 20) {
            snapshot(level, caravan);
            data.setDirty();
        }
        boolean night = Math.floorMod(level.getOverworldClockTime(), 24000L) > 12500 && Math.floorMod(level.getOverworldClockTime(), 24000L) < 23500;
        switch (caravan.state) {
            case ARRIVING -> village(level, caravan.at).ifPresentOrElse(village -> {
                Vec3 center = Vec3.atBottomCenterOf(center(village));
                if (leader.position().distanceTo(center) < ARRIVED) {
                    caravan.state = CaravanData.State.STAYING;
                    caravan.until = now + Config.getServerConfig().caravanStayTime;
                    announce(level, village, Component.translatable("caravan.arrived", caravan.name(), village.getName()));
                    CaravanRoad.rewardEscort(level, caravan, leader, village);
                    CaravanTrade.tradeWithVillage(level, caravan, village, leader.blockPosition());
                    data.setDirty();
                } else if (!night) {
                    walk(level, leader, center);
                }
            }, () -> data.remove(caravan));
            case STAYING -> village(level, caravan.at).ifPresentOrElse(village -> {
                Vec3 center = Vec3.atBottomCenterOf(center(village));
                if (now >= caravan.until && !night) {
                    if (chooseDestination(level, data, caravan)) {
                        village(level, caravan.to).ifPresent(next ->
                                announce(level, village, Component.translatable("caravan.leaving", caravan.name(), next.getName())));
                    } else {
                        caravan.state = CaravanData.State.LEAVING;
                        caravan.until = now;
                        announce(level, village, Component.translatable("caravan.leaving_far", caravan.name()));
                    }
                    data.setDirty();
                } else if (leader.position().distanceTo(center) > STAY_RADIUS) {
                    walk(level, leader, center);
                }
            }, () -> data.remove(caravan));
            case TRAVELING -> village(level, caravan.to).ifPresentOrElse(village -> {
                Vec3 center = Vec3.atBottomCenterOf(center(village));
                if (leader.position().distanceTo(center) < ARRIVED * 2) {
                    caravan.at = village.getId();
                    caravan.to = -1;
                    caravan.state = CaravanData.State.ARRIVING;
                    data.setDirty();
                } else if (!night) {
                    walk(level, leader, center);
                }
                CaravanRoad.travelTick(level, data, caravan, leader);
            }, () -> data.remove(caravan));
            case LEAVING -> {
                Vec3 away = village(level, caravan.at).map(v -> Vec3.atBottomCenterOf(center(v))).orElse(leader.position());
                Vec3 out = leader.position().subtract(away);
                if (out.lengthSqr() < 1.0E-4) {
                    out = new Vec3(1, 0, 0);
                }
                if (leader.position().distanceTo(away) > 96 && level.getNearestPlayer(leader, 48) == null) {
                    disband(level, caravan);
                    data.remove(caravan);
                } else {
                    walk(level, leader, leader.position().add(out.normalize().scale(STEP)));
                }
            }
            case ON_ROAD -> {
            }
        }
    }

    /** The others keep up with the leader; guards fighting are left to it. */
    private static void follow(ServerLevel level, CaravanData.Caravan caravan, VillagerEntityMCA villager, @Nullable Entity leader) {
        if (leader == null || villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        if (caravan.state == CaravanData.State.STAYING && villager.distanceTo(leader) < STAY_RADIUS) {
            return;
        }
        if (villager.distanceTo(leader) > 4.0) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(leader, SPEED + 0.05F, 3));
        }
    }

    /** Walks towards a point, a stretch at a time along the ground. */
    private static void walk(ServerLevel level, VillagerEntityMCA villager, Vec3 target) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        Vec3 here = villager.position();
        Vec3 offset = target.subtract(here);
        Vec3 point = offset.horizontalDistance() > STEP ? here.add(offset.normalize().scale(STEP)) : target;
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(ground, SPEED, 2));
    }

    /** Picks the next known village to go to, nearer ones more likely. False with none in reach. */
    private static boolean chooseDestination(ServerLevel level, CaravanData data, CaravanData.Caravan caravan) {
        Optional<Village> here = village(level, caravan.at);
        if (here.isEmpty()) {
            return false;
        }
        int reach = Config.getServerConfig().caravanMaxDistance;
        List<Village> options = data.known().stream()
                .filter(id -> id != caravan.at)
                .map(id -> village(level, id))
                .flatMap(Optional::stream)
                .filter(v -> center(v).distSqr(center(here.get())) < (double) reach * reach)
                .sorted(Comparator.comparingDouble(v -> center(v).distSqr(center(here.get()))))
                .toList();
        if (options.isEmpty()) {
            return false;
        }
        // nearer villages are likelier: pick among the closest three
        Village next = options.get(level.getRandom().nextInt(Math.min(3, options.size())));
        caravan.from = caravan.at;
        caravan.to = next.getId();
        caravan.state = CaravanData.State.TRAVELING;
        return true;
    }

    /** What the caravan's people are now, to bring them back after travelling unseen. */
    private static void snapshot(ServerLevel level, CaravanData.Caravan caravan) {
        List<CompoundTag> people = new ArrayList<>();
        for (UUID id : caravan.members) {
            if (level.getEntity(id) instanceof VillagerEntityMCA villager && villager.isAlive()) {
                TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                villager.saveWithoutId(output);
                CompoundTag nbt = output.buildResult();
                nbt.remove("Brain");
                nbt.remove("Leash");
                nbt.putString("mca_type", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(villager.getType())));
                people.add(nbt);
            }
        }
        if (!people.isEmpty()) {
            caravan.snapshot.clear();
            caravan.snapshot.addAll(people);
        }
    }

    /** Takes the caravan's people and animals out of the world, as if they had walked off. */
    private static void disband(ServerLevel level, CaravanData.Caravan caravan) {
        for (UUID id : caravan.members) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID id : caravan.animals) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Optional<Village> village(ServerLevel level, int id) {
        return id < 0 ? Optional.empty() : VillageManager.get(level).getOrEmpty(id);
    }

    private static BlockPos center(Village village) {
        return new BlockPos(village.getCenter());
    }

    /** A spot on solid, dry ground at the edge of the village, in a loaded chunk; null when none is found. */
    private static @Nullable BlockPos edge(ServerLevel level, Village village) {
        RandomSource random = level.getRandom();
        BlockPos center = center(village);
        double radius = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan()) / 2.0 + 16;
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int x = center.getX() + (int) (Math.cos(angle) * radius);
            int z = center.getZ() + (int) (Math.sin(angle) * radius);
            if (!level.isLoaded(new BlockPos(x, center.getY(), z))) {
                continue;
            }
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(ground.below()).isEmpty() && level.getBlockState(ground).isAir()
                && level.getBlockState(ground.below()).isSolid()) {
                return ground;
            }
        }
        return null;
    }

    static Optional<Village> villageById(ServerLevel level, int id) {
        return village(level, id);
    }

    /** The caravan a villager walks with, if any, of the current generation. */
    static Optional<CaravanData.Caravan> caravanOf(ServerLevel level, VillagerEntityMCA villager) {
        return villager.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).findFirst()
                .map(tag -> caravanOf(CaravanData.get(level), tag));
    }

    /** Whether a villager walks with a caravan; server side. */
    public static boolean isMember(VillagerEntityMCA villager) {
        return villager.level() instanceof ServerLevel level && caravanOf(level, villager).isPresent();
    }

    /** A death among a caravan's people, by a player's hand: the villages of its route hear of it. */
    public static void onDeath(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (!enabled() || !(victim instanceof VillagerEntityMCA villager) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        caravanOf(level, villager).ifPresent(caravan -> CaravanRoad.onDeath(level, victim, source, caravan));
    }

    static void announceNear(ServerLevel level, Entity near, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(near) < 64) {
                player.sendSystemMessage(message);
            }
        }
    }

    static void announce(ServerLevel level, Village village, Component message) {
        for (ServerPlayer player : level.players()) {
            if (village.isWithinBorder(player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** The caravan a villager belongs to, by its tag; empty when it travels alone. */
    public static Optional<String> caravanName(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return villager.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).findFirst()
                .map(tag -> caravanOf(CaravanData.get(level), tag))
                .map(CaravanData.Caravan::name);
    }

    /** The entity type an id names, if any. */
    private static Optional<EntityType<?>> entityType(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key);
    }
}
