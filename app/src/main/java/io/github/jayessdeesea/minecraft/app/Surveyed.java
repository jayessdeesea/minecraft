package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A decision's input that costs a search - the terrain round a squad's point man, a flood of the ground it can
 * reach - kept on the survey's own clock, not the decision's (docs/architecture.md, the latency architecture). A
 * rule decides every SITREP and waits on nothing, so it reads the last survey taken for its objective; a fresh one
 * is taken on the echelon's own thread, off the squad loop's, when the last try is {@link #PERIOD_MILLIS} old, the
 * squad's point has moved {@link #MOVED_BLOCKS} from where it was taken, or the objective is another. This is the
 * ground's clock, not a wait built for a model: the ground a squad stands on changes slowly, and the search is the
 * costliest thing a decision asks for. A survey that fails is tried again on the same clock, not every SITREP.
 */
final class Surveyed<T> {

    /** How old a survey gets before another is taken with nothing else changed: a decision's ground is this stale at most. */
    static final long PERIOD_MILLIS = 20_000;
    /**
     * How far the point moves, across the ground, before another is taken: half the terrain survey's radius, so the
     * ground toward the objective is always within the one in hand.
     */
    static final double MOVED_BLOCKS = 16;

    /** A survey in hand, and what it was taken about - the objective. */
    private record Taken<T>(T value, Object about) {
    }

    /**
     * The last try at one, taken or failed: when, where the point stood when it was asked for, where the survey says
     * it was taken from - the same till it's in - and about what.
     */
    private record Tried(long at, Position asked, Position from, Object about) {
    }

    /** Where a survey says it was taken from: the move is measured from there, not from where it was asked for. */
    private final Function<T, Position> takenFrom;
    private volatile Taken<T> last;
    private volatile Tried tried;
    /** A survey is being taken: one at a time, on the echelon's thread. */
    private final AtomicBoolean taking = new AtomicBoolean();

    Surveyed(Function<T, Position> takenFrom) {
        this.takenFrom = takenFrom;
    }

    /**
     * The last survey taken, if it was taken about {@code about}: none before the first is in, and none under a new
     * objective till one is taken for it - an order from the last objective's ground is given twice, or wrongly.
     */
    Optional<T> latest(Object about) {
        Taken<T> taken = last;
        return taken != null && taken.about().equals(about) ? Optional.of(taken.value()) : Optional.empty();
    }

    /** Whether another is due, the point at {@code from} and the survey {@code about} this, at {@code now}. */
    boolean due(Position from, Object about, long now) {
        Tried before = tried;
        return before == null || now - before.at() >= PERIOD_MILLIS || !before.about().equals(about)
                // Moved from both: the survey's own point and the one asked from can be two soldiers, and a squad
                // spread that far would otherwise be surveyed again as soon as each survey was in.
                || Cohesion.distance(before.from(), from) >= MOVED_BLOCKS && Cohesion.distance(before.asked(), from) >= MOVED_BLOCKS;
    }

    /**
     * Has {@code thread} take a {@code fresh} survey if one is {@link #due} and none is being taken; the decisions
     * made meanwhile read the last. One that fails is {@code faults}' - logged and counted - and the last stands.
     */
    void keep(String what, Supplier<T> fresh, Position from, Object about, long now, Executor thread, Faults faults) {
        if (!due(from, about, now) || !taking.compareAndSet(false, true)) {
            return;
        }
        tried = new Tried(now, from, from, about);
        thread.execute(faults.guard(what, () -> {
            try {
                T value = fresh.get();
                tried = new Tried(now, from, takenFrom.apply(value), about);
                last = new Taken<>(value, about);
            } finally {
                taking.set(false);
            }
        }));
    }
}
