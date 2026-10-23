package net.conczin.mca.entity.ai.queue;

import net.conczin.mca.entity.ai.SchedulesMCA;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** What a villager can have in its action queue, with the brain activity that carries it out. */
public enum ActionType {
    SLEEP(Activity.REST),
    EAT(Activity.IDLE),
    DRINK(Activity.IDLE),
    SOCIALIZE(Activity.IDLE),
    HIDE(Activity.HIDE),
    WORK(Activity.WORK),
    MEET(Activity.MEET),
    LEISURE(Activity.IDLE),
    GO_HOME(Activity.IDLE);

    private final Activity activity;

    ActionType(Activity activity) {
        this.activity = activity;
    }

    public Activity activity() {
        return activity;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "queue.action." + id();
    }

    public ItemStack icon() {
        Item item = switch (this) {
            case SLEEP -> Items.BED.red();
            case EAT -> Items.BREAD;
            case DRINK -> Items.WATER_BUCKET;
            case SOCIALIZE -> Items.BELL;
            case HIDE -> Items.SHIELD;
            case WORK -> Items.IRON_PICKAXE;
            case MEET -> Items.EMERALD;
            case LEISURE -> Items.MUSIC_DISC_CAT;
            case GO_HOME -> Items.OAK_DOOR;
        };
        return new ItemStack(item);
    }

    /** The action a schedule activity stands for. */
    public static ActionType ofActivity(Activity activity) {
        if (activity == Activity.REST) {
            return SLEEP;
        } else if (activity == Activity.WORK) {
            return WORK;
        } else if (activity == Activity.MEET) {
            return MEET;
        }
        return LEISURE;
    }

    public static ActionType ofLetter(char letter) {
        return ofActivity(SchedulesMCA.activityOf(letter));
    }

    public static @Nullable ActionType byId(String id) {
        for (ActionType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
