package net.conczin.mca.entity.interaction;

import net.conczin.mca.expansion.barter.Barter;
import net.conczin.mca.expansion.requests.Requests;
import net.conczin.mca.expansion.economy.EconomyFeature;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.workorder.WorkOrderPermissions;
import net.minecraft.server.level.ServerLevel;
import net.conczin.mca.expansion.minds.VillagerAttributes;
import net.conczin.mca.expansion.talk.TalkPanels;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public final class Constraint implements BiPredicate<VillagerLike<?>, ServerPlayer> {
    // every constraint in registration order (the network sends their index), and the parseable ones by id
    private static final List<Constraint> VALUES = new ArrayList<>();
    public static final Map<String, Constraint> REGISTRY = new HashMap<>();

    public static final Constraint FAMILY = register("family", Relationship.IS_FAMILY.asConstraint());
    public static final Constraint NOT_FAMILY = register("!family", Relationship.IS_FAMILY.negate().asConstraint());

    public static final Constraint RELATIVE = register("relative", Relationship.IS_RELATIVE.asConstraint());
    public static final Constraint NOT_RELATIVE = register("!relative", Relationship.IS_RELATIVE.negate().asConstraint());

    public static final Constraint BABY = register("baby", (villager, player) -> villager.getAgeState() == AgeState.BABY);
    public static final Constraint NOT_BABY = register("!baby", (villager, player) -> villager.getAgeState() != AgeState.BABY);

    public static final Constraint TODDLER = register("toddler", (villager, player) -> villager.getAgeState() == AgeState.TODDLER);
    public static final Constraint NOT_TODDLER = register("!toddler", (villager, player) -> villager.getAgeState() != AgeState.TODDLER);

    public static final Constraint TEEN = register("teen", (villager, player) -> villager.getAgeState() == AgeState.TEEN);
    public static final Constraint NOT_TEEN = register("!teen", (villager, player) -> villager.getAgeState() != AgeState.TEEN);

    public static final Constraint ADULT = register("adult", (villager, player) -> villager.getAgeState().isAdult());
    public static final Constraint NOT_ADULT = register("!adult", (villager, player) -> !villager.getAgeState().isAdult());

    public static final Constraint ELDER = register("elder", (villager, player) -> villager.getAgeState() == AgeState.ELDER);
    public static final Constraint NOT_ELDER = register("!elder", (villager, player) -> villager.getAgeState() != AgeState.ELDER);

    public static final Constraint SPOUSE = register("spouse", Relationship.IS_MARRIED.asConstraint());
    public static final Constraint NOT_SPOUSE = register("!spouse", Relationship.IS_MARRIED.negate().asConstraint());

    public static final Constraint ENGAGED = register("engaged", Relationship.IS_ENGAGED.asConstraint());
    public static final Constraint NOT_ENGAGED = register("!engaged", Relationship.IS_ENGAGED.negate().asConstraint());

    public static final Constraint PROMISED = register("promised", Relationship.IS_PROMISED.asConstraint());
    public static final Constraint NOT_PROMISED = register("!promised", Relationship.IS_PROMISED.negate().asConstraint());

    public static final Constraint KIDS = register("kids", Relationship.IS_PARENT.asConstraint());
    public static final Constraint NOT_KIDS = register("!kids", Relationship.IS_PARENT.negate().asConstraint());

    public static final Constraint PARENT = register("parent", Relationship.IS_KID.asConstraint());
    public static final Constraint NOT_PARENT = register("!parent", Relationship.IS_KID.negate().asConstraint());

    public static final Constraint CLERIC = register("cleric", (villager, player) -> villager.getVillagerData().profession().is(VillagerProfession.CLERIC));
    public static final Constraint NOT_CLERIC = register("!cleric", (villager, player) -> !villager.getVillagerData().profession().is(VillagerProfession.CLERIC));

    public static final Constraint ADVENTURER = register("adventurer", (villager, player) -> villager.getVillagerData().profession().value() == ProfessionsMCA.ADVENTURER);
    public static final Constraint NOT_ADVENTURER = register("!adventurer", (villager, player) -> villager.getVillagerData().profession().value() != ProfessionsMCA.ADVENTURER);

    public static final Constraint MERCENARY = register("mercenary", (villager, player) -> villager.getVillagerData().profession().value() == ProfessionsMCA.MERCENARY);
    public static final Constraint NOT_MERCENARY = register("!mercenary", (villager, player) -> villager.getVillagerData().profession().value() != ProfessionsMCA.MERCENARY);

    public static final Constraint OUTLAWED = register("outlawed", (villager, player) -> villager.getVillagerData().profession().value() == ProfessionsMCA.OUTLAW);
    public static final Constraint NOT_OUTLAWED = register("!outlawed", (villager, player) -> villager.getVillagerData().profession().value() != ProfessionsMCA.OUTLAW);

    public static final Constraint TRADER = register("trader", (villager, player) -> villager.canTradeWithProfession());
    public static final Constraint NOT_TRADER = register("!trader", (villager, player) -> !villager.canTradeWithProfession());

    public static final Constraint PEASANT = register("peasant", (villager, player) -> isRankAtLeast(villager, player, Rank.PEASANT));
    public static final Constraint NOT_PEASANT = register("!peasant", (villager, player) -> !isRankAtLeast(villager, player, Rank.PEASANT));

    public static final Constraint NOBLE = register("noble", (villager, player) -> isRankAtLeast(villager, player, Rank.NOBLE));
    public static final Constraint NOT_NOBLE = register("!noble", (villager, player) -> !isRankAtLeast(villager, player, Rank.NOBLE));

    public static final Constraint MAYOR = register("mayor", (villager, player) -> isRankAtLeast(villager, player, Rank.MAYOR));
    public static final Constraint NOT_MAYOR = register("!mayor", (villager, player) -> !isRankAtLeast(villager, player, Rank.MAYOR));

    public static final Constraint MONARCH = register("monarch", (villager, player) -> isRankAtLeast(villager, player, Rank.MONARCH));
    public static final Constraint NOT_MONARCH = register("!monarch", (villager, player) -> !isRankAtLeast(villager, player, Rank.MONARCH));

    public static final Constraint ORPHAN = register("orphan", Relationship.IS_ORPHAN.asConstraint());
    public static final Constraint NOT_ORPHAN = register("!orphan", Relationship.IS_ORPHAN.negate().asConstraint());

    public static final Constraint FOLLOWING = register("following", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.FOLLOW);
    public static final Constraint NOT_FOLLOWING = register("!following", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.FOLLOW);

    public static final Constraint STAYING = register("staying", (villager, player) -> villager.getVillagerBrain().getMoveState() == MoveState.STAY);
    public static final Constraint NOT_STAYING = register("!staying", (villager, player) -> villager.getVillagerBrain().getMoveState() != MoveState.STAY);

    public static final Constraint VILLAGE_HAS_SPACE = register("village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel) player.level())).filter(Village::hasSpace).isPresent());
    public static final Constraint NOT_VILLAGE_HAS_SPACE = register("!village_has_space", (villager, player) -> PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel) player.level())).filter(Village::hasSpace).isEmpty());

    public static final Constraint WORK_ORDERS = register("work_orders", (villager, player) -> WorkOrderPermissions.enabled());
    public static final Constraint INFO_TALK = register("info_talk", (villager, player) -> TalkPanels.enabled()); // expansion: talk
    public static final Constraint ATTRIBUTES = register("attributes", (villager, player) -> VillagerAttributes.enabled()); // expansion: minds
    public static final Constraint ECONOMY = register("economy", (villager, player) -> EconomyFeature.enabled()); // expansion: economy
    public static final Constraint REQUESTS = register("requests", (villager, player) -> Requests.enabled()); // expansion: requests
    public static final Constraint BARTER = register("barter", (villager, player) -> Barter.enabled()); // expansion: barter
    public static final Constraint HAS_VILLAGE = register("has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isPresent());
    public static final Constraint NOT_HAS_VILLAGE = register("!has_village", (villager, player) -> villager instanceof VillagerEntityMCA mcaVillager && mcaVillager.getResidency().getHomeVillage().isEmpty());

    public static final Constraint HIT_BY = register("hit_by", (villager, player) -> {
        if (villager instanceof VillagerEntityMCA v) {
            return v.isHitBy(player);
        } else {
            return false;
        }
    });
    public static final Constraint NOT_HIT_BY = register("!hit_by", (villager, player) -> !HIT_BY.test(villager, player));

    public static final Constraint RIDING = register("riding", (villager, player) -> villager.asEntity().isPassenger());
    public static final Constraint NOT_RIDING = register("!riding", (villager, player) -> !villager.asEntity().isPassenger());

    public static final Constraint INVALID = new Constraint("<invalid>", (villager, player) -> false);

    private final String id;
    private final BiPredicate<VillagerLike<?>, ServerPlayer> check;
    private final int ordinal;

    private Constraint(String id, BiPredicate<VillagerLike<?>, ServerPlayer> check) {
        this.id = id;
        this.check = check;
        this.ordinal = VALUES.size();
        VALUES.add(this);
    }

    /**
     * A constraint for dialogues and buttons, by id. MCA's own are the constants above; addons register theirs while
     * they bootstrap, on client and server alike, so both number them the same.
     */
    public static Constraint register(String id, BiPredicate<VillagerLike<?>, ServerPlayer> check) {
        Constraint constraint = new Constraint(id, check);
        if (REGISTRY.putIfAbsent(id, constraint) != null) {
            throw new IllegalArgumentException("Duplicate interaction constraint '" + id + "'");
        }
        return constraint;
    }

    public static Constraint[] values() {
        return VALUES.toArray(new Constraint[0]);
    }

    public int ordinal() {
        return ordinal;
    }

    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    private static boolean isRankAtLeast(VillagerLike<?> villager, ServerPlayer player, Rank rank) {
        return player != null && villager instanceof VillagerEntityMCA && ((VillagerEntityMCA) villager).getResidency().getHomeVillage()
                .filter(village -> Tasks.getRank(village, player).isAtLeast(rank)).isPresent();
    }

    public static Set<Constraint> all() {
        return new HashSet<>(REGISTRY.values());
    }

    public static Set<Constraint> allMatching(VillagerLike<?> villager, ServerPlayer player) {
        return VALUES.stream().filter(c -> c.test(villager, player)).collect(Collectors.toSet());
    }

    public static List<Constraint> fromStringList(String constraints) {
        if (MCA.isBlankString(constraints)) {
            return new ArrayList<>();
        }

        List<Constraint> parsed = new ArrayList<>();
        for (String id : constraints.split(",")) {
            String normalizedId = id.trim();
            Constraint constraint = REGISTRY.get(normalizedId);
            if (constraint == null) {
                MCA.LOGGER.warn("Unknown interaction constraint '{}'", normalizedId);
                parsed.add(INVALID);
            } else {
                parsed.add(constraint);
            }
        }
        return parsed;
    }

    @Override
    public boolean test(VillagerLike<?> t, ServerPlayer u) {
        return check.test(t, u);
    }
}
