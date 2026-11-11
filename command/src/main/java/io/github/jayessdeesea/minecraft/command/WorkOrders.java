package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The squad leader's work orders: each soldier's current {@link Directive}, followed until it's done
 * or timed out (docs/military-doctrine.md § 18). What's left of it is measured from the world each
 * cycle; one whose measure hasn't fallen in {@link #STALL_MILLIS}, or that has run past its
 * allowance - a minute, and a little more for each unit of work it started with - is timed out, so
 * the soldier goes back to its role and its squad leader hears why, rather than waiting on it.
 * Contact pauses both clocks: a soldier fighting is doing what it should. Nothing left - a rifleman
 * holding the point it was sent to - is never timed out.
 *
 * <p>Pure: the caller passes the time. Not thread-safe; the squad loop keeps one per squad.
 */
public final class WorkOrders {

    /** How long what's left may not fall before a work order is timed out. */
    public static final long STALL_MILLIS = 120_000;
    /** Every work order's allowance starts at this. */
    static final long BASE_ALLOWANCE_MILLIS = 60_000;
    /** How long a timed-out work order is still shown its squad leader. */
    public static final long SHOWN_MILLIS = 5 * 60_000;

    /** One work order: whose, what, when given, its allowance's end, and the least left so far, and when. */
    public record WorkOrder(String soldier, Directive directive, long issued, long deadline, int best, long bestAt, long seen) {
    }

    /** A work order timed out: what, why, and when. */
    public record TimedOut(String soldier, Directive directive, String why, long at) {
    }

    private final Map<String, WorkOrder> open = new LinkedHashMap<>();
    private final Map<String, TimedOut> timedOut = new HashMap<>();

    /** How long a directive may take, from {@code left} of it when given. */
    static long allowance(Directive directive, int left) {
        long each = switch (directive) {
            case Directive.GoTo _ -> 2_000;      // a block walked
            case Directive.Fetch _ -> 20_000;    // a log chopped, a block dug, with the walking between
            case Directive.Level _ -> 4_000;     // a cell dug or filled
        };
        return BASE_ALLOWANCE_MILLIS + each * Math.max(0, left);
    }

    /**
     * This cycle: each soldier's current directive, what's left of it, and who's in contact. Returns
     * the work orders timed out now, each forgotten - the caller takes it off the soldier's plan.
     */
    public List<TimedOut> update(long now, Map<String, Directive> current, Map<String, Integer> left, Set<String> inContact) {
        open.keySet().removeIf(soldier -> !current.containsKey(soldier) || !current.get(soldier).equals(open.get(soldier).directive()));
        timedOut.values().removeIf(t -> now - t.at() > SHOWN_MILLIS);
        List<TimedOut> ended = new ArrayList<>();
        current.forEach((soldier, directive) -> {
            int remaining = left.getOrDefault(soldier, 0);
            WorkOrder w = open.get(soldier);
            if (w == null) {
                open.put(soldier, new WorkOrder(soldier, directive, now, now + allowance(directive, remaining), remaining, now, now));
                return;
            }
            if (inContact.contains(soldier)) {
                long paused = now - w.seen();
                open.put(soldier, new WorkOrder(soldier, directive, w.issued(), w.deadline() + paused, w.best(), w.bestAt() + paused, now));
                return;
            }
            int best = Math.min(w.best(), remaining);
            long bestAt = remaining < w.best() ? now : w.bestAt();
            Optional<String> why = remaining == 0 ? Optional.empty()
                    : now - bestAt >= STALL_MILLIS ? Optional.of("no progress in " + (now - bestAt) / 1000 + " s, " + remaining + " left")
                    : now > w.deadline() ? Optional.of("not done in its " + (w.deadline() - w.issued()) / 1000 + " s, " + remaining + " left")
                    : Optional.empty();
            if (why.isPresent()) {
                open.remove(soldier);
                TimedOut t = new TimedOut(soldier, directive, why.get(), now);
                timedOut.put(soldier, t);
                ended.add(t);
            } else {
                open.put(soldier, new WorkOrder(soldier, directive, w.issued(), w.deadline(), best, bestAt, now));
            }
        });
        return ended;
    }

    /**
     * Whether {@code directive} timed out for {@code soldier} lately - within {@link #SHOWN_MILLIS},
     * while its squad leader is shown why: given again in that time, it's refused, not taken up to
     * time out again.
     */
    public boolean barred(String soldier, Directive directive, long now) {
        TimedOut t = timedOut.get(soldier);
        return t != null && t.directive().equals(directive) && now - t.at() <= SHOWN_MILLIS;
    }

    /**
     * How {@code soldier}'s work order stands, in words for its squad leader: under way, with what's
     * left; or the one lately timed out, and why. Empty with neither.
     */
    public Optional<String> standing(String soldier, long now) {
        WorkOrder w = open.get(soldier);
        if (w != null) {
            return Optional.of(w.best() == 0 && w.directive() instanceof Directive.GoTo ? "there"
                    : "under way " + (now - w.issued()) / 1000 + " s, " + w.best() + " left");
        }
        TimedOut t = timedOut.get(soldier);
        return t == null ? Optional.empty()
                : Optional.of("his last order, " + t.directive().describe() + ", timed out " + (now - t.at()) / 1000 + " s ago: " + t.why());
    }
}
