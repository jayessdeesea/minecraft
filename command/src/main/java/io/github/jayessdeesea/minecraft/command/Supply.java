package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;

/**
 * A class of supply's levels at a site, the way the Army sets them (FM 4-0; FM 10-27;
 * docs/military-doctrine.md § 17): stock counted in days of supply against levels set from its burn rate,
 * not from fixed counts.
 * <ul>
 *   <li>The <b>safety level</b>: two days of upkeep - what the site needs to keep running through a
 *       replenishment that's late, or a loss.</li>
 *   <li>The <b>reorder point</b>: the safety level and what's burnt while more is made (the lead time).</li>
 *   <li>The <b>stockage objective</b>: the reorder point and a day of the work committed.</li>
 * </ul>
 * Its state: ADVANCE at the objective - new work may start; PAUSE below the safety level - stop and
 * fill up; HOLD between, carrying on with the work it has and starting none.
 *
 * @param name      the class: "Class I (rations)"
 * @param stock     what's on hand
 * @param upkeep    the burn of running the site, a day
 * @param committed the burn of the work it supplies, a day
 * @param leadDays  the days making more takes
 */
public record Supply(String name, int stock, double upkeep, double committed, double leadDays) {

    /** Days of upkeep the safety level holds (FM 10-27: three days in an airhead, two at least). */
    public static final double SAFETY_DAYS = 2;

    /** The state a class's stock puts its site in. */
    public enum State { ADVANCE, HOLD, PAUSE }

    /** The day's whole burn. */
    public double burn() {
        return upkeep + committed;
    }

    public double safety() {
        return SAFETY_DAYS * upkeep;
    }

    public double reorderPoint() {
        return safety() + leadDays * burn();
    }

    public double objective() {
        return reorderPoint() + committed;
    }

    /** Stock over the day's burn: how many days it lasts; endless with nothing burnt. */
    public double days() {
        return burn() <= 0 ? Double.POSITIVE_INFINITY : stock / burn();
    }

    public State state() {
        return stock < safety() ? State.PAUSE : stock >= objective() ? State.ADVANCE : State.HOLD;
    }

    /** "Class I (rations): 340, 4.2 days at 81 a day; safety 162, reorder 243, objective 243 - ADVANCE". */
    public String line() {
        return String.format(Locale.ROOT, "%s: %d, %s at %.0f a day; safety %.0f, reorder %.0f, objective %.0f - %s", name, stock,
                Double.isInfinite(days()) ? "no burn yet" : String.format(Locale.ROOT, "%.1f days", days()), burn(), safety(), reorderPoint(),
                objective(), state());
    }

    /**
     * A class's upkeep, measured (docs/military-doctrine.md § 17: upkeep doesn't stop when the site pauses,
     * so measuring it is safe): every fall in the stock between two looks is consumption - eaten, worn
     * out, lost - and every rise production, which isn't counted. A day's burn is the falls of the last
     * {@code DAYS} days over the time they span; with less than a day seen, the falls so far over that.
     * Not thread-safe: one owner.
     */
    public static final class Burn {

        /** Days the moving average spans. */
        public static final int DAYS = 3;

        private final long dayMillis;
        private final Deque<long[]> falls = new ArrayDeque<>(); // {at, how much}
        private long firstAt = Long.MIN_VALUE;
        private int last = -1;

        /** {@code dayMillis}: an in-game day on the clock it's fed. */
        public Burn(long dayMillis) {
            this.dayMillis = dayMillis;
        }

        /** The stock as looked at {@code now}. */
        public synchronized void stock(int stock, long now) {
            if (firstAt == Long.MIN_VALUE) {
                firstAt = now;
            }
            if (last >= 0 && stock < last) {
                falls.addLast(new long[]{now, last - stock});
            }
            last = stock;
            while (!falls.isEmpty() && falls.peekFirst()[0] < now - DAYS * dayMillis) {
                falls.removeFirst();
            }
            this.now = now;
        }

        private long now;

        /** When it was first looked at, and each fall since as {at, how much}: what a restart restores (app.BurnStore). */
        public synchronized long[][] snapshot() {
            List<long[]> all = new ArrayList<>();
            all.add(new long[]{firstAt, last});
            falls.forEach(f -> all.add(f.clone()));
            return all.toArray(new long[0][]);
        }

        /** It as {@code snapshot} left it: its first look and stock, and its falls - the days before a restart still counted. */
        public synchronized void restore(long[][] snapshot) {
            if (snapshot.length == 0) {
                return;
            }
            firstAt = snapshot[0][0];
            last = (int) snapshot[0][1];
            falls.clear();
            for (int i = 1; i < snapshot.length; i++) {
                falls.addLast(snapshot[i].clone());
            }
        }

        /** Consumption a day: the falls over the days they span - at least one, so a first hour's meal isn't a day's. */
        public synchronized double perDay() {
            if (firstAt == Long.MIN_VALUE) {
                return 0;
            }
            long span = Math.min(now - firstAt, DAYS * dayMillis);
            double days = Math.max(1, (double) span / dayMillis);
            return falls.stream().mapToLong(f -> f[1]).sum() / days;
        }
    }
}
