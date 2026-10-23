package net.conczin.mca.entity.ai.queue;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Needs;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A villager's action queue, The Sims style. Every second it gathers what the villager wants to
 * do (the current hour of its schedule, its low needs, orders players gave it), scores each by
 * urgency and puts the most urgent at the head; the head drives the brain (see
 * SchedulesMCA#scheduledActivity and the needs tasks). A more urgent action takes over the head
 * only when clearly more urgent, and a started need is seen through until satisfied.
 * <p>
 * The queue is synced to clients as a short string for the interaction screen, and shown as an
 * icon over the villager's head while players are near.
 */
public class ActionQueue {
    public static final float SCHEDULE_SCORE = 0.30F;
    public static final float ORDER_SCORE = 0.55F;
    private static final float NEED_THRESHOLD = 50.0F;
    private static final float SWITCH_MARGIN = 0.15F;
    private static final long ORDER_TICKS = 2400;
    private static final int UPCOMING = 2;

    private static final Map<Needs.Need, Float> WEIGHTS = new EnumMap<>(Map.of(
            Needs.Need.SAFETY, 1.3F,
            Needs.Need.THIRST, 1.1F,
            Needs.Need.HUNGER, 1.0F,
            Needs.Need.ENERGY, 0.9F,
            Needs.Need.SOCIAL, 0.5F,
            Needs.Need.FUN, 0.4F));

    /** The queue as clients see it: "TYPE:score:source[:player]" entries, head first, then "@hour:TYPE" upcoming. */
    private static final CDataParameter<String> SYNC = CParameter.create("ActionQueue", "");

    private final VillagerEntityMCA entity;
    private final List<QueuedAction> orders = new ArrayList<>();
    private boolean ordersLoaded;
    private @Nullable QueuedAction current;

    public ActionQueue(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(SYNC);
    }

    public static boolean isEnabled() {
        return Config.getServerConfig().villagerActionQueue;
    }

    public @Nullable QueuedAction current() {
        return isEnabled() ? current : null;
    }

    public boolean isCurrent(ActionType type) {
        QueuedAction head = current();
        return head != null && head.type() == type;
    }

    /** The brain activity the head of the queue asks for, or null when the queue is off. */
    public @Nullable Activity currentActivity() {
        QueuedAction head = current();
        return head == null ? null : head.type().activity();
    }

    public void addOrder(ActionType type, Player player) {
        loadOrders();
        orders.removeIf(order -> order.type() == type);
        orders.add(new QueuedAction(type, QueuedAction.Source.PLAYER, ORDER_SCORE, player.getUUID(), entity.level().getGameTime() + ORDER_TICKS));
        update();
    }

    /** Cancels an order; players may only cancel their own. */
    public void cancelOrder(ActionType type, Player player) {
        loadOrders();
        if (orders.removeIf(order -> order.type() == type && player.getUUID().equals(order.player()))) {
            update();
        }
    }

    public void tick() {
        if (entity.tickCount % 20 != 7 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isEnabled()) {
            if (current != null || !entity.getTrackedValue(SYNC).isEmpty()) {
                current = null;
                entity.setTrackedValue(SYNC, "");
                ActionBubble.clear(entity);
            }
            return;
        }
        update();
        ActionBubble.update(level, entity, current);
    }

    private void update() {
        loadOrders();
        long time = entity.level().getGameTime();
        Needs needs = entity.getNeeds();
        Activity planned = SchedulesMCA.plannedActivity(entity);

        Map<ActionType, QueuedAction> candidates = new EnumMap<>(ActionType.class);
        offer(candidates, new QueuedAction(ActionType.ofActivity(planned), QueuedAction.Source.SCHEDULE, SCHEDULE_SCORE, null, 0));

        if (needs.hasNeeds()) {
            for (Map.Entry<Needs.Need, Float> weight : WEIGHTS.entrySet()) {
                ActionType type = actionFor(weight.getKey());
                float value = needs.get(weight.getKey());
                boolean started = current != null && current.type() == type && current.source() == QueuedAction.Source.NEED;
                if (value < NEED_THRESHOLD || started && !isSatisfied(type, planned)) {
                    float score = weight.getValue() * Math.max(0.0F, NEED_THRESHOLD - value) / NEED_THRESHOLD;
                    // A need being taken care of is seen through, instead of dropping right at the threshold.
                    if (started) {
                        score = Math.max(score, SCHEDULE_SCORE + 0.05F);
                    }
                    offer(candidates, new QueuedAction(type, QueuedAction.Source.NEED, score, null, 0));
                }
            }
        }

        orders.removeIf(order -> time >= order.until() || isSatisfied(order.type(), planned) && order.type() != ActionType.WORK
                                                         && order.type() != ActionType.LEISURE && order.type() != ActionType.MEET);
        orders.forEach(order -> offer(candidates, order));

        List<QueuedAction> sorted = new ArrayList<>(candidates.values());
        sorted.sort(Comparator.comparingDouble(QueuedAction::score).reversed());

        // Only switch when the new head is clearly more urgent, so villagers do not dither.
        QueuedAction head = sorted.getFirst();
        if (current != null && head.type() != current.type()) {
            QueuedAction stay = candidates.get(current.type());
            if (stay != null && head.score() < stay.score() + SWITCH_MARGIN) {
                head = stay;
            }
        }
        sorted.remove(head);
        sorted.addFirst(head);
        current = head;
        entity.setTrackedValue(SYNC, encode(sorted, upcoming(planned)));
    }

    private static void offer(Map<ActionType, QueuedAction> candidates, QueuedAction action) {
        QueuedAction existing = candidates.get(action.type());
        if (existing == null || action.score() > existing.score()) {
            candidates.put(action.type(), action);
        }
    }

    private static ActionType actionFor(Needs.Need need) {
        return switch (need) {
            case HUNGER -> ActionType.EAT;
            case THIRST -> ActionType.DRINK;
            case ENERGY -> ActionType.SLEEP;
            case SOCIAL -> ActionType.SOCIALIZE;
            case FUN -> ActionType.LEISURE;
            case SAFETY -> ActionType.HIDE;
        };
    }

    /** Whether the reason for an action is gone. */
    private boolean isSatisfied(ActionType type, Activity planned) {
        Needs needs = entity.getNeeds();
        boolean tracked = needs.hasNeeds();
        return switch (type) {
            case EAT -> !tracked || needs.get(Needs.Need.HUNGER) > 80.0F;
            case DRINK -> !tracked || needs.get(Needs.Need.THIRST) > 90.0F;
            case SLEEP -> (!tracked || needs.get(Needs.Need.ENERGY) > 60.0F) && planned != Activity.REST;
            case SOCIALIZE -> !tracked || needs.get(Needs.Need.SOCIAL) > 60.0F;
            case HIDE -> !tracked || needs.get(Needs.Need.SAFETY) > 45.0F;
            case LEISURE -> !tracked || needs.get(Needs.Need.FUN) > 60.0F;
            case GO_HOME -> needs.isAtHome();
            case WORK, MEET -> false;
        };
    }

    /** The next changes of the schedule, as (hour slot, action). */
    private List<int[]> upcoming(Activity planned) {
        List<int[]> result = new ArrayList<>();
        int slot = SchedulesMCA.currentSlot(entity.level());
        ActionType previous = ActionType.ofActivity(planned);
        for (int i = 1; i < SchedulesMCA.SLOTS && result.size() < UPCOMING; i++) {
            ActionType next = ActionType.ofActivity(SchedulesMCA.plannedAt(entity, slot + i));
            if (next != previous) {
                result.add(new int[]{(slot + i) % SchedulesMCA.SLOTS, next.ordinal()});
                previous = next;
            }
        }
        return result;
    }

    // Sync format

    /** An entry as the client sees it. */
    public record Entry(ActionType type, float score, QueuedAction.Source source, @Nullable UUID player) {
    }

    /** A coming change of the schedule: the hour slot (0 = 06:00) and the action. */
    public record Upcoming(int slot, ActionType type) {
    }

    private static String encode(List<QueuedAction> actions, List<int[]> upcoming) {
        List<String> parts = new ArrayList<>();
        for (QueuedAction action : actions) {
            parts.add(String.format(Locale.ROOT, "%s:%.2f:%s%s", action.type().id(), action.score(), action.source().code(),
                    action.player() == null ? "" : ":" + action.player() + ":" + action.until()));
        }
        for (int[] change : upcoming) {
            parts.add("@" + change[0] + ":" + ActionType.values()[change[1]].id());
        }
        return String.join(",", parts);
    }

    /** The queue of a villager (works on clients), head first. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (String part : entity.getTrackedValue(SYNC).split(",")) {
            if (part.isEmpty() || part.startsWith("@")) {
                continue;
            }
            String[] fields = part.split(":");
            ActionType type = ActionType.byId(fields[0]);
            if (type == null || fields.length < 3) {
                continue;
            }
            UUID player = fields.length > 3 ? UUID.fromString(fields[3]) : null;
            entries.add(new Entry(type, Float.parseFloat(fields[1]), QueuedAction.Source.byCode(fields[2]), player));
        }
        return entries;
    }

    /** The coming schedule changes (works on clients). */
    public List<Upcoming> upcoming() {
        List<Upcoming> result = new ArrayList<>();
        for (String part : entity.getTrackedValue(SYNC).split(",")) {
            if (part.startsWith("@")) {
                String[] fields = part.substring(1).split(":");
                ActionType type = ActionType.byId(fields[1]);
                if (type != null) {
                    result.add(new Upcoming(Integer.parseInt(fields[0]), type));
                }
            }
        }
        return result;
    }

    /** Orders survive saving inside the synced string; read them back once after loading. */
    private void loadOrders() {
        if (ordersLoaded) {
            return;
        }
        ordersLoaded = true;
        for (String part : entity.getTrackedValue(SYNC).split(",")) {
            String[] fields = part.split(":");
            if (fields.length >= 5 && "P".equals(fields[2])) {
                ActionType type = ActionType.byId(fields[0]);
                if (type != null) {
                    orders.add(new QueuedAction(type, QueuedAction.Source.PLAYER, ORDER_SCORE, UUID.fromString(fields[3]), Long.parseLong(fields[4])));
                }
            }
        }
    }
}
