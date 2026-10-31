package net.conczin.mca.api.addon;

import net.conczin.mca.MCA;
import net.conczin.mca.addon.BuiltInAddons;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The addons MCA knows, and the calls MCA's classes make into all of them. The addons shipped in this jar register
 * themselves the first time the list is needed ({@link BuiltInAddons}); others call {@link #register}.
 */
public final class Addons {
    private static final List<McaAddon> ADDONS = new ArrayList<>();
    private static boolean builtIn;
    private static boolean bootstrapped;
    private static boolean clientBootstrapped;

    private Addons() {
    }

    public static void register(McaAddon addon) {
        if (ADDONS.stream().anyMatch(a -> a.id().equals(addon.id()))) {
            throw new IllegalArgumentException("Duplicate MCA addon '" + addon.id() + "'");
        }
        ADDONS.add(addon);
    }

    public static List<McaAddon> all() {
        if (!builtIn) {
            builtIn = true;
            BuiltInAddons.register(); // expansion: all
        }
        return Collections.unmodifiableList(ADDONS);
    }

    public static Optional<McaAddon> byId(String id) {
        return all().stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /** Once, from MCA's registries. */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        for (McaAddon addon : all()) {
            addon.bootstrap();
            MCA.LOGGER.info("MCA addon '{}' loaded{}", addon.id(), addon.enabled() ? "" : " (switched off)");
        }
    }

    /** Once, on the client, from whatever needs the addons' client parts first. */
    public static void clientBootstrap() {
        if (clientBootstrapped) {
            return;
        }
        clientBootstrapped = true;
        all().forEach(McaAddon::clientBootstrap);
    }

    public static void registerMessages(Network.Registrar registrar) {
        all().forEach(addon -> addon.registerMessages(registrar));
    }

    public static void levelTick(ServerLevel level) {
        all().forEach(addon -> addon.levelTick(level));
    }

    public static void villageTick(ServerLevel level, Village village, long time) {
        all().forEach(addon -> addon.villageTick(level, village, time));
    }

    public static void villagerTick(ServerLevel level, VillagerEntityMCA villager) {
        all().forEach(addon -> addon.villagerTick(level, villager));
    }

    public static boolean refusesInteraction(VillagerEntityMCA villager, Player player) {
        return all().stream().anyMatch(addon -> addon.refusesInteraction(villager, player));
    }

    /** The addon that replaces this entity as it joins the world, if any. */
    public static @Nullable McaAddon spawnReplacer(Entity entity) {
        return all().stream().filter(addon -> addon.replacesSpawn(entity)).findFirst().orElse(null);
    }

    public static boolean fightsLikeMercenary(VillagerProfession profession) {
        return all().stream().anyMatch(addon -> addon.fightsLikeMercenary(profession));
    }

    /**
     * An addon's own data on a villager, saved with it. The data of every addon is kept, so removing an addon does
     * not erase what it stored: it is there again when the addon comes back.
     */
    public static CompoundTag data(VillagerEntityMCA villager, String addonId) {
        CompoundTag all = villager.getAddonData();
        if (!(all.get(addonId) instanceof CompoundTag tag)) {
            CompoundTag created = new CompoundTag();
            all.put(addonId, created);
            return created;
        }
        return tag;
    }
}
