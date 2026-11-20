package io.github.jayessdeesea.minecraft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The prediction ledger (backlog/simulator-fidelity.md; operator, 2026-11-20: tell us when the simulation and the game
 * part): what each kind of act was planned to take against what it took, summed over a spell and logged as
 * {@code [Ledger]} - the same lines live and in the squad simulation, so the two rates can be set side by side. Its
 * first kind is the route: the steps a soldier's first route to a goal planned, and the ticks it took to stand there.
 * Thread-safe: every soldier's tick writes, and the spell's line goes out from whichever comes after it ends.
 */
final class Ledger {

    private static final Logger LOG = LogManager.getLogger(Ledger.class);

    /** How long a spell is summed before its line goes out. */
    static final long SPELL_MILLIS = 10 * 60_000;

    /** A spell's acts of one kind: each its planned measure and what it took. */
    private final Map<String, List<double[]>> acts = new TreeMap<>();
    private long spellFrom = Long.MIN_VALUE;

    /** A route walked: {@code planned} steps, {@code ticks} to stand at its goal, at {@code now}. */
    synchronized void route(int planned, int ticks, long now) {
        record("route", planned, ticks, now);
    }

    /** One act of {@code kind}, planned at {@code planned}, taking {@code took}, at {@code now}. */
    synchronized void record(String kind, double planned, double took, long now) {
        if (spellFrom == Long.MIN_VALUE) {
            spellFrom = now;
        }
        if (now - spellFrom >= SPELL_MILLIS) {
            summary().forEach(line -> LOG.info("[Ledger] {}", line));
            acts.clear();
            spellFrom = now;
        }
        acts.computeIfAbsent(kind, k -> new ArrayList<>()).add(new double[]{planned, took});
    }

    /**
     * The spell so far, a line a kind: how many, what they took for each planned unit, the median and the spread
     * between the quarter and three-quarter marks - a route's ticks a step planned.
     */
    synchronized List<String> summary() {
        List<String> lines = new ArrayList<>();
        acts.forEach((kind, list) -> rates(list).ifPresent(r -> lines.add(String.format(Locale.ROOT,
                "%s: %d, %.2f a unit planned (median; quartiles %.2f-%.2f), %.0f planned, %.0f taken",
                kind, list.size(), r[1], r[0], r[2], list.stream().mapToDouble(a -> a[0]).sum(), list.stream().mapToDouble(a -> a[1]).sum()))));
        return lines;
    }

    /** The quarter, median and three-quarter marks of what each act took a unit planned, if any was planned. */
    static Optional<double[]> rates(List<double[]> acts) {
        double[] rates = acts.stream().filter(a -> a[0] > 0).mapToDouble(a -> a[1] / a[0]).sorted().toArray();
        if (rates.length == 0) {
            return Optional.empty();
        }
        return Optional.of(new double[]{rates[rates.length / 4], rates[rates.length / 2], rates[rates.length * 3 / 4]});
    }
}
