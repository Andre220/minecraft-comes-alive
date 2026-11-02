package net.conczin.mca.addon.roads;

import net.conczin.mca.addon.roads.caravan.Caravans;
import net.conczin.mca.addon.roads.faction.Bandits;
import net.conczin.mca.addon.roads.faction.FactionMapMessages;
import net.conczin.mca.addon.roads.faction.FactionTypes;
import net.conczin.mca.addon.roads.faction.Factions;
import net.conczin.mca.addon.roads.faction.Redemption;
import net.conczin.mca.api.addon.McaAddon;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.Network;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;

/**
 * MCA Reborn Addons: Bands and Roads. Factions with names, banners, outposts and lands, whose pillagers become MCA
 * people (bandits) who fight, deal and go to war with each other; and caravans travelling and trading between the
 * villages players know, ambushed by the bands on the road.
 */
public final class RoadsAddon implements McaAddon {
    @Override
    public String id() {
        return "roads";
    }

    @Override
    public boolean enabled() {
        return Factions.enabled() || Caravans.enabled();
    }

    @Override
    public void bootstrap() {
        Caravans.bootstrap();
        Bandits.bootstrap();
        Constraint.register("bandit", (villager, player) -> Bandits.isBandit(villager.asEntity()));
        Constraint.register("bandit_friend", (villager, player) -> Bandits.trusts(villager.asEntity(), player));
        Constraint.register("caravan", (villager, player) -> villager instanceof VillagerEntityMCA mca && Caravans.isMember(mca));
    }

    @Override
    public void reloadListeners(BiConsumer<Identifier, PreparableReloadListener> register) {
        register.accept(FactionTypes.ID, new FactionTypes());
    }

    @Override
    public void clientBootstrap() {
        RoadsClient.init();
    }

    @Override
    public void registerMessages(Network.Registrar registrar) {
        registrar.register(FactionMapMessages.Response.TYPE, FactionMapMessages.Response.STREAM_CODEC, false);
        registrar.register(FactionMapMessages.Request.TYPE, FactionMapMessages.Request.STREAM_CODEC, true);
    }

    @Override
    public void levelTick(ServerLevel level) {
        Factions.levelTick(level);
    }

    @Override
    public void villageTick(ServerLevel level, Village village, long time) {
        Caravans.villageTick(level, village, time);
    }

    @Override
    public void villagerTick(ServerLevel level, VillagerEntityMCA villager) {
        if (Caravans.enabled()) {
            Caravans.tickMember(level, villager);
        }
        if (Factions.enabled() && Bandits.isBandit(villager)) {
            Bandits.tick(level, villager);
        }
        if (Factions.enabled() && villager.getProfession() == ProfessionsMCA.OUTLAW) {
            Bandits.tickOutlaw(level, villager);
        }
    }

    @Override
    public void requestCompleted(ServerPlayer player, VillagerEntityMCA villager) {
        Redemption.requestDone(player, villager);
    }

    @Override
    public boolean refusesInteraction(VillagerEntityMCA villager, Player player) {
        return Bandits.refusesTalk(villager, player);
    }

    @Override
    public boolean replacesSpawn(Entity entity) {
        return Bandits.converts(entity);
    }

    @Override
    public void replaceSpawn(Entity entity) {
        Bandits.convert((Pillager) entity);
    }

    @Override
    public boolean fightsLikeMercenary(VillagerProfession profession) {
        return profession == Bandits.BANDIT;
    }
}
