package io.github.jayessdeesea.minecraft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The soldier-seconds stood doing nothing, and whose they are (operator, 2026-11-21: a soldier stood there under orders
 * is bad for the soldier; one stood there with none is bad for the platoon leader). Each second past {@link #GRACE_MILLIS}
 * of a spell stood still - a block's pause isn't one - is counted: under orders, hands free and out of contact, the
 * soldier's; holding where sent with no work while its squad has a task by day, the chain of command's, by squad. Resting
 * - hiding, sleeping, down the mine by night - is neither. Said every {@link #SUMMARY_MILLIS}, and at dawn's review.
 * Pure: the clock is handed in, lines come back.
 */
final class StoodStill {

    /** A spell stood still shorter than this is a pause, not idleness. */
    static final long GRACE_MILLIS = 10_000;
    /** How often the tally is said. */
    static final long SUMMARY_MILLIS = 10 * 60_000;
    /** Soldiers or squads named in a line, the most seconds first. */
    static final int NAMED = 4;

    private final Map<String, Integer> soldiers = new TreeMap<>();
    private final Map<String, Integer> squads = new TreeMap<>();
    private final Map<String, Integer> dawnSoldiers = new TreeMap<>();
    private final Map<String, Integer> dawnSquads = new TreeMap<>();
    private long saidAt = Long.MIN_VALUE;
    /**
     * When each soldier's last second was counted: one a clock second, however often it's asked - live (field664), the
     * tick loop's catch-up after a stall asked many times a second, and soldiers were charged 1086 s in ten minutes.
     */
    private final Map<String, Long> countedAt = new java.util.HashMap<>();

    /**
     * One second of {@code soldier} of {@code squad} at {@code now}: stood still {@code underOrders} - its spell's length,
     * 0 for none - or {@code withoutWork}, its squad with work it wasn't given. A line when the tally is due.
     */
    synchronized Optional<String> second(String soldier, String squad, long underOrders, long withoutWork, long now) {
        Long last = countedAt.get(soldier);
        boolean counts = last == null || now - last >= 1000;
        if (counts) {
            countedAt.put(soldier, now);
        }
        if (!counts) {
            // Asked again within the second: nothing counted.
        } else if (underOrders >= GRACE_MILLIS) {
            soldiers.merge(soldier, 1, Integer::sum);
            dawnSoldiers.merge(soldier, 1, Integer::sum);
        } else if (withoutWork >= GRACE_MILLIS) {
            squads.merge(squad, 1, Integer::sum);
            dawnSquads.merge(squad, 1, Integer::sum);
        }
        if (saidAt == Long.MIN_VALUE) {
            saidAt = now;
            return Optional.empty();
        }
        if (now - saidAt < SUMMARY_MILLIS) {
            return Optional.empty();
        }
        saidAt = now;
        String line = line(SUMMARY_MILLIS / 60_000 + " min", soldiers, squads);
        soldiers.clear();
        squads.clear();
        return Optional.of(line);
    }

    /** The day's tally for dawn's review, as lines; it starts again. */
    synchronized List<String> review() {
        List<String> lines = new ArrayList<>();
        lines.add("stood doing nothing, by soldier-seconds: " + line("the day", dawnSoldiers, dawnSquads));
        dawnSoldiers.clear();
        dawnSquads.clear();
        return lines;
    }

    private static String line(String over, Map<String, Integer> soldiers, Map<String, Integer> squads) {
        return String.format(Locale.ROOT, "over %s, %d under orders - the soldiers'%s; %d without work - the chain of command's%s", over,
                total(soldiers), named(soldiers), total(squads), named(squads));
    }

    private static int total(Map<String, Integer> seconds) {
        return seconds.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String named(Map<String, Integer> seconds) {
        return seconds.isEmpty() ? "" : " (" + seconds.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(NAMED).map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")) + ")";
    }
}
