package net.conczin.mca.entity.ai;

import com.mojang.math.Transformation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.mixin.DisplayAccessor;
import net.conczin.mca.mixin.ItemDisplayAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import java.util.List;

/**
 * Icons over a villager's head telling why it suffers, e.g. bread crossed out when it takes
 * starvation damage. They are item displays riding the villager, so they follow its head on every
 * client without extra syncing, and are removed after a few seconds.
 */
public final class NeedIndicators {
    private static final String TAG = "mca_need_icon";
    private static final String UNTIL = TAG + "_until:";
    private static final int DURATION = 80;

    private NeedIndicators() {
    }

    /** Shows an item crossed out (a barrier over it) above the villager. */
    public static void showLack(VillagerEntityMCA villager, Item item) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        clear(villager);
        long until = level.getGameTime() + DURATION;
        spawn(level, villager, new ItemStack(item), 0.35F, 0.0F, until);
        spawn(level, villager, new ItemStack(Items.BARRIER), 0.42F, 0.02F, until);
    }

    private static void spawn(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, float scale, float depth, long until) {
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return;
        }
        ((ItemDisplayAccessor) display).mca$setItemStack(stack);
        ((ItemDisplayAccessor) display).mca$setItemTransform(ItemDisplayContext.GUI);
        ((DisplayAccessor) display).mca$setBillboardConstraints(Display.BillboardConstraints.CENTER);
        ((DisplayAccessor) display).mca$setTransformation(new Transformation(new Vector3f(0.0F, 0.45F, depth), null, new Vector3f(scale), null));
        display.addTag(TAG);
        display.addTag(UNTIL + until);
        display.setPos(villager.getX(), villager.getY() + villager.getBbHeight(), villager.getZ());
        level.addFreshEntity(display);
        display.startRiding(villager, true, true);
    }

    /** Removes icons whose time is up. */
    public static void tick(VillagerEntityMCA villager) {
        if (villager.getPassengers().isEmpty()) {
            return;
        }
        long time = villager.level().getGameTime();
        for (Entity passenger : List.copyOf(villager.getPassengers())) {
            for (String tag : passenger.entityTags()) {
                if (tag.startsWith(UNTIL) && time >= Long.parseLong(tag.substring(UNTIL.length()))) {
                    passenger.discard();
                    break;
                }
            }
        }
    }

    private static void clear(VillagerEntityMCA villager) {
        for (Entity passenger : List.copyOf(villager.getPassengers())) {
            if (passenger.entityTags().contains(TAG)) {
                passenger.discard();
            }
        }
    }
}
