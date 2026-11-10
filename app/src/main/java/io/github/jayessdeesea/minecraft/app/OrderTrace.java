package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One operator order followed down the chain of command until the soldiers are at it
 * (docs/military-doctrine.md § 3): received, then the general's orders to each squad, then each
 * squad leader's FRAGO under them, then the squad's soldiers seen doing the task - the positive
 * acknowledgement that it arrived, not just that it was sent. Each hop is timed, and one that takes
 * too long raises an alarm, once. Pure: the clock is passed in, and what it has to say comes back
 * as lines for the caller to log and put on the radio.
 */
final class OrderTrace {

    /** The general's decision on an operator's order: after this long, an alarm. */
    static final long GENERAL_ALARM_MILLIS = 30_000;
    /** A squad leader's FRAGO under the general's order. */
    static final long FRAGO_ALARM_MILLIS = 45_000;
    /** A squad's soldiers seen doing the task, from the general's order. */
    static final long WORKING_ALARM_MILLIS = 180_000;

    /** One squad's part: what it was told (empty: it holds the order it had), and when each hop came. */
    private static final class Leg {
        final Optional<Objective.Task> task;
        final long orderedAt;
        long fragoAt = -1;
        long workingAt = -1;
        boolean fragoAlarmed;
        boolean workingAlarmed;

        Leg(Optional<Objective.Task> task, long orderedAt) {
            this.task = task;
            this.orderedAt = orderedAt;
        }
    }

    final String id;
    final String operator;
    final String order;
    final long receivedAt;
    /** The chain of command's mission version the order made: the general's decision under it is the one traced. */
    final int version;
    private long decidedAt = -1;
    private boolean generalAlarmed;
    private final Map<String, Leg> legs = new LinkedHashMap<>();
    private boolean closed;

    OrderTrace(String id, String operator, String order, long receivedAt, int version) {
        this.id = id;
        this.operator = operator;
        this.order = order;
        this.receivedAt = receivedAt;
        this.version = version;
    }

    /** An order that goes straight to a squad, no general between: an escort. */
    synchronized List<String> direct(String squad, Optional<Objective.Task> task, long now) {
        decidedAt = now;
        legs.put(squad, new Leg(task, now));
        return List.of(id + ": " + squad + " ordered directly");
    }

    /** The general decided under this order: what each squad was given, empty where it holds what it had. */
    synchronized List<String> decided(Map<String, Optional<Objective>> given, long now) {
        if (decidedAt >= 0 || closed) {
            return List.of();
        }
        decidedAt = now;
        List<String> parts = new ArrayList<>();
        given.forEach((squad, objective) -> {
            legs.put(squad, new Leg(objective.map(Objective::task), now));
            parts.add(squad + (objective.isPresent() ? " " + taskName(objective.get().task()) : " holds its order"));
        });
        return List.of(id + ": the general decided in " + seconds(now - receivedAt) + " - " + String.join(", ", parts));
    }

    /**
     * A SITREP from {@code squad}: {@code lastFrago} is when its squad leader last gave the squad an
     * order, and {@code soldiers} what they're doing. Lines for each hop come, and each alarm due.
     */
    synchronized List<String> sitrep(String squad, long lastFrago, List<SoldierStatus> soldiers, long now) {
        List<String> lines = new ArrayList<>();
        if (closed) {
            return lines;
        }
        if (decidedAt < 0) {
            if (!generalAlarmed && now - receivedAt > GENERAL_ALARM_MILLIS) {
                generalAlarmed = true;
                lines.add("ALARM " + id + ": no decision from the general " + seconds(now - receivedAt) + " after the order");
            }
            return lines;
        }
        Leg leg = legs.get(squad);
        if (leg == null) {
            return lines;
        }
        if (leg.fragoAt < 0 && lastFrago >= leg.orderedAt) {
            leg.fragoAt = lastFrago;
            lines.add(id + ": " + squad + "'s squad leader gave the squad its order (FRAGO) " + seconds(lastFrago - receivedAt)
                    + " after the operator's");
        }
        if (leg.workingAt < 0 && soldiers.stream().anyMatch(s -> working(leg.task, s.report()))) {
            leg.workingAt = now;
            lines.add(id + ": " + squad + " WILCO - at it (" + soldiers.stream().map(SoldierStatus::report).filter(r -> working(leg.task, r))
                    .findFirst().map(r -> r.getClass().getSimpleName()).orElseThrow() + ") " + seconds(now - receivedAt) + " after the order");
        }
        if (leg.fragoAt < 0 && !leg.fragoAlarmed && now - leg.orderedAt > FRAGO_ALARM_MILLIS) {
            leg.fragoAlarmed = true;
            lines.add("ALARM " + id + ": " + squad + "'s squad leader hasn't passed the order on, " + seconds(now - leg.orderedAt)
                    + " after it came");
        }
        if (leg.workingAt < 0 && !leg.workingAlarmed && now - leg.orderedAt > WORKING_ALARM_MILLIS) {
            leg.workingAlarmed = true;
            lines.add("ALARM " + id + ": " + squad + " not yet at " + leg.task.map(OrderTrace::taskName).orElse("its order") + " "
                    + seconds(now - leg.orderedAt) + " after it came - its soldiers report "
                    + soldiers.stream().map(s -> s.name() + " " + s.report().getClass().getSimpleName()).toList());
        }
        if (legs.values().stream().allMatch(l -> l.workingAt >= 0)) {
            closed = true;
            lines.add(id + ": carried out down to the soldiers, " + seconds(now - receivedAt) + " from the order to the last squad at it");
        }
        return lines;
    }

    /** A newer order replaces this one before it was carried out. */
    synchronized Optional<String> supersede() {
        if (closed) {
            return Optional.empty();
        }
        closed = true;
        return Optional.of(id + ": superseded by a newer order before it was carried out");
    }

    synchronized boolean closed() {
        return closed;
    }

    /** Whether {@code report} is a soldier at {@code task} - a squad that holds, at whatever it was doing. */
    static boolean working(Optional<Objective.Task> task, Report report) {
        if (task.isEmpty()) {
            return !(report instanceof Report.Idle) && !(report instanceof Report.Cantco);
        }
        return switch (task.get()) {
            case Objective.Task.None() -> report instanceof Report.Moving || report instanceof Report.InPosition;
            case Objective.Task.Gather(var resource) -> report instanceof Report.Gathering;
            case Objective.Task.PatrolBase() -> report instanceof Report.Fortifying || report instanceof Report.InPosition;
            case Objective.Task.Farm() -> report instanceof Report.Farming;
            case Objective.Task.TreeFarm() -> report instanceof Report.Planting;
            case Objective.Task.Secure() -> report instanceof Report.Securing;
            case Objective.Task.Fortify() -> report instanceof Report.Fortifying || report instanceof Report.Working;
            case Objective.Task.Mine() -> report instanceof Report.Mining;
            case Objective.Task.Patrol() -> report instanceof Report.Patrolling;
        };
    }

    static String taskName(Objective.Task task) {
        return switch (task) {
            case Objective.Task.None() -> "moving";
            case Objective.Task.Gather(var resource) -> "gathering " + resource.name().toLowerCase(Locale.ROOT);
            case Objective.Task.PatrolBase() -> "PATROL_BASE";
            case Objective.Task.Farm() -> "FARM";
            case Objective.Task.TreeFarm() -> "TREE_FARM";
            case Objective.Task.Secure() -> "SECURE";
            case Objective.Task.Fortify() -> "FORTIFY";
            case Objective.Task.Mine() -> "MINE";
            case Objective.Task.Patrol() -> "PATROL";
        };
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
