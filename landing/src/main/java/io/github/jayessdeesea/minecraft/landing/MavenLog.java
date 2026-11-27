package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a verify's log says of where its build is - for display and the estimate only; nothing read here decides a kill
 * or an outcome. A module is begun at its {@code [INFO] Building <name> <version> [i/n]} line, and finished when a later
 * one, or a BUILD line, is there. A test class is done at its own {@code Tests run: ... -- in <class>} line - at a line's
 * start, so a test printing the words is not one, and a module's summary line (no {@code -- in}) is not either - and
 * failing when it had failures or errors. Colour codes are stripped first.
 *
 * @param modules the modules begun, in order
 * @param index   the last begun's place in the reactor, 1-based (0 before any)
 * @param of      the reactor's size (0 before any)
 * @param done    each class done, with the module it was done in - the same class in two modules is two
 * @param failing the classes done with failures or errors
 * @param ended   whether a BUILD line is there
 */
public record MavenLog(List<String> modules, int index, int of, Set<Done> done, int failing, boolean ended) {

    static final Pattern BUILDING = Pattern.compile("^\\[INFO] Building (\\S+) \\S+\\s+\\[(\\d+)/(\\d+)]$");
    static final Pattern CLASS = Pattern.compile("^\\[(?:INFO|WARNING|ERROR)] Tests run: \\d+, Failures: (\\d+), Errors: (\\d+), "
            + "Skipped: \\d+, Time elapsed: ([0-9.,]+) s\\b.*?-{1,2} in (\\S+)$");

    public MavenLog {
        modules = List.copyOf(modules);
        done = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(done));
    }

    /** A log's line as it is read here: colour codes and the surrounding space gone. */
    static String plain(String line) {
        return VerifyOutcome.COLOUR.matcher(line).replaceAll("").strip();
    }

    /** A class's own line, if {@code line} is one: its name, its time, whether it failed. */
    record ClassLine(String name, Duration time, boolean failing) {
        static Optional<ClassLine> of(String plainLine) {
            Matcher m = CLASS.matcher(plainLine);
            if (!m.matches()) {
                return Optional.empty();
            }
            try {
                long millis = Math.round(Double.parseDouble(m.group(3).replace(",", "")) * 1000);
                return Optional.of(new ClassLine(m.group(4), Duration.ofMillis(millis),
                        (long) Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)) > 0));
            } catch (NumberFormatException e) {
                // a test's own output in Maven's shape that will not read: skipped - the reading is display only
                return Optional.empty();
            }
        }
    }

    /** A module's own line, if {@code line} is one: its name, place and the reactor's size. */
    record ModuleLine(String name, int index, int of) {
        static Optional<ModuleLine> of(String plainLine) {
            Matcher m = BUILDING.matcher(plainLine);
            if (!m.matches()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new ModuleLine(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    /** A class done in a module: the same class name in two modules is two. */
    public record Done(String module, String name) {
    }

    public static MavenLog of(List<String> lines) {
        List<String> modules = new ArrayList<>();
        Set<Done> done = new LinkedHashSet<>();
        int index = 0;
        int of = 0;
        int failing = 0;
        boolean ended = false;
        for (String raw : lines) {
            String line = plain(raw);
            Optional<ModuleLine> module = ModuleLine.of(line);
            if (module.isPresent()) {
                modules.add(module.get().name());
                index = module.get().index();
                of = module.get().of();
                continue;
            }
            Optional<ClassLine> c = ClassLine.of(line);
            if (c.isPresent()) {
                if (done.add(new Done(modules.isEmpty() ? "" : modules.getLast(), c.get().name())) && c.get().failing()) {
                    failing++;
                }
                continue;
            }
            if (VerifyOutcome.SUCCESS.contains(line) || VerifyOutcome.FAILURE.contains(line)) {
                ended = true;
            }
        }
        return new MavenLog(modules, index, of, done, failing, ended);
    }

    /** Whether {@code module} is finished: a later module begun, or the build ended, after it. */
    public boolean finished(String module) {
        int at = modules.lastIndexOf(module);
        return at >= 0 && (at < modules.size() - 1 || ended);
    }

    /** Where it is, as a waiter is told it. */
    public MavenProgress progress() {
        return new MavenProgress(modules.isEmpty() ? Optional.empty() : Optional.of(modules.getLast()), index, of, done.size(),
                failing);
    }
}
