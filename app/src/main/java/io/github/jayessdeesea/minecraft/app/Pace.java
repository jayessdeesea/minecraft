package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DecidesByRules;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadOrder;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * How an echelon's leader is paced, by what decides for it (docs/architecture.md, the latency architecture). A
 * model's answer takes seconds and is paid for, so it's asked on a slow clock, not again within a gap, and through
 * the queue every model call is timed in ({@link ModelCalls}). A standing rule answers in microseconds: it's asked
 * whenever there's something to decide on - a report up, an order down, each SITREP - where it's asked, with nothing
 * to wait behind. Built once for each leader ({@link #of}); every wait in the chain of command is read from here,
 * and everything that differs between a rule's decision and a model's is decided here, not where the decision is made.
 * What a decision reads that costs a search isn't one of these waits: it has its own clock ({@link Surveyed}).
 *
 * @param periodMillis    the squad leader's routine look: the longest it goes without deciding while it has an
 *                        objective. Zero is every SITREP - the squad loop's own period, with no second clock to
 *                        drift against it.
 * @param reportGapMillis the least time after a squad leader's decision before a soldier's new CANTCO or contact
 *                        has it decide again, however many arrive: a stuck soldier isn't to spend a model call
 *                        every SITREP.
 * @param wakeGapMillis   the least time after the platoon leader's decision before a request - a squad's task
 *                        finished or stalled, its Class I RED, its squad leader's CANTCO - wakes it again. What
 *                        wakes it at once (an operator order, dawn, dusk, a squad with no objective) doesn't wait.
 * @param queued          whether a decision runs on the echelon's own thread, timed and counted with the model
 *                        calls in flight and shed when routine and they're backed up; otherwise it runs where it's
 *                        asked for, and the order it gives stands before the SITREP that asked is done with -
 *                        but for one that searches as it decides ({@link Deciding#onItsThread}).
 */
record Pace(long periodMillis, long reportGapMillis, long wakeGapMillis, boolean queued) {

    /**
     * A model's: a squad leader's look every 20 seconds, 8 seconds between its decisions on reports, and a minute
     * between the platoon leader's on requests - live, woken by every request, a model was asked every ten seconds
     * at night to rewrite the same order.
     */
    static final Pace MODEL = new Pace(20_000, 8_000, 60_000, true);

    /** A standing rule's: no wait anywhere (operator, 2026-11-24). */
    static final Pace RULES = new Pace(0, 0, 0, false);

    static Pace of(SquadLeader leader) {
        return by(leader);
    }

    static Pace of(PlatoonLeader leader) {
        return by(leader);
    }

    /** The one place a decider's kind becomes its pace. */
    private static Pace by(Object leader) {
        return leader instanceof DecidesByRules ? RULES : MODEL;
    }

    /**
     * Whether a soldier's contact is a new contact report by the squad's contact having settled since its last
     * ({@code command.SquadContact}): a rule's, asked every SITREP, or one flickering each second is a report each time.
     * A model's gap between decisions does that for it.
     */
    boolean settlesContact() {
        return !queued;
    }

    /** Whether {@code who}'s routine decision is passed over this cycle: only a queued one, with the models backed up. */
    boolean sheds(ModelCalls calls, String who) {
        return queued && calls.shed(who);
    }

    /**
     * Whether {@code decided} is the {@code standing} order over again, to be neither said, logged nor given anew. A
     * rule asked every SITREP gives the same order most of them, and each would be a line on the net and in the log,
     * and an order the squad loop takes for a new one. Never a model's: each of its decisions is said, as it was
     * asked for seconds apart. Nor the first under a new objective, which is the squad leader's answer to it.
     */
    boolean stands(SquadOrder decided, SquadOrder standing, boolean answersObjective) {
        return !queued && !answersObjective && decided.equals(standing);
    }

    /**
     * The survey a decision at this pace reads. A model's decision takes its own {@code fresh} one as it decides, on
     * its own thread, as often as it decides. A rule decides every SITREP, so its survey is {@code kept} on the
     * survey's own clock ({@link Surveyed}) and taken on {@code thread}: the last one taken about {@code about} is
     * read - none while the first, or the first about a new objective, is still being taken, and that SITREP's
     * decision is left to the next.
     */
    <T> Optional<Supplier<T>> survey(Surveyed<T> kept, String what, Supplier<T> fresh, Position from, Object about, long now,
                                     Executor thread, Faults faults) {
        if (queued) {
            return Optional.of(fresh);
        }
        kept.keep(what, fresh, from, about, now, thread, faults);
        return kept.latest(about).map(value -> () -> value);
    }

    /** {@code who}'s decision, about to be made at this pace: a model's is in the model calls' queue from here on. */
    Deciding deciding(String who, ModelCalls calls, Faults faults) {
        return new Deciding(who, queued ? calls.queued(who) : null, faults);
    }

    /**
     * One decision's passage, as its pace has it. The decision itself brackets its work with {@link #started},
     * {@link #failed} and {@link #finished}, and what each does is the pace's: a model's is timed through its
     * {@link ModelCalls.Ticket} and each failure logged as the caller logs it; a rule's fault is its echelon's
     * {@link Faults}' - with its stack the first time, counted after - since a rule that throws does it every SITREP.
     */
    static final class Deciding {
        private final String what;
        /** The model call's ticket - none for a rule's decision. */
        private final ModelCalls.Ticket ticket;
        private final Faults faults;
        private boolean failed;

        private Deciding(String who, ModelCalls.Ticket ticket, Faults faults) {
            this.what = who + "'s decision";
            this.ticket = ticket;
            this.faults = faults;
        }

        /** Runs {@code decision}: a model's on {@code thread}; a rule's here and now, on the caller's. */
        void run(Executor thread, Runnable decision) {
            if (ticket == null) {
                decision.run();
            } else {
                thread.execute(decision);
            }
        }

        /**
         * Runs a {@code decision} that takes a search of its own each time it's made - the platoon leader's, of what
         * each squad sees to gather - on {@code thread} whatever decides it, so the search never runs on the squad
         * loop's: a rule's starts at once, and its orders are there for the next SITREP.
         */
        void onItsThread(Executor thread, Runnable decision) {
            thread.execute(decision);
        }

        void started() {
            if (ticket != null) {
                ticket.started();
            }
        }

        /** It threw {@code e}: a model's is logged by {@code said}, as each is; a rule's goes to its faults. */
        void failed(RuntimeException e, Runnable said) {
            failed = true;
            if (ticket != null) {
                said.run();
            } else {
                faults.failed(what, e);
            }
        }

        void finished() {
            if (ticket != null) {
                ticket.finished();
            } else if (!failed) {
                faults.ran(what);
            }
        }
    }
}
