package net.conczin.mca.expansion.minds.routine;

import net.conczin.mca.entity.ai.Needs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Something the world offers a villager with free time, The Sims style: a place, an object or
 * someone that lets it do something that looks alive. Places and things advertise what can be done
 * with them; the villager picks what suits it best right now.
 *
 * @param spot     where the villager stands (or sits) to do it
 * @param lookAt   what it looks at meanwhile
 * @param partner  who or what it does it with: a friend, a cat, a chair to sit on, a display stand
 * @param appeal   how attractive it is before needs, distance and variety are weighed in
 * @param hold     an item the villager holds meanwhile (a book to read, a copy of the displayed tool)
 * @param particle a particle shown now and then
 * @param action   behaviour of its own, from an integration with another mod
 */
public record Opportunity(Kind kind, BlockPos spot, @Nullable BlockPos lookAt, @Nullable Entity partner, float appeal,
                          @Nullable ItemStack hold, @Nullable ParticleOptions particle, Opportunities.@Nullable Action action) {

    public Opportunity(Kind kind, BlockPos spot, @Nullable BlockPos lookAt, @Nullable Entity partner, float appeal) {
        this(kind, spot, lookAt, partner, appeal, null, null, null);
    }

    /** What can be done, which need it serves and how long it lasts (in ticks). */
    public enum Kind {
        SIT(Needs.Need.ENERGY, 1.0F, 600, 1600),
        CHAT(Needs.Need.SOCIAL, 1.2F, 400, 1200),
        GATHER(Needs.Need.SOCIAL, 0.8F, 400, 1000),
        READ(Needs.Need.FUN, 0.9F, 400, 1000),
        LISTEN(Needs.Need.FUN, 1.1F, 300, 800),
        WARM(Needs.Need.SAFETY, 0.8F, 300, 800),
        PET(Needs.Need.FUN, 1.0F, 200, 500),
        ADMIRE(Needs.Need.FUN, 0.6F, 200, 500),
        /** Takes a look at something on display, holding a copy of it and turning it in the hands. */
        INSPECT(Needs.Need.FUN, 1.0F, 300, 700),
        /** Plays music on something held, e.g. an instrument of another mod; others come and listen. */
        PLAY(Needs.Need.FUN, 1.1F, 600, 1600),
        /** Takes a serving of a feast on the table and eats it in company. */
        FEAST(Needs.Need.HUNGER, 1.0F, 300, 700),
        WATER(Needs.Need.FUN, 0.6F, 300, 700),
        HOME(Needs.Need.SAFETY, 0.5F, 400, 1000),
        /** The fallback: just being near someone. */
        NEAR_OTHERS(Needs.Need.SOCIAL, 0.2F, 300, 600);

        private final Needs.Need need;
        private final float weight;
        private final int minTicks;
        private final int maxTicks;

        Kind(Needs.Need need, float weight, int minTicks, int maxTicks) {
            this.need = need;
            this.weight = weight;
            this.minTicks = minTicks;
            this.maxTicks = maxTicks;
        }

        public Needs.Need need() {
            return need;
        }

        public float weight() {
            return weight;
        }

        public int minTicks() {
            return minTicks;
        }

        public int maxTicks() {
            return maxTicks;
        }
    }
}
