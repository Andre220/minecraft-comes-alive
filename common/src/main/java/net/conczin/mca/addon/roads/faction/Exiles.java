package net.conczin.mca.addon.roads.faction;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Comparator;
import java.util.Optional;

/**
 * The cast out band together. A villager made an outlaw who lives through it leaves the village for the exiles' camp
 * nearby and becomes one of them, a bandit of the exiles. With no exiles' camp within reach, and room for one more
 * band, the outlaw makes a camp of their own a little way off: a campfire and a banner, the start of a new band, which
 * grows as more are cast out.
 */
final class Exiles {
    static final String EXILES = "exiles";
    /** The chance each second that an outlaw thinks of leaving. */
    private static final float CHANCE = 0.1F;
    /** How far an outlaw goes to find the exiles. */
    private static final double REACH = 600.0;
    private static final int CAMP_NEAR = 48;
    private static final int CAMP_FAR = 80;

    private Exiles() {
    }

    /** From the villager tick of an outlaw. */
    static void tick(ServerLevel level, VillagerEntityMCA outlaw) {
        if (!Factions.enabled() || level.getRandom().nextFloat() > CHANCE || outlaw.isBaby() || !outlaw.isAlive()) {
            return;
        }
        FactionData data = FactionData.get(level);
        BlockPos at = outlaw.blockPosition();
        Optional<FactionData.Outpost> camp = data.outposts().stream()
                .filter(o -> data.faction(o.faction()).filter(f -> EXILES.equals(f.type())).isPresent())
                .filter(o -> o.pos().distSqr(at) < REACH * REACH)
                .min(Comparator.comparingDouble(o -> o.pos().distSqr(at)));
        if (camp.isEmpty()) {
            camp = makeCamp(level, data, outlaw);
        }
        camp.ifPresent(found -> {
            takeIn(outlaw, found.faction());
            Bandits.march(outlaw, found.pos());
        });
    }

    /** A new exiles' band and its camp, a little way from the outlaw; none while there is no room or the land is not loaded. */
    private static Optional<FactionData.Outpost> makeCamp(ServerLevel level, FactionData data, VillagerEntityMCA outlaw) {
        if (data.factions().size() >= Config.getServerConfig().factionMax) {
            return Optional.empty();
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = CAMP_NEAR + random.nextInt(CAMP_FAR - CAMP_NEAR);
        int x = outlaw.getBlockX() + (int) (Math.cos(angle) * distance);
        int z = outlaw.getBlockZ() + (int) (Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return Optional.empty();
        }
        BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        FactionData.Faction band = Factions.create(level, data, FactionTypes.get(EXILES));
        FactionData.Outpost camp = new FactionData.Outpost(ground, band.id());
        data.addOutpost(camp);
        if (level.getBlockState(ground).canBeReplaced() && level.getBlockState(ground.east(2)).canBeReplaced()) {
            level.setBlock(ground, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(ground.east(2), BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(band.base().getSerializedName() + "_banner")).defaultBlockState(), Block.UPDATE_ALL);
            Factions.repaintLater(data, ground); // the banner takes the band's colors
        }
        FactionWars.tell(level, Component.translatable("faction.exiles_camp", Factions.name(band), ground.getX(), ground.getZ()));
        return Optional.of(camp);
    }

    /** An outlaw leaves their village and becomes one of a band, armed as its people are. */
    static void takeIn(VillagerEntityMCA outlaw, int faction) {
        if (outlaw.getProfession() != ProfessionsMCA.OUTLAW && !Bandits.isBandit(outlaw)) {
            return;
        }
        outlaw.getResidency().leaveVillage();
        outlaw.setProfession(Bandits.BANDIT);
        outlaw.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        Factions.setFaction(outlaw, faction);
        outlaw.setPersistenceRequired();
        if (outlaw.getMainHandItem().isEmpty() && outlaw.level() instanceof ServerLevel level) {
            String type = FactionData.get(level).faction(faction).map(FactionData.Faction::type).orElse(FactionTypes.RAIDERS);
            Item weapon = FactionTypes.get(type).weapon(level.getRandom());
            if (weapon != null) {
                outlaw.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            }
        }
    }
}
