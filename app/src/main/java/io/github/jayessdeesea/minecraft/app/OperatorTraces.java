package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * The operator's orders being followed down the chain, each an {@link OrderTrace} dropped once carried out or
 * superseded. Kept apart from the chain of command so the chain's own decisions and this bookkeeping change
 * separately; what a hop has to say goes out through {@code traced} (the chain logs it and puts it on the radio),
 * and the time is read from {@code clock} only where a trace is opened, never held. The list is copy-on-write
 * because the operator's chat, the sitrep tick and the platoon leader's thread each touch it.
 */
final class OperatorTraces {

    private final List<OrderTrace> traces = new CopyOnWriteArrayList<>();
    private final AtomicInteger traceIds = new AtomicInteger();
    private final LongSupplier clock;
    private final BiConsumer<String, String> traced;

    /** @param traced called with the operator an order came from and one line of its trace */
    OperatorTraces(LongSupplier clock, BiConsumer<String, String> traced) {
        this.clock = clock;
        this.traced = traced;
    }

    /** A trace for the operator's new order: the ones before it, not yet carried out, are superseded. */
    OrderTrace open(String operator, String order, int version) {
        traces.forEach(t -> t.supersede().ifPresent(line -> traced.accept(operator, line)));
        traces.removeIf(OrderTrace::closed);
        OrderTrace trace = new OrderTrace("OP-" + traceIds.incrementAndGet(), operator, order, clock.getAsLong(), version);
        traces.add(trace);
        return trace;
    }

    /** Every present squad's SITREP at {@code now}: {@code lastFrago} says when a squad's leader last gave it an order. */
    void sitreps(List<ChainOfCommand.Sitrep> sitreps, ToLongFunction<String> lastFrago, long now) {
        for (OrderTrace trace : traces) {
            for (ChainOfCommand.Sitrep sitrep : sitreps) {
                trace.sitrep(sitrep.squad(), lastFrago.applyAsLong(sitrep.squad()), sitrep.soldiers(), now)
                        .forEach(line -> traced.accept(trace.operator, line));
            }
        }
        traces.removeIf(OrderTrace::closed);
    }

    /** The platoon leader's decision on the order of {@code version}, at {@code decidedAt}. */
    void decided(int version, Map<String, Optional<Objective>> decided, long decidedAt) {
        traces.stream().filter(t -> t.version == version)
                .forEach(t -> t.decided(decided, decidedAt).forEach(line -> traced.accept(t.operator, line)));
    }
}
