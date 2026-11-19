package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import io.github.jayessdeesea.minecraft.command.FarmDuties;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ShareWatch;
import io.github.jayessdeesea.minecraft.command.WoodDuties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Each producer's duty order from its squad leader (command.DutyOrder): one value a soldier has been told, in
 * place of a map per duty. The squad loop writes it, each order said when it changes - the squad leader's view
 * ("[Alpha -> Alice] DUTY: harvester over x 6..8, z -4..4, the dump at (3, 64, 2)") - and the tick reads it.
 * Thread-safe.
 */
final class DutyBook {

    private static final Logger LOG = LogManager.getLogger(DutyBook.class);

    /** What each duty makes or spends: any change in it is the share being worked. */
    static final Map<DutyOrder.Duty, Set<ItemClass>> PRODUCTS = Map.of(
            DutyOrder.Duty.HARVESTER, Set.of(ItemClass.WHEAT, ItemClass.SEEDS), DutyOrder.Duty.BAKER, Set.of(ItemClass.WHEAT, ItemClass.FOOD),
            DutyOrder.Duty.WOODCUTTER, Set.of(ItemClass.LOG), DutyOrder.Duty.PLANTER, Set.of(ItemClass.SAPLING));

    private final Map<String, DutyOrder> orders = new ConcurrentHashMap<>();
    private final ShareWatch watch = new ShareWatch();
    /** Each squad's strips found cut out, and the objective they were found on. */
    private final Map<String, List<Box>> cutOut = new HashMap<>();
    private final Map<String, Optional<Objective>> cutOutFor = new HashMap<>();
    /** When each producer was given the ground it works. */
    private final Map<String, Long> givenAt = new ConcurrentHashMap<>();

    /**
     * {@code squad}'s {@code soldiers}' orders this cycle at {@code now}: {@code given}'s for its producers, none for the
     * rest - and, the squad on {@code production} work, the alarm on one holding without a share.
     */
    void assign(String squad, List<String> soldiers, Map<String, DutyOrder> given, Optional<String> production, long now) {
        for (String soldier : soldiers) {
            DutyOrder order = given.get(soldier);
            DutyOrder was = order == null ? orders.remove(soldier) : orders.put(soldier, order);
            if (order != null && !order.equals(was)) {
                LOG.info("[{} -> {}] DUTY: {}", squad, soldier, order.line());
            }
            if (order != null && (was == null || !order.ground().equals(was.ground()))) {
                givenAt.put(soldier, now);
            }
            if (order != null) {
                watch.shared(soldier);
            } else if (production.isPresent()) {
                watch.withoutShare(squad, soldier, production.get(), now).ifPresent(LOG::warn);
            } else {
                watch.off(soldier);
            }
        }
    }

    /**
     * {@code soldier}'s share sampled at {@code now}: what its duty makes or spends, as {@code count} has it. One
     * {@code paused} - in contact, doing what it should, or with nothing in its share to do (a plot growing, no wheat
     * in the stores to bake) - isn't behind: its clock starts again. The alarm when it falls behind (command.ShareWatch).
     */
    void sample(String squad, String soldier, ToIntFunction<ItemClass> count, boolean paused, long now) {
        DutyOrder order = orders.get(soldier);
        if (order != null) {
            int product = PRODUCTS.get(order.duty()).stream().mapToInt(count).sum();
            watch.sample(squad, order, paused ? Integer.MIN_VALUE : product, now).filter(l -> !paused).ifPresent(LOG::warn);
        }
    }

    /**
     * Of {@code soldiers} on producers' duties, how many are behind their shares, for the squad's progress - the
     * platoon leader's view - and the platoon leader's alarm when half or more are (command.ShareWatch).
     */
    Optional<String> behind(String squad, List<String> soldiers, String task) {
        List<String> producers = soldiers.stream().filter(orders::containsKey).toList();
        long behind = watch.behind(producers);
        watch.squadBehind(squad, task, behind, producers.size()).ifPresent(l -> {
            if (l.startsWith("[Alarm]")) {
                LOG.warn(l);
            } else {
                LOG.info(l);
            }
        });
        return behind == 0 ? Optional.empty() : Optional.of(behind + " of " + producers.size() + " producers behind their shares");
    }

    /**
     * The duty orders of {@code soldiers}, their yield to {@code dump}: the wood's {@code woodDuties}, and on the farm -
     * those {@code farming} - the shares of its plot among everyone farming it, whatever their squad: one set of shares
     * and one baker to a farm. Live (field473), Alpha's and Delta's first harvesters were both given x -31..-20 of the
     * camp's farm, each squad sharing the plot among its own.
     */
    static Map<String, DutyOrder> given(List<String> soldiers, Map<String, WoodDuties.Duty> woodDuties, Map<String, FarmPlan> farming,
                                        Optional<Position> dump) {
        Map<String, DutyOrder> given = new HashMap<>(WoodDuties.orders(woodDuties, dump));
        soldiers.stream().filter(farming::containsKey).findFirst().map(farming::get).ifPresent(f -> {
            List<String> onTheFarm = farming.entrySet().stream().filter(e -> e.getValue().water().equals(f.water()))
                    .map(Map.Entry::getKey).sorted().toList();
            given.putAll(FarmDuties.orders(onTheFarm, f.plot().stream().map(c -> new Position(c.x(), c.y(), c.z())).toList(), dump));
        });
        return given;
    }

    /**
     * The strips {@code squad}'s woodcutters have found cut out on its {@code objective}: each woodcutter's ground added
     * once its gather found nothing in it - {@code foundNothingAt} - after it was given that ground, said once;
     * forgotten when the objective moves. Live in the squad simulation, a gather that found nothing in the old strip
     * marked the new one cut out the moment it was given.
     */
    synchronized List<Box> cutOut(String squad, Optional<Objective> objective, ToLongFunction<String> foundNothingAt) {
        if (!objective.equals(cutOutFor.get(squad))) {
            cutOutFor.put(squad, objective);
            cutOut.put(squad, new ArrayList<>());
        }
        List<Box> found = cutOut.get(squad);
        orders.values().stream().filter(o -> o.duty() == DutyOrder.Duty.WOODCUTTER
                        && foundNothingAt.applyAsLong(o.soldier()) > givenAt.getOrDefault(o.soldier(), Long.MAX_VALUE))
                .flatMap(o -> o.ground().stream()).filter(g -> !found.contains(g)).toList().forEach(g -> {
                    found.add(g);
                    LOG.info("[{}] the strip over x {}..{}, z {}..{} is cut out - the wood shared again without it", squad, g.minX(), g.maxX(),
                            g.minZ(), g.maxZ());
                });
        return List.copyOf(found);
    }

    /** {@code soldier}'s duty order, if it's a producer. */
    Optional<DutyOrder> of(String soldier) {
        return Optional.ofNullable(orders.get(soldier));
    }

    /** Whether {@code soldier}'s order is {@code duty}. */
    boolean is(String soldier, DutyOrder.Duty duty) {
        return of(soldier).filter(o -> o.duty() == duty).isPresent();
    }

    /**
     * {@code soldier}'s field of {@code farm}: the plot's cells in its ground for a harvester, none for the baker - its
     * work is at the dump - and the whole for a farmer with no farm order.
     */
    FarmPlan field(String soldier, FarmPlan farm) {
        DutyOrder order = orders.get(soldier);
        if (farm == null || order == null || !order.farm()) {
            return farm;
        }
        return order.duty() == DutyOrder.Duty.BAKER ? null : new FarmPlan(farm.water(), farm.plot().stream()
                .filter(c -> order.ground().filter(g -> g.contains(c.x(), c.y(), c.z())).isPresent()).toList());
    }
}
