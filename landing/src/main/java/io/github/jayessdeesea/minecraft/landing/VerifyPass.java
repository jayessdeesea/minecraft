package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A verify that passed, read once from its log when it passes - never from the candidate's tree or report files, whose
 * stale ones cannot count. Each module's wall time, compile and packaging included, is its Reactor Summary line; each
 * test class's time is its {@code -- in <class>} line, under the module begun before it. Under parallelism a class's
 * time is not wall time: it counts only as its share of its module, whose wall carries the seconds.
 *
 * @param at        when it passed
 * @param candidate the commit verified
 * @param took      the verify's whole time, as its watchdog watched it
 * @param modules   the modules in the order built
 */
public record VerifyPass(Instant at, Sha candidate, Duration took, List<Module> modules) {

    /** A module's wall time and its test classes' times, in the order run; each span in the bound, or refused. */
    public record Module(String name, Duration wall, Map<String, Duration> classes) {
        public Module {
            bounded("wall of " + name, wall);
            classes.forEach((c, t) -> bounded("time of " + c, t));
            classes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        }
    }

    /**
     * The longest span a pass holds - its time, a module's wall, a class's time: no verify outlives the run budget, and
     * five passes' spans summed in milliseconds are nowhere near a long, so all that reads them is total with no check
     * of its own. The bound is the type's: a span outside [0, this] is refused where a pass is made.
     */
    public static final Duration MAX_SPAN = Duration.ofHours(24);

    /** {@code [INFO] protocol ...... SUCCESS [  3.985 s]}, {@code [25:31 min]}, {@code [01:02 h]}. */
    static final Pattern SUMMARY = Pattern.compile("^\\[INFO] (\\S+) \\.+ \\S+ \\[\\s*([0-9.:]+) (s|min|h)]$");

    public VerifyPass {
        bounded("took", took);
        modules = List.copyOf(modules);
    }

    /** Whether {@code span} is one a pass may hold: in [0, {@link #MAX_SPAN}], compared as a duration. */
    static boolean inBound(Duration span) {
        return !span.isNegative() && span.compareTo(MAX_SPAN) <= 0;
    }

    private static void bounded(String what, Duration span) {
        if (span == null || !inBound(span)) {
            throw new IllegalArgumentException("a pass's " + what + " of " + span + ", outside [0, " + MAX_SPAN + "]");
        }
    }

    /** The pass its log describes; none without a Reactor Summary, which only a whole build writes. */
    public static Optional<VerifyPass> of(List<String> lines, Instant at, Sha candidate, Duration took) {
        if (!inBound(took)) {
            return Optional.empty();
        }
        Map<String, Map<String, Duration>> classes = new LinkedHashMap<>();
        Map<String, Duration> walls = new LinkedHashMap<>();
        String module = "";
        for (String raw : lines) {
            String line = MavenLog.plain(raw);
            Optional<MavenLog.ModuleLine> building = MavenLog.ModuleLine.of(line);
            if (building.isPresent()) {
                module = building.get().name();
                classes.putIfAbsent(module, new LinkedHashMap<>());
                continue;
            }
            Optional<MavenLog.ClassLine> c = MavenLog.ClassLine.of(line);
            if (c.isPresent()) {
                // a time no verify could take is not put: a class the history does not know, no weight
                if (inBound(c.get().time())) {
                    classes.computeIfAbsent(module, m -> new LinkedHashMap<>()).put(c.get().name(), c.get().time());
                }
                continue;
            }
            Matcher summary = SUMMARY.matcher(line);
            if (summary.matches()) {
                String name = summary.group(1);
                wall(summary.group(2), summary.group(3)).ifPresent(w -> walls.put(name, w));
            }
        }
        if (walls.isEmpty()) {
            return Optional.empty();
        }
        List<Module> modules = new ArrayList<>();
        walls.forEach((name, wall) -> modules.add(new Module(name, wall, classes.getOrDefault(name, Map.of()))));
        return Optional.of(new VerifyPass(at, candidate, took, modules));
    }

    /** Maven's summary time: seconds with a fraction, {@code mm:ss min}, or {@code hh:mm h}. */
    private static Optional<Duration> wall(String value, String unit) {
        try {
            Duration wall;
            if (unit.equals("s")) {
                if (value.contains(":")) {
                    return Optional.empty();
                }
                wall = Duration.ofMillis(Math.round(Double.parseDouble(value) * 1000));
            } else {
                String[] p = value.split(":");
                if (p.length != 2) {
                    return Optional.empty();
                }
                long high = Long.parseLong(p[0]);
                long low = Long.parseLong(p[1]);
                wall = unit.equals("min") ? Duration.ofMinutes(high).plusSeconds(low) : Duration.ofHours(high).plusMinutes(low);
            }
            // a time no verify could take is a line that will not read: the bound, as a duration, before any millis
            return inBound(wall) ? Optional.of(wall) : Optional.empty();
        } catch (NumberFormatException | ArithmeticException e) {
            // a line in the summary's shape that will not read: skipped, as any display-only parse
            return Optional.empty();
        }
    }
}
