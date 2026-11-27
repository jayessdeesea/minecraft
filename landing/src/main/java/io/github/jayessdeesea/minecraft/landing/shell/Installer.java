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
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * {@code landing install <built jar> <installed jar> <built-from> <landing change> <git dir> <sh dir> <mvn dir>}, run by
 * install-landing.sh from the jar it has just built: under the integrator's lock, so no integrator runs from the jar
 * while it is replaced, the jar is copied to its installed place and the config records the commit it was built from,
 * the last change to {@code landing/} there - what the audit's I8 compares with main - and where git, sh and mvn are
 * ({@code tool.path}), which a scheduled task's PATH does not say.
 */
final class Installer {

    static final String BUILT_FROM = "jar.built-from";
    static final String LANDING_CHANGE = "jar.landing-change";
    static final String TOOL_PATH = "tool.path";

    private Installer() {
    }

    /** 0 installed; 2 arguments it cannot read; 4 the lock held. */
    static int install(Config config, Path configFile, List<String> args, PrintStream out) {
        if (args.size() != 7) {
            out.println("usage: landing install <built jar> <installed jar> <built-from sha> <landing change sha> <git dir>"
                    + " <sh dir> <mvn dir>");
            return ExitCode.USAGE;
        }
        Path built = Path.of(args.get(0));
        Path installed = Path.of(args.get(1));
        Sha from;
        Sha change;
        try {
            from = Sha.parse(args.get(2));
            change = Sha.parse(args.get(3));
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            return ExitCode.USAGE;
        }
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
            Properties properties = new Properties();
            try (Reader in = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                properties.load(in);
            }
            properties.setProperty(BUILT_FROM, from.value());
            properties.setProperty(LANDING_CHANGE, change.value());
            properties.setProperty(TOOL_PATH, String.join(";", args.subList(4, 7)));
            Path next = configFile.resolveSibling(configFile.getFileName() + ".new");
            try (Writer write = Files.newBufferedWriter(next, StandardCharsets.UTF_8)) {
                properties.store(write, "the landing queue's config; jar.* written by landing install");
            }
            Files.move(next, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            out.println("installed " + installed + ", built from " + from + ", its last landing/ change " + change);
            return ExitCode.OK;
        } catch (IOException e) {
            throw new UncheckedIOException("landing not installed at " + installed, e);
        }
    }
}
