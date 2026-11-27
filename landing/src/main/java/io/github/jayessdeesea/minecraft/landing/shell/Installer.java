package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.ExitCode;
import io.github.jayessdeesea.minecraft.landing.Sha;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * {@code landing install <built jar> <installed jar> <built-from> <landing change> <git dir> <sh dir> <mvn dir>
 * [<key>=<value> ...]}, run by install-landing.sh from the jar it has just built: the config's one writer. Under the
 * integrator's lock, so no integrator runs from the jar while it is replaced, the jar is copied to its installed place
 * and the config written: every key there kept, each seed set only where there is none, and the commit the jar was
 * built from, the last change to {@code landing/} there - what the audit's I8 compares with main - and where git, sh
 * and mvn are ({@code tool.path}), which a scheduled task's PATH does not say.
 */
final class Installer {

    static final String BUILT_FROM = "jar.built-from";
    static final String LANDING_CHANGE = "jar.landing-change";
    static final String TOOL_PATH = "tool.path";
    static final String HEADER = """
            The landing queue's config, beside its jar: Java properties, written by landing install alone - every key kept,
            a path's colon and backslashes escaped as the format has them; jar.* and tool.path set at each install. A value
            edited by hand stays. The task is made from it at every install: after a change to run.budget.minutes, or a JDK
            moved or updated, run tools/ops/install-landing.sh again. Under the task the verify builds on the JDK the task
            runs, not a JAVA_HOME of the user's.""";

    private Installer() {
    }

    /**
     * The config as an install leaves it: every key there kept with its value; a seed set only where there is none;
     * jar.built-from, jar.landing-change and tool.path this install's.
     */
    static Properties merged(Properties existing, Map<String, String> seeds, Sha from, Sha change, List<String> toolDirs) {
        Properties merged = new Properties();
        seeds.forEach(merged::setProperty);
        existing.stringPropertyNames().forEach(key -> merged.setProperty(key, existing.getProperty(key)));
        merged.setProperty(BUILT_FROM, from.value());
        merged.setProperty(LANDING_CHANGE, change.value());
        merged.setProperty(TOOL_PATH, String.join(";", toolDirs));
        return merged;
    }

    /**
     * 0 installed; 2 arguments it cannot read; 4 the lock held. A config that would name no repo, or give a run budget
     * with no room for one attempt, throws before anything is made.
     */
    static int install(Path configFile, List<String> args, PrintStream out) {
        if (args.size() < 7) {
            return usage(out);
        }
        Sha from;
        Sha change;
        try {
            from = Sha.parse(args.get(2));
            change = Sha.parse(args.get(3));
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            return ExitCode.USAGE;
        }
        Optional<Map<String, String>> seeds = seeds(args.subList(7, args.size()), out);
        if (seeds.isEmpty()) {
            return usage(out);
        }
        Path built = Path.of(args.get(0));
        Path installed = Path.of(args.get(1));
        try {
            Properties existing = new Properties();
            if (Files.exists(configFile)) {
                try (Reader in = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                    existing.load(in);
                }
            }
            Properties merged = merged(existing, seeds.get(), from, change, args.subList(4, 7));
            Path home = configFile.toAbsolutePath().getParent();
            Config config = Config.of(merged, home);
            Files.createDirectories(home);
            Optional<IntegratorLock.Held> held = new IntegratorLock(config.lock()).tryAcquire();
            if (held.isEmpty()) {
                out.println("an integrator holds " + config.lock() + ": install again when it is done");
                return ExitCode.HALTED;
            }
            try (IntegratorLock.Held h = held.get()) {
                Files.createDirectories(installed.toAbsolutePath().getParent());
                Path copy = installed.resolveSibling(installed.getFileName() + ".new");
                Files.copy(built, copy, StandardCopyOption.REPLACE_EXISTING);
                Files.move(copy, installed, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                Path next = configFile.resolveSibling(configFile.getFileName() + ".new");
                try (Writer write = Files.newBufferedWriter(next, StandardCharsets.UTF_8)) {
                    merged.store(write, HEADER);
                }
                Files.move(next, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                out.println("installed " + installed + ", built from " + from + ", its last landing/ change " + change);
                return ExitCode.OK;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("landing not installed at " + installed, e);
        }
    }

    /** The seeds {@code key=value}, each key once and never empty; else none, said. */
    private static Optional<Map<String, String>> seeds(List<String> given, PrintStream out) {
        Map<String, String> seeds = new LinkedHashMap<>();
        for (String seed : given) {
            int eq = seed.indexOf('=');
            if (eq <= 0 || seeds.putIfAbsent(seed.substring(0, eq), seed.substring(eq + 1)) != null) {
                out.println("a seed is <key>=<value>, each key once: " + seed);
                return Optional.empty();
            }
        }
        return Optional.of(seeds);
    }

    private static int usage(PrintStream out) {
        out.println("usage: landing install <built jar> <installed jar> <built-from sha> <landing change sha> <git dir>"
                + " <sh dir> <mvn dir> [<key>=<value> ...]");
        return ExitCode.USAGE;
    }
}
