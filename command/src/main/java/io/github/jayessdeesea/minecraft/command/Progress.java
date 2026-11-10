package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How a squad's task stands, measured rather than claimed (docs/military-doctrine.md § 4): under
 * way with what's left, done, or stalled with why. It's the squad's answer to the general's task -
 * without it a finished task and a blocked one both read as an order to keep, and live, a squad
 * stood by a finished mine for an hour while the general kept MINE.
 */
public record Progress(State state, String detail) {

    /** How long nobody may work at a task before it's stalled: longer than a squad cycle's flicker. */
    public static final long STALLED_AFTER_MILLIS = 60_000;

    public enum State { UNDER_WAY, DONE, STALLED }

    public Progress {
        java.util.Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(detail, "detail");
    }

    /** Whether {@code report} shows a soldier at work, or on its way to it: anything but standing or refusing. */
    public static boolean working(Report report) {
        return switch (report) {
            case Report.Idle _, Report.InPosition _, Report.Cantco _, Report.Recovering _ -> false;
            default -> true;
        };
    }

    /**
     * The task's progress: {@code done} when the world shows it's done, saying how; otherwise stalled
     * when no soldier has worked at it for {@link #STALLED_AFTER_MILLIS}, with their reasons counted;
     * otherwise under way, with {@code left}, what's left where that's measured.
     */
    public static Progress of(Optional<String> done, Optional<String> left, List<SoldierStatus> soldiers, long idleMillis) {
        if (done.isPresent()) {
            return new Progress(State.DONE, done.get());
        }
        if (idleMillis >= STALLED_AFTER_MILLIS && soldiers.stream().noneMatch(s -> working(s.report()))) {
            Map<String, Integer> reasons = new LinkedHashMap<>();
            int standing = 0;
            for (SoldierStatus s : soldiers) {
                if (s.report() instanceof Report.Cantco(String reason)) {
                    reasons.merge(reason, 1, Integer::sum);
                } else {
                    standing++;
                }
            }
            StringBuilder why = new StringBuilder(String.format(Locale.ROOT, "nobody has worked at it for %d min",
                    Math.round(idleMillis / 60_000.0)));
            reasons.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                    .forEach(e -> why.append(String.format(Locale.ROOT, "; %d of %d report CANTCO: %s", e.getValue(), soldiers.size(), e.getKey())));
            if (standing > 0) {
                why.append(String.format(Locale.ROOT, "; %d of %d stand with nothing to do", standing, soldiers.size()));
            }
            left.ifPresent(l -> why.append("; still ").append(l));
            return new Progress(State.STALLED, why.toString());
        }
        return new Progress(State.UNDER_WAY, left.orElse("under way"));
    }
}
