package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * An echelon's register of what it has tasked and to whom, followed to completion
 * (backlog/task-register.md; ATP 3-21.8, "supervise and refine"). Each cycle it's told what each
 * member is on, what the member reports, and what's left of the task as the world measures it.
 * It trusts the report and verifies it: a member that reports working while what's left hasn't
 * fallen in {@link #VERIFY_MILLIS} is a finding, the tasking STALLED - live, a soldier reported
 * stockpiling all night at a chest that wasn't there. A CANTCO is held open against its tasking
 * until the tasking changes. A tasking done is counted for the dawn review and then forgotten;
 * the sites a task leaves are the operations map's to keep (backlog/operations-map.md).
 *
 * <p>Pure: the caller passes the time. Not thread-safe; the squad loop keeps one per squad.
 */
public final class TaskRegister {

    /** How long a member may report working with nothing left falling before it's checked out. */
    public static final long VERIFY_MILLIS = 120_000;

    public enum State { UNDER_WAY, STALLED, CANTCO, DONE }

    /** What a member is on this cycle: its task, its report, and what's left of the task, where that's measured. */
    public record Assignment(String task, Report report, OptionalInt left) {
    }

    /** One tasking: who, what, since when, and how it stands. */
    public record Tasking(String who, String task, long given, State state, Report report, OptionalInt best, long bestAt) {
        /** One line for the log and the review. */
        public String line(long now) {
            return who + ": " + task + " " + state + " for " + (now - given) / 1000 + " s, reports " + report
                    + (best.isPresent() ? ", " + best.getAsInt() + " left" : "");
        }
    }

    private final Map<String, Tasking> open = new LinkedHashMap<>();
    /** Taskings ended since the last review: how each ended, by task. */
    private final Map<String, int[]> ended = new LinkedHashMap<>();

    /** As {@link #update(long, Map, Map)}, with no task measured once its members are off it. */
    public List<String> update(long now, Map<String, Assignment> assignments) {
        return update(now, assignments, Map.of());
    }

    /**
     * This cycle's {@code assignments}, by member: records each, and returns what changed - a new
     * tasking, one done or given up, one the world doesn't bear out, a CANTCO raised. {@code left}
     * is what's left of each task the echelon measures, by task: a member taken off a task that
     * measures 0 is done with it - the work ran out, as it does when the last log falls.
     */
    public List<String> update(long now, Map<String, Assignment> assignments, Map<String, Integer> left) {
        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, Tasking> entry : List.copyOf(open.entrySet())) {
            Assignment now_ = assignments.get(entry.getKey());
            if (now_ == null || !now_.task().equals(entry.getValue().task())) {
                Tasking ending = open.remove(entry.getKey());
                Tasking left_ = ending.state() != State.DONE && left.getOrDefault(ending.task(), -1) == 0
                        ? done(ending, now, findings) : ending;
                end(left_, left_.state() == State.DONE ? 0 : 1);
                if (left_.state() != State.DONE) {
                    findings.add("[Register] " + left_.who() + " taken off " + left_.task() + " unfinished, after "
                            + (now - left_.given()) / 1000 + " s (" + left_.state() + ")");
                }
            }
        }
        assignments.forEach((who, a) -> {
            Tasking t = open.get(who);
            // Done, and the work back - a torch washed away, a wall block broken: a new tasking.
            if (t != null && t.state() == State.DONE && a.left().orElse(0) > 0) {
                end(t, 0);
                t = null;
            }
            if (t == null) {
                t = new Tasking(who, a.task(), now, State.UNDER_WAY, a.report(), a.left(), now);
                findings.add("[Register] " + who + " on " + a.task() + (a.left().isPresent() ? ", " + a.left().getAsInt() + " left" : ""));
            }
            open.put(who, next(t, a, now, findings));
        });
        return findings;
    }

    private static Tasking done(Tasking t, long now, List<String> findings) {
        findings.add("[Register] " + t.who() + " done: " + t.task() + ", in " + (now - t.given()) / 1000 + " s");
        return new Tasking(t.who(), t.task(), t.given(), State.DONE, t.report(), OptionalInt.of(0), now);
    }

    private static Tasking next(Tasking t, Assignment a, long now, List<String> findings) {
        if (t.state() == State.DONE) {
            return t;
        }
        OptionalInt best = t.best();
        long bestAt = t.bestAt();
        if (a.left().isPresent() && (best.isEmpty() || a.left().getAsInt() < best.getAsInt())) {
            best = a.left();
            bestAt = now;
        }
        State state;
        if (a.left().isPresent() && a.left().getAsInt() == 0) {
            state = State.DONE;
            findings.add("[Register] " + t.who() + " done: " + t.task() + ", in " + (now - t.given()) / 1000 + " s");
        } else if (a.report() instanceof Report.Cantco(String reason)) {
            state = State.CANTCO;
            if (t.state() != State.CANTCO) {
                findings.add("[Register] " + t.who() + " CANTCO on " + t.task() + ": " + reason);
            }
        } else if (Progress.working(a.report()) && best.isPresent() && now - bestAt >= VERIFY_MILLIS) {
            state = State.STALLED;
            if (t.state() != State.STALLED) {
                findings.add("[Verify] " + t.who() + " reports " + a.report() + " on " + t.task() + ", but " + best.getAsInt()
                        + " left hasn't fallen in " + (now - bestAt) / 1000 + " s");
            }
        } else if (t.state() == State.STALLED && bestAt != now) {
            state = State.STALLED; // stalled until what's left falls, not until the report changes
        } else {
            state = State.UNDER_WAY;
            if (t.state() == State.STALLED) {
                findings.add("[Verify] " + t.who() + " making progress on " + t.task() + " again, " + best.getAsInt() + " left");
            }
        }
        return new Tasking(t.who(), t.task(), t.given(), state, a.report(), best, bestAt);
    }

    private void end(Tasking t, int column) {
        ended.computeIfAbsent(t.task(), k -> new int[2])[column]++;
    }

    /** The taskings open now. */
    public List<Tasking> open() {
        return List.copyOf(open.values());
    }

    /**
     * The dawn review: each task's taskings done and given up unfinished since the last review, then
     * each one still open; the ended ones are forgotten.
     */
    public List<String> review(long now) {
        List<String> lines = new ArrayList<>();
        ended.forEach((task, counts) -> lines.add("  " + task + ": " + counts[0] + " done, " + counts[1] + " taken off unfinished"));
        ended.clear();
        open.values().forEach(t -> lines.add("    open: " + t.line(now)));
        return lines;
    }
}
