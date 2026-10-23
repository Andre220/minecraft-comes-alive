package net.conczin.mca.expansion.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shutters and casement windows swing open and shut with an ease in and out, like the animated doors mods do it.
 * This only remembers, on the client, when the block last changed so its renderer can play the swing.
 */
public class SwingBlockEntity extends BlockEntity {
    /** How long a swing takes, in ms. */
    private static final float DURATION = 350.0F;

    private boolean seen;
    private boolean open;
    private float from;
    private long changed;

    public SwingBlockEntity(BlockPos pos, BlockState state) {
        super(Shutters.SWING, pos, state);
    }

    /** How open it looks now, 0 shut to 1 open, playing the swing after a change. */
    public float openAmount(boolean nowOpen, long millis) {
        if (!seen) {
            seen = true;
            open = nowOpen;
            changed = 0;
        } else if (nowOpen != open) {
            from = current(millis);
            open = nowOpen;
            changed = millis;
        }
        return current(millis);
    }

    private float current(long millis) {
        float target = open ? 1.0F : 0.0F;
        if (changed == 0) {
            return target;
        }
        float t = Math.min(1.0F, (millis - changed) / DURATION);
        if (t >= 1.0F) {
            changed = 0;
            return target;
        }
        return from + (target - from) * ease(t);
    }

    /** Ease in and out (cubic): slow start, quick middle, soft stop. */
    static float ease(float t) {
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }
}
