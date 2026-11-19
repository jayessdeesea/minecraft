package io.github.jayessdeesea.minecraft.app;

import static io.github.jayessdeesea.minecraft.app.DayWorkRules.answers;

import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each soldier's requests to its squad leader, raised and settled from the SITREPs into the chain's
 * {@link RequestLedger} (docs/military-doctrine.md § 4): a CANTCO is answered by a different order. Thread-safe.
 */
final class SoldierRequests {

    private static final Logger LOG = LogManager.getLogger(SoldierRequests.class);

    /** The order each soldier held when its CANTCO went up: a different one is the squad leader's answer. */
    private final Map<String, Order> cantcoOrders = new ConcurrentHashMap<>();

    /** {@code sitrep}'s soldiers' CANTCOs raised, answered or settled in {@code requests} at {@code now}. */
    void track(ChainOfCommand.Sitrep sitrep, RequestLedger requests, long now) {
        for (SoldierStatus soldier : sitrep.soldiers()) {
            String key = "soldier:" + soldier.name();
            if (soldier.report() instanceof Report.Cantco(String reason)) {
                Order held = cantcoOrders.putIfAbsent(soldier.name(), soldier.order());
                if (held != null && answers(held, soldier.order())) {
                    requests.answer(key, now).ifPresent(LOG::info);
                    cantcoOrders.put(soldier.name(), soldier.order());
                }
                requests.raise(key, RequestLedger.Echelon.SQUAD_LEADER, soldier.name() + " of " + sitrep.squad(), "CANTCO: " + reason, now)
                        .ifPresent(LOG::info);
            } else if (cantcoOrders.remove(soldier.name()) instanceof Order held) {
                if (!answers(held, soldier.order())) {
                    requests.settle(key);
                } else {
                    requests.answer(key, now).ifPresent(LOG::info);
                }
            }
        }
    }
}
