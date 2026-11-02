package net.conczin.mca.server.world;

import net.conczin.mca.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.jspecify.annotations.Nullable;

/**
 * Longer (or shorter) days and nights: the overworld clock runs slower or faster, the day half and the night half each
 * as configured. Everything that follows the clock stretches with it: the sun, the moon, the villagers' routines.
 * Left alone while both halves keep the vanilla ten minutes, so {@code /time rate} still works then.
 */
public final class DayLength {
    /** A vanilla day half (sunrise to sunset, or sunset to sunrise) lasts this many real minutes. */
    public static final float VANILLA_MINUTES = 10.0F;
    private static final int DAY_TICKS = 24000;
    private static final int CHECK_INTERVAL = 20;

    private static @Nullable MinecraftServer server;
    /** The rate set last on this server, NaN while the clock was left alone. */
    private static float applied = Float.NaN;
    private static int ticks;

    private DayLength() {
    }

    public static void tick(MinecraftServer current) {
        if (current != server) {
            server = current;
            applied = Float.NaN;
        }
        if (++ticks % CHECK_INTERVAL != 0) {
            return;
        }
        Config config = Config.getInstance();
        boolean active = config.dayLengthMinutes != VANILLA_MINUTES || config.nightLengthMinutes != VANILLA_MINUTES;
        if (!active && Float.isNaN(applied)) {
            return;
        }
        Holder<WorldClock> clock = current.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        ServerClockManager manager = current.overworld().clockManager();
        float rate;
        if (active) {
            boolean night = Math.floorMod(manager.getTotalTicks(clock), DAY_TICKS) >= DAY_TICKS / 2;
            float minutes = night ? config.nightLengthMinutes : config.dayLengthMinutes;
            rate = VANILLA_MINUTES / Math.clamp(minutes, 0.5F, 600.0F);
        } else {
            // back to vanilla after the config changed
            rate = 1.0F;
        }
        if (rate != applied) {
            manager.setRate(clock, rate);
            applied = active ? rate : Float.NaN;
        }
    }

    /** How many real minutes a whole day and night take with the current settings. */
    public static float fullDayMinutes() {
        Config config = Config.getInstance();
        return Math.clamp(config.dayLengthMinutes, 0.5F, 600.0F) + Math.clamp(config.nightLengthMinutes, 0.5F, 600.0F);
    }
}
