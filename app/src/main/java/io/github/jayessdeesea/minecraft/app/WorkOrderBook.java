package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.command.WorkOrder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each producer's work order from its squad leader (command.WorkOrder): one value a soldier has been told, in
 * place of a map per duty. The squad loop writes it, each order said when it changes - the squad leader's view
 * ("[Alpha -> Alice] WORK: harvester over x 6..8, z -4..4, the dump at (3, 64, 2)") - and the tick reads it.
 * Thread-safe.
 */
final class WorkOrderBook {

    private static final Logger LOG = LogManager.getLogger(WorkOrderBook.class);

    private final Map<String, WorkOrder> orders = new ConcurrentHashMap<>();

    /** {@code squad}'s {@code soldiers}' orders this cycle: {@code given}'s for its producers, none for the rest. */
    void assign(String squad, List<String> soldiers, Map<String, WorkOrder> given) {
        for (String soldier : soldiers) {
            WorkOrder now = given.get(soldier);
            WorkOrder was = now == null ? orders.remove(soldier) : orders.put(soldier, now);
            if (now != null && !now.equals(was)) {
                LOG.info("[{} -> {}] WORK: {}", squad, soldier, now.line());
            }
        }
    }

    /** {@code soldier}'s work order, if it's a producer. */
    Optional<WorkOrder> of(String soldier) {
        return Optional.ofNullable(orders.get(soldier));
    }

    /** Whether {@code soldier}'s order is {@code duty}. */
    boolean is(String soldier, WorkOrder.Duty duty) {
        return of(soldier).filter(o -> o.duty() == duty).isPresent();
    }

    /**
     * {@code soldier}'s field of {@code farm}: the plot's cells in its ground for a harvester, none for the baker - its
     * work is at the dump - and the whole for a farmer with no farm order.
     */
    FarmPlan field(String soldier, FarmPlan farm) {
        WorkOrder order = orders.get(soldier);
        if (farm == null || order == null || !order.farm()) {
            return farm;
        }
        return order.duty() == WorkOrder.Duty.BAKER ? null : new FarmPlan(farm.water(), farm.plot().stream()
                .filter(c -> order.ground().filter(g -> g.contains(c.x(), c.y(), c.z())).isPresent()).toList());
    }
}
