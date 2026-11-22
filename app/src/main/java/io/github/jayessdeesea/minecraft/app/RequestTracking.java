package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The requests raised and settled from each cycle's SITREPs: each soldier's CANTCO to its squad leader
 * ({@link SoldierRequests}), each squad's finished or stalled task and its Class I RED to the platoon leader - which is
 * woken for them. Overdue ones alarm. The chain of command's, kept apart from it.
 */
final class RequestTracking {

    private static final Logger LOG = LogManager.getLogger(ChainOfCommand.class);

    /** Each soldier's CANTCO to its squad leader. */
    private final SoldierRequests soldierRequests = new SoldierRequests();

    /**
     * {@code sitreps}' requests into {@code requests} at {@code now}, the platoon leader woken by {@code wake} - a cause,
     * and whether at once - for each the squads raise.
     */
    void track(List<ChainOfCommand.Sitrep> sitreps, RequestLedger requests, BiConsumer<String, Boolean> wake, long now) {
        for (ChainOfCommand.Sitrep sitrep : sitreps) {
            soldierRequests.track(sitrep, requests, now);
            String task = sitrep.squad() + ":task";
            Optional<Progress> finished = sitrep.progress()
                    .filter(p -> p.state() != Progress.State.UNDER_WAY);
            if (finished.isPresent()) {
                requests.raise(task, RequestLedger.Echelon.PLATOON_LEADER, sitrep.squad(), "task " + finished.get().state() + ": " + finished.get().detail(), now)
                        .ifPresent(line -> {
                            LOG.info(line);
                            wake.accept(sitrep.squad() + "'s task " + finished.get().state(), false);
                        });
            } else {
                requests.settle(task);
            }
            String food = sitrep.squad() + ":food";
            Optional<Subsistence.Status> red = sitrep.classOne()
                    .filter(c -> c.rating() == Subsistence.Rating.RED);
            if (red.isPresent()) {
                requests.raise(food, RequestLedger.Echelon.PLATOON_LEADER, sitrep.squad(), red.get().line(), now).ifPresent(line -> {
                    LOG.info(line);
                    wake.accept(sitrep.squad() + "'s Class I RED", false);
                });
            } else {
                requests.settle(food);
            }
        }
        requests.alarms(now).forEach(LOG::warn);
    }
}
