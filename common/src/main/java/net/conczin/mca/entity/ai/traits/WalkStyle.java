package net.conczin.mca.entity.ai.traits;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * How a villager walks, given by its traits like the walk styles of the Sims. Each style scales the
 * swing of the legs and arms and adds a bounce, a sway of the shoulders, a tilt of the head and a lean
 * of the torso while walking. The model and the Fresh Animations pack (through the EMF variables
 * {@code mca_walk_*}) read the same numbers. When traits disagree, the style listed first wins.
 */
public enum WalkStyle {
    /** Elders take short steps and barely swing their arms; the stoop comes on top. */
    SHUFFLE(0.55F, 0.4F, 0.0F, 0.03F, 0.05F, 0.0F),
    /** Heavy, angry steps, the head pushed forward. */
    STOMP(1.15F, 0.7F, 0.35F, 0.0F, 0.12F, 0.1F),
    /** Head hanging, arms limp. */
    MOPEY(0.75F, 0.45F, 0.0F, 0.0F, 0.32F, 0.06F),
    /** Small steps, arms close, eyes on the ground. */
    TIMID(0.7F, 0.35F, 0.0F, 0.0F, 0.22F, 0.12F),
    /** Dragging the feet, slouched. */
    SLUGGISH(0.65F, 0.3F, 0.0F, 0.05F, 0.15F, 0.1F),
    /** A hop in every step, arms flailing. */
    BOUNCY(1.1F, 1.6F, 1.1F, 0.06F, 0.0F, 0.0F),
    /** Shoulders rolling, chin up, leaning back a little. */
    SWAGGER(1.0F, 1.15F, 0.2F, 0.14F, -0.14F, -0.05F),
    /** Long steps, a strong arm swing, a little forward. */
    BRISK(1.2F, 1.35F, 0.4F, 0.0F, -0.04F, 0.06F),
    NORMAL(1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    /** Multiplier of the leg swing. */
    public final float stride;
    /** Multiplier of the arm swing. */
    public final float arms;
    /** How high the body rises with each step, in model pixels. */
    public final float bounce;
    /** How far the shoulders roll from side to side with the steps, in radians. */
    public final float sway;
    /** How far the head tilts down (up when negative) while walking, in radians. */
    public final float head;
    /** How far the torso leans forward (back when negative) while walking, in radians. */
    public final float lean;

    WalkStyle(float stride, float arms, float bounce, float sway, float head, float lean) {
        this.stride = stride;
        this.arms = arms;
        this.bounce = bounce;
        this.sway = sway;
        this.head = head;
        this.lean = lean;
    }

    /** The style that wins between two traits. */
    public static WalkStyle stronger(WalkStyle a, WalkStyle b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    public Component getName() {
        return Component.translatable("trait.walk." + name().toLowerCase(Locale.ROOT));
    }
}
