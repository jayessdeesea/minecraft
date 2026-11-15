package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How fast a level of the keep came down against the fastest it could have (operator, 2026-11-14:
 * predict the theoretical maximum from the route, the tool's break time and the travel; below 90%,
 * dig in and find out why). Each builder's {@link Tally} adds up its digs: the break time vanilla
 * gives the block with the best tool the builder carried, and the walk between the stands its route
 * took at walking speed - its best - against the ticks each part of the dig actually took. The
 * level's best is its slowest builder's best, since the level falls when its last block does.
 *
 * @param level   the level's height
 * @param millis  how long it took, from its plan to its fall
 * @param tallies each builder's digs on it
 */
public record LevelPace(int level, long millis, Map<String, Tally> tallies) {

    /** Below this share of its best, a level is looked into. */
    public static final double LOOK_INTO = 0.9;
    /** Milliseconds a server tick takes. */
    public static final int TICK_MILLIS = 50;

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

    /** The fastest the level could have come down: its slowest builder's best. */
    public long bestMillis() {
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

    /**
     * The level's line: what it took against its best, and each builder's digs - where its ticks
     * went against its best, and the time it wasn't digging at all.
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
        return String.format("level y=%d in %.0f s, its best %.0f s (%.0f%%)%s - ticks taken/best: %s",
                level, millis / 1000.0, bestMillis() / 1000.0, 100 * share(), slow() ? " - BELOW 90%, look into it" : "", builders);
    }
}
