package net.conczin.mca.expansion.requests;

import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.expansion.HomeChests;
import net.conczin.mca.server.world.data.workorder.VillageStorage;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Villager requests: a villager asks a player for a delivery and sets the reward aside from its own
 * home chests right away (escrow), so it can't be spent or stolen in between. Delivering earns
 * hearts by difficulty; letting it run out costs some and the reward goes back home.
 * <p>
 * This is the core that works on its own. A quest mod (Bountiful, once it supports this
 * Minecraft version) can plug in as a {@link RequestBoard}.
 */
public final class Requests {
    /** The active request, empty when there is none. */
    private static final CDataParameter<CompoundTag> REQUEST = CParameter.create("VillagerRequest", new CompoundTag());
    /** Whether the home chests got their first savings. */
    private static final CDataParameter<Boolean> SAVINGS = CParameter.create("SavingsFilled", false);

    private static final long DURATION = 3 * 24000L;
    private static final int MIN_BUDGET = 16;
    private static final int MAX_BUDGET = 128;
    private static final int EXPIRY_HEARTS = -3;

    /**
     * What a request asks and promises: a delivery of {@code count} {@code item}, or, with a
     * {@code target}, defeating that many of a mob (counted from the player's statistics since
     * {@code baseline}).
     */
    public record Request(UUID player, Item item, @Nullable EntityType<?> target, int count, int baseline,
                          List<ItemStack> rewards, int hearts, long deadline) {
        public int rewardWorth() {
            return rewards.stream().mapToInt(RequestPool::worth).sum();
        }

        public boolean isHunt() {
            return target != null;
        }

        public Component what() {
            return target != null ? target.getDescription() : item.getName(new ItemStack(item));
        }
    }

    /** How many of a mob the player has defeated in total; 0 when unknown. */
    static int kills(Player player, EntityType<?> type) {
        return player instanceof ServerPlayer serverPlayer ? serverPlayer.getStats().getValue(Stats.ENTITY_KILLED.get(type)) : 0;
    }

    /** How far the player is with a request. */
    public static int progress(Player player, Request request) {
        return request.isHunt() ? kills(player, request.target()) - request.baseline() : player.getInventory().countItem(request.item());
    }

    /** Where requests can also be shown, e.g. a quest mod's board. */
    public interface RequestBoard {
        /** Offers the request on the board; false when the board can't take it. */
        boolean offer(Player player, VillagerEntityMCA villager, Request request);
    }

    private static @Nullable RequestBoard board;

    private Requests() {
    }

    public static void setBoard(@Nullable RequestBoard requestBoard) {
        board = requestBoard;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(REQUEST, SAVINGS);
    }

    public static boolean enabled() {
        return Config.getServerConfig().villagerRequests;
    }

    // ---------------------------------------------------------------- storage

    public static Optional<Request> get(VillagerEntityMCA villager) {
        CompoundTag tag = villager.getTrackedValue(REQUEST);
        if (!tag.contains("player")) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(tag.getStringOr("item", "minecraft:air")));
        EntityType<?> target = tag.contains("entity")
                ? BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(tag.getStringOr("entity", "minecraft:pig"))) : null;
        List<ItemStack> rewards = new ArrayList<>();
        var ops = villager.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag reward : tag.getListOrEmpty("rewards")) {
            ItemStack.CODEC.parse(ops, reward).result().ifPresent(rewards::add);
        }
        return Optional.of(new Request(UUID.fromString(tag.getStringOr("player", "")), item, target, tag.getIntOr("count", 1),
                tag.getIntOr("baseline", 0), rewards, tag.getIntOr("hearts", 0), tag.getLongOr("deadline", 0)));
    }

    public static void set(VillagerEntityMCA villager, @Nullable Request request) {
        CompoundTag tag = new CompoundTag();
        if (request != null) {
            tag.putString("player", request.player().toString());
            tag.putString("item", BuiltInRegistries.ITEM.getKey(request.item()).toString());
            tag.putInt("count", request.count());
            if (request.target() != null) {
                tag.putString("entity", BuiltInRegistries.ENTITY_TYPE.getKey(request.target()).toString());
                tag.putInt("baseline", request.baseline());
            }
            tag.putInt("hearts", request.hearts());
            tag.putLong("deadline", request.deadline());
            ListTag rewards = new ListTag();
            var ops = villager.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (ItemStack reward : request.rewards()) {
                ItemStack.CODEC.encodeStart(ops, reward).result().ifPresent(rewards::add);
            }
            tag.put("rewards", rewards);
        }
        villager.setTrackedValue(REQUEST, tag);
    }

    // ---------------------------------------------------------------- talking

    /** The player asked "need any help?": offer a request, remind of it, or take the delivery. */
    public static void interact(Player player, VillagerEntityMCA villager) {
        if (!enabled() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        expireIfDue(level, villager);
        Optional<Request> active = get(villager);
        if (active.isEmpty()) {
            announce(level, villager, player, create(level, player, villager));
            return;
        }
        Request request = active.get();
        if (!request.player().equals(player.getUUID())) {
            say(villager, player, Component.translatable("request.busy"));
            return;
        }
        int have = progress(player, request);
        if (have < request.count()) {
            say(villager, player, Component.translatable(request.isHunt() ? "request.reminder.hunt" : "request.reminder",
                    request.count(), request.what(), have, days(level, request)));
            return;
        }
        complete(player, villager, request);
    }

    private static void announce(ServerLevel level, VillagerEntityMCA villager, Player player, Request request) {
        say(villager, player, Component.translatable(request.isHunt() ? "request.ask.hunt" : "request.ask", request.count(),
                request.what(), describeRewards(request), days(level, request)));
        if (board != null) {
            board.offer(player, villager, request);
        }
    }

    // ---------------------------------------------------------------- the request board

    /** Requests a player may have open in one village through the board. */
    private static final int BOARD_LIMIT = 3;

    private static List<VillagerEntityMCA> residentsNear(ServerLevel level, BlockPos pos) {
        return VillageManager.get(level).findNearestVillage(pos, Village.BORDER_MARGIN)
                .map(village -> village.getResidents(level))
                .orElse(List.of());
    }

    /** Someone in the village who needs help posts a request for the player. */
    public static void takeFromBoard(ServerLevel level, BlockPos pos, Player player) {
        List<VillagerEntityMCA> residents = residentsNear(level, pos);
        if (residents.isEmpty()) {
            player.sendSystemMessage(Component.translatable("request.board.no_village"));
            return;
        }
        residents.forEach(villager -> expireIfDue(level, villager));
        long mine = residents.stream().filter(v -> get(v).filter(r -> r.player().equals(player.getUUID())).isPresent()).count();
        if (mine >= BOARD_LIMIT) {
            player.sendSystemMessage(Component.translatable("request.board.full", BOARD_LIMIT));
            return;
        }
        List<VillagerEntityMCA> free = residents.stream()
                .filter(v -> v.isAlive() && v.getAgeState().isAdult() && get(v).isEmpty())
                .toList();
        if (free.isEmpty()) {
            player.sendSystemMessage(Component.translatable("request.board.empty"));
            return;
        }
        VillagerEntityMCA villager = free.get(level.getRandom().nextInt(free.size()));
        announce(level, villager, player, create(level, player, villager));
        player.sendSystemMessage(Component.translatable("request.board.posted", villager.getDisplayName()).withStyle(ChatFormatting.GRAY));
    }

    /** Lists the requests the player took in the village. */
    public static void listOnBoard(ServerLevel level, BlockPos pos, Player player) {
        boolean any = false;
        for (VillagerEntityMCA villager : residentsNear(level, pos)) {
            Optional<Request> request = get(villager).filter(r -> r.player().equals(player.getUUID()));
            if (request.isPresent()) {
                any = true;
                Request r = request.get();
                player.sendSystemMessage(Component.translatable("request.board.entry", villager.getDisplayName(),
                        Math.max(0, progress(player, r)), r.count(), r.what(), days(level, r)));
            }
        }
        if (!any) {
            player.sendSystemMessage(Component.translatable("request.board.none"));
        }
    }

    private static void say(VillagerEntityMCA villager, Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            villager.sendChatMessage(message, serverPlayer);
        }
    }

    private static Component describeRewards(Request request) {
        if (request.rewards().isEmpty()) {
            return Component.translatable("request.reward.thanks");
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < request.rewards().size(); i++) {
            ItemStack reward = request.rewards().get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(reward.getCount() + " ").append(reward.getHoverName());
        }
        return text.withStyle(ChatFormatting.GOLD);
    }

    private static long days(ServerLevel level, Request request) {
        return Math.max(1, (request.deadline() - level.getGameTime() + 23999) / 24000);
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * A new request: the reward is taken from the home chests now, as much as the villager can
     * spare and trusts the player with; the task matches the reward's worth.
     */
    public static Request create(ServerLevel level, Player player, VillagerEntityMCA villager) {
        fillSavings(level, villager);
        Memories memories = villager.getVillagerBrain().getMemoriesForPlayer(player);
        int budget = Math.clamp(MIN_BUDGET + memories.getHearts() / 2, MIN_BUDGET, MAX_BUDGET);
        List<ItemStack> rewards = takeRewards(level, villager, budget);
        int worth = rewards.stream().mapToInt(RequestPool::worth).sum();
        RequestPool.Objective objective = RequestPool.objectiveFor(Math.max(2, worth), villager.getRandom());
        int hearts = Math.clamp(3 + objective.worth() / 2, 3, 25);
        Request request = new Request(player.getUUID(), objective.item(), objective.target(), objective.count(),
                objective.target() == null ? 0 : kills(player, objective.target()), rewards, hearts, level.getGameTime() + DURATION);
        set(villager, request);
        return request;
    }

    /** The most valuable things from the home chests, up to the budget, removed from the chests. */
    static List<ItemStack> takeRewards(ServerLevel level, VillagerEntityMCA villager, int budget) {
        List<ItemStack> rewards = new ArrayList<>();
        int left = budget;
        List<Container> chests = HomeChests.of(level, villager).stream()
                .map(pos -> VillageStorage.getContainer(level, pos))
                .flatMap(Optional::stream)
                .toList();
        record Slot(Container container, int index, int each) {
        }
        List<Slot> slots = new ArrayList<>();
        for (Container chest : chests) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                int each = RequestPool.worth(stack.copyWithCount(1));
                if (!stack.isEmpty() && each > 0) {
                    slots.add(new Slot(chest, i, each));
                }
            }
        }
        slots.sort(Comparator.comparingInt(Slot::each).reversed());
        for (Slot slot : slots) {
            if (left <= 0) {
                break;
            }
            ItemStack stack = slot.container().getItem(slot.index());
            int take = Math.min(stack.getCount(), left / slot.each());
            if (take <= 0) {
                continue;
            }
            rewards.add(stack.split(take));
            slot.container().setChanged();
            left -= take * slot.each();
        }
        return rewards;
    }

    private static void complete(Player player, VillagerEntityMCA villager, Request request) {
        int left = request.isHunt() ? 0 : request.count();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(request.item())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        if (!request.isHunt()) {
            villager.getInventory().addItem(new ItemStack(request.item(), request.count()));
        }
        for (ItemStack reward : request.rewards()) {
            if (!player.getInventory().add(reward.copy())) {
                player.drop(reward.copy(), false);
            }
        }
        villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(request.hearts());
        set(villager, null);
        say(villager, player, Component.translatable("request.thanks", request.hearts()));
    }

    /** A request past its deadline ends: the reward goes back home and the player loses some hearts. */
    public static void expireIfDue(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Request> request = get(villager);
        if (request.isEmpty() || level.getGameTime() < request.get().deadline()) {
            return;
        }
        returnRewards(level, villager, request.get().rewards());
        Memories memories = villager.getVillagerBrain().getMemories().get(request.get().player());
        if (memories != null) {
            memories.modHearts(EXPIRY_HEARTS);
        }
        set(villager, null);
    }

    private static void returnRewards(ServerLevel level, VillagerEntityMCA villager, List<ItemStack> rewards) {
        List<BlockPos> chests = HomeChests.of(level, villager);
        for (ItemStack reward : rewards) {
            ItemStack rest = reward.copy();
            for (BlockPos pos : chests) {
                Optional<Container> chest = VillageStorage.getContainer(level, pos);
                if (chest.isPresent() && !rest.isEmpty()) {
                    rest = VillageStorage.insert(chest.get(), rest);
                }
            }
            rest = villager.getInventory().addItem(rest);
            if (!rest.isEmpty()) {
                villager.spawnAtLocation(level, rest);
            }
        }
    }

    // ---------------------------------------------------------------- savings

    /**
     * The first time a villager offers a request, its home chests get its savings from the loot
     * table of its profession ({@code mca:homes/<profession>}, else {@code mca:homes/default}).
     */
    public static void fillSavings(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getTrackedValue(SAVINGS)) {
            return;
        }
        List<BlockPos> chests = HomeChests.of(level, villager);
        if (chests.isEmpty()) {
            return;
        }
        Identifier profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getProfession());
        LootTable table = lootTable(level, MCA.locate("homes/" + (profession == null ? "default" : profession.getPath())));
        if (table == LootTable.EMPTY) {
            table = lootTable(level, MCA.locate("homes/default"));
        }
        List<ItemStack> savings = table.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .create(LootContextParamSets.CHEST));
        for (ItemStack stack : savings) {
            ItemStack rest = stack;
            for (BlockPos pos : chests) {
                Optional<Container> chest = VillageStorage.getContainer(level, pos);
                if (chest.isPresent() && !rest.isEmpty()) {
                    rest = VillageStorage.insert(chest.get(), rest);
                }
            }
        }
        villager.setTrackedValue(SAVINGS, true);
    }

    private static LootTable lootTable(ServerLevel level, Identifier id) {
        return level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
    }

    static RegistryAccess registries(VillagerEntityMCA villager) {
        return villager.registryAccess();
    }
}
