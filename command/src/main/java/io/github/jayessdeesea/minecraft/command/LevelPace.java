package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How fast a level of the keep came down against the fastest it could have (operator, 2026-11-14:
 * predict the theoretical maximum from the route, the tool's break time and the travel; below 90%,
 * dig in and find out why; 2026-11-15: enough telemetry to tell whether we're within it, and where
 * the rest went, so we can refactor). Each builder's {@link Tally} adds up its digs: the break time
 * vanilla gives the block with the best tool the builder carried, and the walk between the stands its
 * route took at walking speed - its best - against the ticks each part of the dig actually took.
 *
 * <p>The level's best is its work shared out evenly over its crew - the theoretical maximum: a
 * split that gave one builder more is a loss like any other, and shows as the gap to {@link
 * #splitMillis()}, the slowest builder's own best. What every builder's ticks went on over the level
 * - each routine holding its hands, or waiting with none - is {@code spent}, so the gap is accounted for.
 *
 * @param level   the level's height
 * @param millis  how long it took, from its plan to its fall
 * @param tallies each builder's digs on it
 * @param crew    the builders it was shared out among
 * @param spent   each builder's ticks over the level, by what held them
 */
public record LevelPace(int level, long millis, Map<String, Tally> tallies, int crew, Map<String, Map<String, Integer>> spent) {

    /** Below this share of its best, a level is looked into. */
    public static final double LOOK_INTO = 0.9;
    /** Milliseconds a server tick takes. */
    public static final int TICK_MILLIS = 50;

    public LevelPace {
        tallies = Map.copyOf(tallies);
        spent = spent.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
    }

    /** A level with its digs only: shared among the builders who dug it, nothing known of their other ticks. */
    public LevelPace(int level, long millis, Map<String, Tally> tallies) {
        this(level, millis, tallies, tallies.size(), Map.of());
    }

    /**
     * One builder's digs: how many, the ticks their breaks and walks could have taken at best, and
     * the ticks each part took - walking to the stand, taking the tool in hand, breaking, the
     * server's word the block is gone, and picking up its drop.
     */
    public record Tally(int digs, int bestBreak, int bestWalk, int walk, int hand, int breaking, int ack, int pickup) {

        public static final Tally NONE = new Tally(0, 0, 0, 0, 0, 0, 0, 0);

        public Tally plus(Tally o) {
            return new Tally(digs + o.digs, bestBreak + o.bestBreak, bestWalk + o.bestWalk, walk + o.walk, hand + o.hand,
                    breaking + o.breaking, ack + o.ack, pickup + o.pickup);
        }

        /** The ticks its digs could have taken at best. */
        public int best() {
            return bestBreak + bestWalk;
        }

        /** The ticks its digs took. */
        public int took() {
            return walk + hand + breaking + ack + pickup;
        }
    }

    /** The fastest the level could have come down: all its digs' best, shared evenly over its crew. */
    public long bestMillis() {
        long best = tallies.values().stream().mapToLong(t -> (long) t.best() * TICK_MILLIS).sum();
        return best / Math.max(1, Math.max(crew, tallies.size()));
    }

    /** The fastest it could have come down as it was split: its slowest builder's best. */
    public long splitMillis() {
        return tallies.values().stream().mapToLong(t -> (long) t.best() * TICK_MILLIS).max().orElse(0);
    }

    /** Its best over what it took: 1 is as fast as it could go. */
    public double share() {
        return millis <= 0 ? 1 : (double) bestMillis() / millis;
    }

    /** Whether it's slow enough to look into. */
    public boolean slow() {
        return share() < LOOK_INTO;
    }

    /** Every builder's ticks over the level, by what held them, added up: where the level's time went. */
    public Map<String, Integer> spentInAll() {
        Map<String, Integer> all = new HashMap<>();
        spent.values().forEach(m -> m.forEach((what, ticks) -> all.merge(what, ticks, Integer::sum)));
        return Map.copyOf(all);
    }

    /**
     * The level's line: what it took against its best and against its split's, where the crew's
     * ticks went, and each builder's digs - where its ticks went against its best, and the time it
     * wasn't digging at all.
     */
    public String summary() {
        long levelTicks = millis / TICK_MILLIS;
        String builders = tallies.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(e -> {
                    Tally t = e.getValue();
                    return e.getKey() + " " + t.digs() + " digs: break " + t.breaking() + "/" + t.bestBreak()
                            + ", walk " + t.walk() + "/" + t.bestWalk() + ", hand " + t.hand() + ", ack " + t.ack()
                            + ", pickup " + t.pickup() + ", not digging " + Math.max(0, levelTicks - t.took());
                })
                .collect(Collectors.joining("; "));
        return String.format("level y=%d in %.0f s, its best %.0f s (%.0f%%)%s, %.0f s as it was split among %d - the crew's ticks went on: %s - ticks taken/best: %s",
                level, millis / 1000.0, bestMillis() / 1000.0, 100 * share(), slow() ? " - BELOW 90%, look into it" : "",
                splitMillis() / 1000.0, Math.max(crew, tallies.size()), shares(spentInAll()), builders);
    }

    /** Each thing's share of the ticks, the most first: "clearer 52%, waiting 30%, ...". */
    static String shares(Map<String, Integer> ticks) {
        long all = ticks.values().stream().mapToLong(Integer::longValue).sum();
        if (all == 0) {
            return "nothing recorded";
        }
        return ticks.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> e.getKey() + " " + Math.round(100.0 * e.getValue() / all) + "%")
                .collect(Collectors.joining(", "));
    }

    /**
     * The dig so far, level on level: its levels' best against what they took, and where the crew's
     * ticks went - the figure that says whether the method is within {@link #LOOK_INTO} of its best,
     * steadier than any one level's.
     */
    public record Run(int levels, long millis, long bestMillis, Map<String, Integer> spent) {

        public static final Run NONE = new Run(0, 0, 0, Map.of());

        public Run {
            spent = Map.copyOf(spent);
        }

        public Run plus(LevelPace pace) {
            Map<String, Integer> all = new HashMap<>(spent);
            pace.spentInAll().forEach((what, ticks) -> all.merge(what, ticks, Integer::sum));
            return new Run(levels + 1, millis + pace.millis(), bestMillis + pace.bestMillis(), all);
        }

        public double share() {
            return millis <= 0 ? 1 : (double) bestMillis / millis;
        }

        public boolean slow() {
            return share() < LOOK_INTO;
        }

        public String summary() {
            return String.format("the dig so far: %d level(s) in %.0f s, their best %.0f s (%.0f%%)%s - the crew's ticks went on: %s",
                    levels, millis / 1000.0, bestMillis / 1000.0, 100 * share(), slow() ? " - BELOW 90%" : " - within 90%", shares(spent));
        }
    }
}
