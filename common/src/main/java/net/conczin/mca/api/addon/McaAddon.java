package net.conczin.mca.api.addon;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

/**
 * An MCA Reborn addon: a themed expansion (feelings, needs, factions...) that plugs into MCA through these calls
 * instead of MCA's classes calling it. Everything is optional; an addon overrides what it uses. MCA calls each one
 * whether the addon is {@link #enabled()} or not, so the addon checks its own switches where they matter.
 */
public interface McaAddon {
    /** Short id, also the addon's key in villager data: "roads", "feelings". */
    String id();

    /** Whether any part of the addon is switched on in the config. */
    boolean enabled();

    /** Once, while MCA registers its blocks, items and professions: the addon's own, its dialogue actions and constraints. */
    default void bootstrap() {
    }

    /** Once, on the client, before the addon's screens and icons are first needed: map overlays, dialogue icons. */
    default void clientBootstrap() {
    }

    /** The addon's network messages, with MCA's. */
    default void registerMessages(Network.Registrar registrar) {
    }

    /** Every tick of a level that has MCA villages. */
    default void levelTick(ServerLevel level) {
    }

    /** Every tick of each village. */
    default void villageTick(ServerLevel level, Village village, long time) {
    }

    /** Once a second for each MCA villager, on the server. */
    default void villagerTick(ServerLevel level, VillagerEntityMCA villager) {
    }

    /** Whether the villager turns the player away instead of opening the interaction (the addon tells the player why). */
    default boolean refusesInteraction(VillagerEntityMCA villager, Player player) {
        return false;
    }

    /** Whether the addon replaces this entity as it joins the world; it is held back and {@link #replaceSpawn} called on the next tick. */
    default boolean replacesSpawn(Entity entity) {
        return false;
    }

    /** Puts the addon's own entity in place of one held back by {@link #replacesSpawn}, which is already removed. */
    default void replaceSpawn(Entity entity) {
    }

    /** Whether villagers of this profession get the mercenaries' combat brain. */
    default boolean fightsLikeMercenary(VillagerProfession profession) {
        return false;
    }
}
