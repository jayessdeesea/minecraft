package io.github.jayessdeesea.minecraft.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Every request going up the chain of command, from when it's raised to when it's answered
 * (docs/military-doctrine.md § 4): a soldier's CANTCO to its squad leader, a squad's finished or
 * stalled task and its request for food to the general. A request is answered when the echelon it
 * went to changes the order it was about, or settles by itself when what raised it clears. One
 * still open past its echelon's deadline raises an alarm, once - so a request is never dropped
 * without its being said, and how long each echelon takes to answer is measured, not guessed.
 *
 * <p>Pure bookkeeping over the times it's given; thread-safe, since the squad loop raises and the
 * general's thread answers.
 */
final class RequestLedger {

    /** Who a request goes to, and how long it may wait for an answer. */
    enum Echelon {
        /** A soldier's request: its squad leader's drills and model run every second to 20 seconds. */
        SQUAD_LEADER(60_000),
        /** A squad's request: the general decides every two minutes, and at dusk and dawn. */
        GENERAL(300_000);

        final long deadlineMillis;

        Echelon(long deadlineMillis) {
            this.deadlineMillis = deadlineMillis;
        }
    }

    private record Open(Echelon to, String from, String what, long raised, boolean alarmed) {
    }

    private final Map<String, Open> open = new LinkedHashMap<>();
    /** Answer times, by echelon, since the last review. */
    private final Map<Echelon, List<Long>> answered = new TreeMap<>();
    private final Map<Echelon, Integer> settled = new TreeMap<>();
    private final Map<Echelon, Integer> alarms = new TreeMap<>();

    /**
     * {@code key}'s request, from {@code from} to {@code to}, saying {@code what}, is open at
     * {@code now}: raised now, or still open since it was. Returns the line to log when it's new.
     */
    synchronized Optional<String> raise(String key, Echelon to, String from, String what, long now) {
        if (open.containsKey(key)) {
            return Optional.empty();
        }
        open.put(key, new Open(to, from, what, now, false));
        return Optional.of(String.format(Locale.ROOT, "[Request] %s -> %s: %s", from, label(to), what));
    }

    /** {@code key}'s request answered at {@code now}: its order changed. Returns the line to log, if one was open. */
    synchronized Optional<String> answer(String key, long now) {
        Open request = open.remove(key);
        if (request == null) {
            return Optional.empty();
        }
        long took = now - request.raised();
        answered.computeIfAbsent(request.to(), e -> new ArrayList<>()).add(took);
        return Optional.of(String.format(Locale.ROOT, "[Request] %s answered %s in %d s: %s", label(request.to()),
                request.from(), took / 1000, request.what()));
    }

    /** {@code key}'s request no longer stands - what raised it cleared before any answer. */
    synchronized void settle(String key) {
        Open request = open.remove(key);
        if (request != null) {
            settled.merge(request.to(), 1, Integer::sum);
        }
    }

    /** Whether {@code key}'s request is open. */
    synchronized boolean isOpen(String key) {
        return open.containsKey(key);
    }

    /** The alarms due at {@code now}: each open request past its deadline, said once. */
    synchronized List<String> alarms(long now) {
        List<String> lines = new ArrayList<>();
        open.replaceAll((key, request) -> {
            if (request.alarmed() || now - request.raised() < request.to().deadlineMillis) {
                return request;
            }
            alarms.merge(request.to(), 1, Integer::sum);
            lines.add(String.format(Locale.ROOT, "[Request] ALARM: %s's request to %s unanswered after %d s: %s",
                    request.from(), label(request.to()), (now - request.raised()) / 1000, request.what()));
            return new Open(request.to(), request.from(), request.what(), request.raised(), true);
        });
        return lines;
    }

    /** The review's lines: each echelon's answers - how many, how fast - settled, alarms, and what's open. The tally starts again. */
    synchronized List<String> review(long now) {
        List<String> lines = new ArrayList<>();
        lines.add("requests up the chain, by who they went to:");
        for (Echelon to : Echelon.values()) {
            List<Long> times = new ArrayList<>(answered.getOrDefault(to, List.of()));
            times.sort(null);
            long stillOpen = open.values().stream().filter(r -> r.to() == to).count();
            if (times.isEmpty() && stillOpen == 0 && !settled.containsKey(to)) {
                continue;
            }
            lines.add(String.format(Locale.ROOT, "  %s: %d answered%s, %d settled by themselves, %d alarms, %d open", label(to),
                    times.size(), times.isEmpty() ? "" : String.format(Locale.ROOT, " (median %d s, longest %d s)",
                            times.get(times.size() / 2) / 1000, times.getLast() / 1000),
                    settled.getOrDefault(to, 0), alarms.getOrDefault(to, 0), stillOpen));
            open.values().stream().filter(r -> r.to() == to).forEach(r -> lines.add(String.format(Locale.ROOT,
                    "    open %d s: %s - %s", (now - r.raised()) / 1000, r.from(), r.what())));
        }
        answered.clear();
        settled.clear();
        alarms.clear();
        return lines;
    }

    private static String label(Echelon to) {
        return to == Echelon.GENERAL ? "General" : "squad leader";
    }
}
