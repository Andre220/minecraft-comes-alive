package net.conczin.mca.expansion.minds;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * The village storage as everyone's pantry: a hungry or thirsty villager with nothing at home
 * fetches from it, and a worker whose tool broke takes a new one from it.
 */
public final class Provisions {
    private Provisions() {
    }

    /** The nearest storage container holding something wanted, when smarter villagers are on. */
    public static @Nullable BlockPos storageWith(ServerLevel level, VillagerEntityMCA villager, Predicate<ItemStack> wanted) {
        if (!Skills.enabled()) {
            return null;
        }
        Optional<Village> village = villager.getResidency().getHomeVillage().or(() -> Village.findNearest(villager));
        if (village.isEmpty() || !VillageStorage.exists(village.get())) {
            return null;
        }
        return VillageStorage.containers(level, village.get(), villager.blockPosition()).stream()
                .filter(pos -> VillageStorage.getContainer(level, pos).map(c -> contains(c, wanted)).orElse(false))
                .findFirst()
                .orElse(null);
    }

    public static boolean contains(Container container, Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty() && wanted.test(container.getItem(slot))) {
                return true;
            }
        }
        return false;
    }
}
