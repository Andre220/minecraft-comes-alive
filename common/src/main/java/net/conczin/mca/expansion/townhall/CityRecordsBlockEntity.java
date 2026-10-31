package net.conczin.mca.expansion.townhall;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Animates the book of the City Records like the enchanting table's (EnchantingTableBlockEntity.bookAnimationTick):
 * it turns to the nearest player and opens and leafs through itself while someone is near. It keeps nothing.
 */
public class CityRecordsBlockEntity extends BlockEntity {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final double NEAR = 3.0;
    private static final float TWO_PI = (float) (Math.PI * 2);

    public int time;
    public float flip;
    public float oFlip;
    private float flipT;
    private float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    private float tRot;

    public CityRecordsBlockEntity(BlockPos pos, BlockState state) {
        super(TownHall.RECORDS, pos, state);
    }

    void animate(Level level, BlockPos pos) {
        oOpen = open;
        oRot = rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NEAR, false);
        if (player != null) {
            tRot = (float) Mth.atan2(player.getZ() - (pos.getZ() + 0.5), player.getX() - (pos.getX() + 0.5));
            open += 0.1F;
            if (open < 0.5F || RANDOM.nextInt(40) == 0) {
                float old = flipT;
                do {
                    flipT += RANDOM.nextInt(4) - RANDOM.nextInt(4);
                } while (old == flipT);
            }
        } else {
            tRot += 0.02F;
            open -= 0.1F;
        }
        rot = wrap(rot);
        tRot = wrap(tRot);
        rot += wrap(tRot - rot) * 0.4F;
        open = Mth.clamp(open, 0.0F, 1.0F);
        time++;
        oFlip = flip;
        float diff = Mth.clamp((flipT - flip) * 0.4F, -0.2F, 0.2F);
        flipA += (diff - flipA) * 0.9F;
        flip += flipA;
    }

    /** An angle in [-pi, pi). */
    static float wrap(float angle) {
        while (angle >= Math.PI) {
            angle -= TWO_PI;
        }
        while (angle < -Math.PI) {
            angle += TWO_PI;
        }
        return angle;
    }
}
