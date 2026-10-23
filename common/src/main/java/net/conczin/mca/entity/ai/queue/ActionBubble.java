package net.conczin.mca.entity.ai.queue;

import com.mojang.math.Transformation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.mixin.DisplayAccessor;
import net.conczin.mca.mixin.ItemDisplayAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * The icon of a villager's current action floating over its head, while a player is near enough
 * to care (it is an item display riding the villager, like NeedIndicators). Need icons, which
 * explain damage, take its place while shown.
 */
final class ActionBubble {
    private static final String TAG = "mca_action_bubble";
    private static final double PLAYER_RANGE = 16.0;
    private static final String NEED_ICON_TAG = "mca_need_icon";

    private ActionBubble() {
    }

    static void update(ServerLevel level, VillagerEntityMCA villager, @Nullable QueuedAction current) {
        boolean wanted = current != null && !villager.isSleeping()
                         && level.getNearestPlayer(villager, PLAYER_RANGE) != null
                         && villager.getPassengers().stream().noneMatch(p -> p.entityTags().contains(NEED_ICON_TAG));
        String typeTag = current == null ? "" : TAG + ":" + current.type().id();
        Display.ItemDisplay bubble = null;
        for (Entity passenger : List.copyOf(villager.getPassengers())) {
            if (passenger.entityTags().contains(TAG)) {
                if (wanted && bubble == null && passenger.entityTags().contains(typeTag) && passenger instanceof Display.ItemDisplay display) {
                    bubble = display;
                } else {
                    passenger.discard();
                }
            }
        }
        if (!wanted || bubble != null) {
            return;
        }
        Display.ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) {
            return;
        }
        ((ItemDisplayAccessor) display).mca$setItemStack(current.type().icon());
        ((ItemDisplayAccessor) display).mca$setItemTransform(ItemDisplayContext.GUI);
        ((DisplayAccessor) display).mca$setBillboardConstraints(Display.BillboardConstraints.CENTER);
        ((DisplayAccessor) display).mca$setTransformation(new Transformation(new Vector3f(0.0F, 0.45F, 0.0F), null, new Vector3f(0.3F), null));
        display.addTag(TAG);
        display.addTag(typeTag);
        display.setPos(villager.getX(), villager.getY() + villager.getBbHeight(), villager.getZ());
        level.addFreshEntity(display);
        display.startRiding(villager, true, true);
    }

    static void clear(VillagerEntityMCA villager) {
        for (Entity passenger : List.copyOf(villager.getPassengers())) {
            if (passenger.entityTags().contains(TAG)) {
                passenger.discard();
            }
        }
    }
}
