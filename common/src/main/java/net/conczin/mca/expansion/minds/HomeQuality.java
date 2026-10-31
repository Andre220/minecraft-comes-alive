package net.conczin.mca.expansion.minds;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.traits.PersonalityTraits;
import net.conczin.mca.server.property.PropertyRules;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageBuilding;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How nice a villager's home is, from 0 to 100: room per resident, light, decoration and a chest
 * of their own. A cozy home cheers them up, a cramped one gets them down.
 */
public final class HomeQuality {
    /** Blocks that make a home nicer; a data pack can change the list. */
    public static final TagKey<Block> DECORATION = TagKey.create(Registries.BLOCK, MCA.locate("decoration"));

    public static final int COZY = 70;
    public static final int CRAMPED = 35;
    private static final int REFRESH_TICKS = 2400;
    private static final int MAX_VOLUME = 16 * 16 * 8;
    /** Floor area per resident that counts as roomy. */
    private static final int ROOMY_AREA = 25;
    private static final int DECORATION_POINTS = 3;

    private record Score(int value, long time) {
    }

    private static final Map<UUID, Score> SCORES = new ConcurrentHashMap<>();

    private HomeQuality() {
    }

    /** The home's score, recomputed every couple of minutes; -1 for none or unknown. */
    public static int score(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return -1;
        }
        Score cached = SCORES.get(villager.getUUID());
        long time = level.getGameTime();
        if (cached != null && time - cached.time() < REFRESH_TICKS) {
            return cached.value();
        }
        int value = compute(level, villager);
        SCORES.put(villager.getUUID(), new Score(value, time));
        return value;
    }

    private static int compute(ServerLevel level, VillagerEntityMCA villager) {
        Optional<GlobalPos> bed = villager.getResidency().getHome().filter(home -> home.dimension() == level.dimension());
        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (bed.isEmpty() || village.isEmpty()) {
            return -1;
        }
        Optional<VillageBuilding> home = PropertyRules.homeOf(village.get(), bed.get().pos());
        if (home.isEmpty()) {
            return -1;
        }
        return compute(level, home.get(), residents(village.get(), home.get()), bed.get().pos());
    }

    /** The score of a home building with this many residents, its bed at {@code bed}. */
    public static int compute(ServerLevel level, VillageBuilding home, int residents, BlockPos bed) {
        BlockPos min = home.getPos0();
        BlockPos max = home.getPos1();
        int dx = Math.abs(max.getX() - min.getX()) + 1;
        int dy = Math.abs(max.getY() - min.getY()) + 1;
        int dz = Math.abs(max.getZ() - min.getZ()) + 1;
        if ((long) dx * dy * dz > MAX_VOLUME) {
            return -1;
        }

        // room: up to 35 points, by the walkable floor where MCA knows it
        long floor = home instanceof Building building && building.getFloorFootprintArea() > 0
                ? building.getFloorFootprintArea() : (long) dx * dz;
        float area = (float) floor / Math.max(1, residents);
        int score = Math.round(35 * Math.min(1.0F, area / ROOMY_AREA));
        // lamps and torches near the bed: up to 15 points
        score += Math.round(15 * Math.min(1.0F, level.getBrightness(LightLayer.BLOCK, bed.above()) / 12.0F));
        // decoration and a chest
        int decoration = 0;
        boolean chest = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(DECORATION)) {
                decoration++;
            }
            if (!chest && VillageStorage.getContainer(level, pos).isPresent()) {
                chest = true;
            }
        }
        score += Math.min(40, decoration * DECORATION_POINTS);
        score += chest ? 10 : 0;
        return Math.min(100, score);
    }

    private static int residents(Village village, VillageBuilding home) {
        int[] count = {0};
        village.forEachResidentHome((resident, bed) -> {
            if (PropertyRules.isInside(home, bed, 0)) {
                count[0]++;
            }
        });
        return Math.max(1, count[0]);
    }

    public static List<Needs.Thought> thoughts(VillagerEntityMCA villager) {
        if (!Skills.enabled()) {
            return List.of();
        }
        int score = score(villager);
        // neat villagers care twice as much about their home, slobs do not mind a cramped one
        int weight = villager.getTraits().hasTrait(PersonalityTraits.NEAT) ? 2 : 1;
        if (score >= COZY) {
            return List.of(new Needs.Thought(Needs.Need.ENERGY, "needs.thought.home.cozy", 2 * weight));
        }
        if (score >= 0 && score < CRAMPED && !villager.getTraits().hasTrait(PersonalityTraits.SLOB)) {
            return List.of(new Needs.Thought(Needs.Need.ENERGY, "needs.thought.home.cramped", -2 * weight));
        }
        return List.of();
    }
}
