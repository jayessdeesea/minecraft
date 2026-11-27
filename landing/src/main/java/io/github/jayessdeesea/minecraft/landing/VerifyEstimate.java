package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A running verify's percent done and time left, against the kept passes (design 3b; operator's decisions 9 and 11),
 * computed by the integrator at each look and carried in the progress file. The percent is of expected time, weighted
 * by where the time goes: f = the sum over the modules the history knows of each one's expected wall times its part -
 * 1 finished, 0 not begun, the running one its expected class time done over its expected class time - over the sum
 * of those walls, what the weights add to; the time left stretches the expected total. A class or module the history does not know carries no weight; a known class that never comes is
 * closed by its module finishing.
 *
 * @param percent  floor(100 f), at most 99 while it runs
 * @param left     (1 - f)(elapsed + (1 - f) total): the expected rest, stretched by how far it has fallen behind
 * @param over     elapsed past the expected total, when it is
 * @param expected the expected total
 * @param passes   the passes it is measured against
 * @param classes  the test classes expected
 */
public record VerifyEstimate(int percent, Duration left, Optional<Duration> over, Duration expected, int passes, int classes) {

    /** Over this times expected, a verify's end adds an [Overrun] line (operator's decision 10). */
    static final double OVERRUN = 1.25;
    /** What stands for "no history" in every line that would carry an estimate. */
    public static final String NONE = "(no estimate: no verify has passed yet)";

    public static Optional<VerifyEstimate> of(Optional<Expected> expected, MavenLog log, Duration elapsed) {
        if (expected.isEmpty()) {
            return Optional.empty();
        }
        Expected e = expected.get();
        double weighed = 0;
        long walls = 0;
        for (VerifyPass.Module m : e.modules()) {
            weighed += m.wall().toMillis() * part(m, log);
            walls += m.wall().toMillis();
        }
        // the divisor is what the weights sum to: all modules finished is all done, whatever else the whole time held
        long total = e.total().toMillis();
        double f = walls == 0 ? 0 : Math.min(1, weighed / walls);
        int percent = Math.min(99, (int) Math.floor(100 * f + 1e-9));
        double rest = 1 - f;
        Duration left = Duration.ofMillis(Math.round(rest * (elapsed.toMillis() + rest * total)));
        Duration over = elapsed.minus(e.total());
        return Optional.of(new VerifyEstimate(percent, left, over.isNegative() || over.isZero() ? Optional.empty()
                : Optional.of(over), e.total(), e.passes(), e.classes()));
    }

    /** A module's part done: finished 1, not begun 0, running its expected class time done over all of it. */
    private static double part(VerifyPass.Module m, MavenLog log) {
        if (log.finished(m.name())) {
            return 1;
        }
        if (!log.modules().contains(m.name())) {
            return 0;
        }
        long all = m.classes().values().stream().mapToLong(Duration::toMillis).sum();
        long done = m.classes().entrySet().stream().filter(c -> log.done().contains(new MavenLog.Done(m.name(), c.getKey())))
                .mapToLong(c -> c.getValue().toMillis()).sum();
        return all == 0 ? 0 : (double) done / all;
    }

    /** "68%, about 11m left", and ", past the 34m estimate by 6m" once it is. */
    public String words() {
        return percent + "%, about " + Span.about(left) + " left"
                + over.map(o -> ", past the " + Span.about(expected) + " estimate by " + Span.about(o)).orElse("");
    }

    /**
     * The lines a verify ends with on the task's log: its time against expected and its longest quiet - the stall
     * limit's expected-against-max - and, over 1.25 x expected, an [Overrun] naming each module over 1.25 x its own
     * expected wall, from this verify's Reactor Summary when it wrote one. It ends nothing.
     */
    public static List<String> ended(Optional<Expected> expected, Sha candidate, Duration took, Duration longestQuiet,
                                     Optional<VerifyPass> thisOne) {
        List<String> lines = new ArrayList<>();
        lines.add("verify took " + Span.of(took) + expected.map(e -> " against " + Span.of(e.total()) + " expected ("
                + e.passes() + (e.passes() == 1 ? " pass)" : " passes)")).orElse(" " + NONE) + ", longest quiet "
                + Span.of(longestQuiet));
        expected.filter(e -> over(took, e.total())).ifPresent(e -> {
            String modules = thisOne.map(p -> p.modules().stream()
                            .filter(m -> e.module(m.name()).filter(x -> over(m.wall(), x.wall())).isPresent())
                            .map(m -> m.name() + " " + Span.of(m.wall()) + " against " + Span.of(e.module(m.name())
                                    .orElseThrow().wall())).toList())
                    .map(l -> l.isEmpty() ? "no module over its own" : String.join(", ", l))
                    .orElse("no Reactor Summary to name its modules");
            lines.add("[Overrun] the verify of " + candidate + " took " + Span.of(took) + ", over 1.25 x the "
                    + Span.of(e.total()) + " expected: " + modules);
        });
        return lines;
    }

    private static boolean over(Duration took, Duration expected) {
        return took.toMillis() > OVERRUN * expected.toMillis();
    }
}
