package net.conczin.mca.expansion.minds.routine;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The extension point for integrations that need code (another mod's API): they register a
 * provider that offers opportunities around a villager. Integrations that only need block ids,
 * tags or entity types are data files instead (see {@link OpportunityTypes}).
 */
public final class Opportunities {
    /** Offers opportunities around a villager with free time. */
    @FunctionalInterface
    public interface Provider {
        void provide(ServerLevel level, VillagerEntityMCA villager, Consumer<Opportunity> offer);
    }

    /**
     * What a villager does while taking an opportunity that brings its own behaviour, e.g. playing
     * an instrument of another mod. Called on the server.
     */
    public interface Action {
        default void start(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
        }

        default void tick(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity, int ticks) {
        }

        default void stop(ServerLevel level, VillagerEntityMCA villager, Opportunity opportunity) {
        }
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private Opportunities() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    static void provide(ServerLevel level, VillagerEntityMCA villager, Consumer<Opportunity> offer) {
        for (Provider provider : PROVIDERS) {
            provider.provide(level, villager, offer);
        }
    }
}
