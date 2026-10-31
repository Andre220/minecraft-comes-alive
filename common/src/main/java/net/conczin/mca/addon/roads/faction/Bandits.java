package net.conczin.mca.addon.roads.faction;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.conczin.mca.network.Network;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.Optional;

/**
 * The people of the factions: pillagers outside raids become MCA villagers of the bandit profession, so they look,
 * think and talk like everyone else (Fresh Animations, traits, mood, the conversation), carrying the band's banner on
 * their tag. They fight with the mercenaries' moves, go for players their band is hostile to, and a player their
 * band is hostile to gets no word out of them. Raids stay vanilla pillagers, as the raid needs them.
 */
public final class Bandits {
    public static final VillagerProfession BANDIT = ProfessionsMCA.register("bandit", false, true, true,
            PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.PILLAGER_AMBIENT);

    /** How far a bandit spots a player its band is hostile to. */
    private static final double SPOT = 16.0;
    /** The most bandits around an outpost; more pillagers spawned there are not turned. */
    private static final int CROWD = 8;
    private static final double CROWD_RANGE = 48.0;
    private static final String CAPTAIN = "mca_bandit_captain";
    /** A bandit with "mca_march:<pos>" walks on that place. */
    private static final String MARCH = "mca_march:";
    /** What bandits carry, with the most of each (items, not stacks: stacks cannot be made before registries bind). */
    private record Loot(Item item, int most) {
    }

    private static final List<Loot> LOOT = List.of(
            new Loot(Items.BREAD, 6), new Loot(Items.WHEAT, 16), new Loot(Items.LEATHER, 6),
            new Loot(Items.IRON_INGOT, 4), new Loot(Items.GOLD_INGOT, 2), new Loot(Items.ARROW, 16),
            new Loot(Items.STRING, 8), new Loot(Items.COOKED_BEEF, 4), new Loot(Items.FEATHER, 8),
            new Loot(Items.COAL, 12), new Loot(Items.BOOK, 2), new Loot(Items.SADDLE, 1));

    private Bandits() {
    }

    public static void bootstrap() {
        // "Who are you?": the bandit names its band and how it sees the player
        Actions.register("bandit_band", GsonHelper::convertToString, ignored -> (villager, player) -> {
            Component line = FactionData.get(player.level()).faction(Factions.factionOf(villager))
                    .map(band -> (Component) Component.translatable("dialogue.bandit.band", Factions.name(band),
                            Factions.standing(player.level(), player.getUUID(), band.id()).getName()))
                    .orElse(Component.translatable("dialogue.bandit.no_band"));
            Network.sendToPlayer(new InteractionDialogueQuestionResponse(line, false), player);
        });
        FactionDeals.bootstrap();
    }

    public static boolean isBandit(Entity entity) {
        return entity instanceof VillagerEntityMCA villager && villager.getProfession() == BANDIT;
    }

    /** Whether a pillager joining the world becomes a bandit: with factions on, outside raids, if configured. */
    public static boolean converts(Entity entity) {
        return Factions.enabled() && Config.getServerConfig().factionPillagersAsPeople
               && entity.getClass().equals(Pillager.class) && ((Pillager) entity).getCurrentRaid() == null
               && entity.level() instanceof ServerLevel;
    }

    /** Turns a pillager into a bandit of the band whose land it stands in; with enough bandits around, it just goes. */
    public static void convert(Pillager pillager) {
        if (pillager.level().getEntitiesOfClass(VillagerEntityMCA.class, pillager.getBoundingBox().inflate(CROWD_RANGE),
                Bandits::isBandit).size() >= CROWD) {
            return; // outposts keep spawning pillagers, and bandits do not count against the monster cap
        }
        if (!(pillager.level() instanceof ServerLevel level)) {
            return;
        }
        int faction = Factions.factionOf(pillager);
        if (FactionDeals.truceCovers(level, faction, pillager.blockPosition())) {
            return; // the band was paid to keep its patrols away from this village
        }
        VillagerEntityMCA bandit = spawn(level, pillager.blockPosition(), faction, pillager.isPatrolLeader()).orElse(null);
        if (bandit != null && pillager.hasCustomName()) {
            bandit.setCustomName(pillager.getCustomName());
        }
    }

    /** A new bandit of a band: armed, carrying some loot, staying around. */
    public static Optional<VillagerEntityMCA> spawn(ServerLevel level, BlockPos pos, int faction, boolean captain) {
        VillagerEntityMCA bandit = VillagerFactory.newVillager(level)
                .withGender(Gender.getRandom())
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withProfession(BANDIT)
                .spawn(EntitySpawnReason.NATURAL);
        if (bandit == null) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();
        bandit.setItemSlot(EquipmentSlot.MAINHAND, random.nextFloat() < 0.7F ? new ItemStack(Items.CROSSBOW) : new ItemStack(Items.IRON_AXE));
        bandit.addTag(Factions.TAG_PREFIX + faction);
        bandit.setPersistenceRequired();
        if (captain) {
            bandit.addTag(CAPTAIN);
        }
        // what was taken on the road, for whoever comes to deal
        for (int i = 0, count = 2 + random.nextInt(3); i < count; i++) {
            Loot loot = LOOT.get(random.nextInt(LOOT.size()));
            bandit.getInventory().addItem(new ItemStack(loot.item(), 1 + random.nextInt(loot.most())));
        }
        bandit.getInventory().addItem(new ItemStack(Items.EMERALD, 2 + random.nextInt(6)));
        return Optional.of(bandit);
    }

    /** Sends a bandit marching on a place; it walks there between fights. */
    static void march(VillagerEntityMCA bandit, BlockPos target) {
        clearTags(bandit, MARCH);
        bandit.addTag(MARCH + target.asLong());
    }

    private static void clearTags(Entity entity, String prefix) {
        entity.entityTags().stream().filter(t -> t.startsWith(prefix)).toList().forEach(entity::removeTag);
    }

    /** Once a second: a bandit picks a player its band is hostile to, and lets go of one it is not. */
    public static void tick(ServerLevel level, VillagerEntityMCA bandit) {
        int faction = Factions.factionOf(bandit);
        if (faction < 0) {
            return;
        }
        Optional<Entity> current = bandit.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).map(Entity.class::cast);
        if (current.isPresent() && current.get() instanceof ServerPlayer player
            && Factions.standing(level, player.getUUID(), faction) != Factions.Standing.HOSTILE && bandit.getLastHurtByMob() != player) {
            bandit.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        if (current.isPresent()) {
            return;
        }
        Optional<LivingEntity> target = level.players().stream()
                .filter(p -> !p.isCreative() && !p.isSpectator() && p.distanceTo(bandit) < SPOT && bandit.hasLineOfSight(p))
                .filter(p -> Factions.standing(level, p.getUUID(), faction) == Factions.Standing.HOSTILE)
                .min(Comparator.comparingDouble(p -> p.distanceTo(bandit)))
                .map(LivingEntity.class::cast)
                .or(() -> FactionWars.enemyNear(level, bandit, faction, SPOT)); // a band at war with its own
        if (target.isPresent()) {
            bandit.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target.get());
            return;
        }
        // on the march: walk on, until there
        bandit.entityTags().stream().filter(t -> t.startsWith(MARCH)).findFirst().ifPresent(tag -> {
            BlockPos goal = BlockPos.of(Long.parseLong(tag.substring(MARCH.length())));
            if (bandit.blockPosition().distSqr(goal) < 12 * 12) {
                bandit.removeTag(tag);
            } else {
                bandit.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, 0.7F, 8));
            }
        });
    }

    /** Whether a bandit's band counts a player a friend or more, enough to hire its people. */
    public static boolean trusts(Entity bandit, ServerPlayer player) {
        int faction = Factions.factionOf(bandit);
        return faction >= 0 && Factions.standing(player.level(), player.getUUID(), faction).ordinal() >= Factions.Standing.FRIENDLY.ordinal();
    }

    /** A bandit has no word for a player its band is hostile to. */
    public static boolean refusesTalk(VillagerEntityMCA bandit, Player player) {
        if (!(bandit.level() instanceof ServerLevel level) || !isBandit(bandit)) {
            return false;
        }
        int faction = Factions.factionOf(bandit);
        if (faction >= 0 && Factions.standing(level, player.getUUID(), faction) == Factions.Standing.HOSTILE) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("faction.no_talk"), true);
            }
            return true;
        }
        return false;
    }

    /** The band's name, for the interaction screen; empty for no band. */
    public static Optional<Component> bandName(VillagerEntityMCA bandit) {
        if (!(bandit.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return FactionData.get(level).faction(Factions.factionOf(bandit)).map(Factions::name);
    }
}
