package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.MavenProgress;
import io.github.jayessdeesea.minecraft.landing.VerifyEnd;
import io.github.jayessdeesea.minecraft.landing.VerifyWatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The verify: the configured command, run in the landing worktree with {@code VERIFY_LOG} naming where Maven's whole
 * output goes. What it prints is nobody's - the outcome is the log's lines and its exit. The event waited for is the
 * process's exit, and it alone gives an outcome; each {@code look} the watchdog reads the log's size, and a verify that
 * wrote nothing for the stall limit, or ran its cap, is ended by {@link Children} - its log moved to
 * {@code verify.killed.log} first, so the next attempt's clearing keeps the evidence. A tree not proven gone throws
 * {@link NotGone}, naming how it was ended. A verify outliving a killed integrator cannot be: the integrator's job ends
 * it with the integrator. A verify that never ran throws {@link NoRun}, saying at which phase.
 */
public final class Verifier {

    private final List<String> command;
    private final VerifyWatch.Limits limits;
    private final Path log;
    private final Map<String, String> env;
    private final Children children;
    private final Clock clock;

    public Verifier(List<String> command, VerifyWatch.Limits limits, Path log, Map<String, String> env, Children children,
                    Clock clock) {
        this.command = List.copyOf(command);
        this.limits = limits;
        this.log = log;
        this.env = Map.copyOf(env);
        this.children = children;
        this.clock = clock;
    }

    /**
     * The log's lines (none if it was ended), how it ended, how long it took and its longest quiet, both as the watchdog
     * watched them.
     */
    public record Run(List<String> lines, VerifyEnd end, Duration took, Duration longestQuiet) {
    }

    /** Where a verify that gave no outcome failed - each the integrator's, in words of its own. */
    public enum Phase {
        LOG_NOT_CLEARED("the verify's log could not be cleared"),
        NOT_STARTED("the verify did not start"),
        LOG_NOT_READ("the verify's log could not be read");

        private final String words;

        Phase(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /** How long a verify that ran went, and its longest quiet, as the watchdog watched them. */
    public record Watched(Duration took, Duration longestQuiet) {
    }

    /** A verify that gave no outcome, the phase it failed at, and - if it ran - how long it went. */
    public static final class NoRun extends RuntimeException {
        private final Phase phase;
        private final transient Optional<Watched> watched;

        NoRun(Phase phase, Throwable cause) {
            this(phase, cause, Optional.empty());
        }

        NoRun(Phase phase, Throwable cause, Optional<Watched> watched) {
            super(phase.words() + ": " + cause.getMessage(), cause);
            this.phase = phase;
            this.watched = watched;
        }

        public Phase phase() {
            return phase;
        }

        public Optional<Watched> watched() {
            return watched;
        }
    }

    /** A verify the watchdog ended whose tree was not proven gone: {@code end} says how, {@code watched} how long. */
    public static final class NotGone extends Children.NotGone {
        private final transient VerifyEnd end;
        private final transient Watched watched;

        NotGone(VerifyEnd end, Watched watched, String message) {
            super(message);
            this.end = end;
            this.watched = watched;
        }

        public VerifyEnd end() {
            return end;
        }

        public Watched watched() {
            return watched;
        }
    }

    /** Where an ended verify's log is kept, beside the log: one file, replaced. */
    public Path killedLog() {
        String name = log.getFileName().toString();
        return log.resolveSibling((name.endsWith(".log") ? name.substring(0, name.length() - 4) : name) + ".killed.log");
    }

    public Run run(Path worktree) {
        return run(worktree, limits.cap(), w -> {
        });
    }

    /** The verify, capped at {@code cap}; {@code looked} is told each watch that found it going. */
    public Run run(Path worktree, Duration cap, Consumer<VerifyWatch> looked) {
        VerifyWatch.Limits watch = limits.withCap(cap);
        try {
            Files.createDirectories(log.toAbsolutePath().getParent());
            Files.deleteIfExists(log);
        } catch (IOException e) {
            throw new NoRun(Phase.LOG_NOT_CLEARED, e);
        }
        ProcessBuilder builder = new ProcessBuilder(command).directory(worktree.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Programs.set(builder.environment(), env);
        // forward slashes: one path both Windows and the verify's sh read alike
        builder.environment().put("VERIFY_LOG", log.toAbsolutePath().toString().replace('\\', '/'));
        Process p;
        try {
            p = builder.start();
            p.getOutputStream().close();
        } catch (IOException e) {
            throw new NoRun(Phase.NOT_STARTED, e);
        }
        VerifyWatch w = VerifyWatch.start(clock.instant());
        try {
            while (!p.waitFor(watch.look().toMillis(), TimeUnit.MILLISECONDS)) {
                switch (VerifyWatch.look(w, size(), clock.instant(), watch)) {
                    case VerifyWatch.Verdict.Going g -> {
                        w = g.next();
                        looked.accept(w);
                    }
                    case VerifyWatch.Verdict.Stalled s -> {
                        return ended(p, new VerifyEnd.Stalled(s.quiet(), where()), s.at());
                    }
                    case VerifyWatch.Verdict.Capped c -> {
                        return ended(p, new VerifyEnd.Capped(c.ran(), where()), c.at());
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during the verify", e);
        }
        Watched watched = new Watched(Duration.between(w.started(), clock.instant()), w.longestQuiet());
        try {
            List<String> lines = Files.exists(log)
                    ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList() : List.of();
            return new Run(lines, new VerifyEnd.Exited(p.exitValue()), watched.took(), watched.longestQuiet());
        } catch (IOException e) {
            throw new NoRun(Phase.LOG_NOT_READ, e, Optional.of(watched));
        }
    }

    /** Ended by the watchdog: the log kept aside, then the tree killed - not proven gone, {@link NotGone}. */
    private Run ended(Process p, VerifyEnd end, VerifyWatch at) {
        try {
            if (Files.exists(log)) {
                Files.move(log, killedLog(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // a witness: the kill goes on, the log left where the next attempt clears it
        }
        Watched watched = new Watched(Duration.between(at.started(), at.lastLook()), at.longestQuiet());
        if (!children.kill(p.toHandle())) {
            throw new NotGone(end, watched, "the verify " + command + " ended (" + end + "): its processes not proven gone");
        }
        return new Run(List.of(), end, watched.took(), watched.longestQuiet());
    }

    private java.util.OptionalLong size() {
        return size(log);
    }

    /**
     * A log's size now, one read; none while it is not there or cannot be read - the watch keeps the last size, which
     * starts at 0, so a log not there yet reads as before, one gone and back at its size is no output, and one gone or
     * unreadable for good ends as a stall, said.
     */
    static java.util.OptionalLong size(Path log) {
        try {
            return java.util.OptionalLong.of(Files.size(log));
        } catch (IOException e) {
            return java.util.OptionalLong.empty();
        }
    }

    /** Where the build was, read from the log for the fault's words - display only. */
    private String where() {
        return MavenProgress.of(lines(log)).words();
    }

    /** A log's lines as far as it can be read: none if it is not there or cannot be read - for display only. */
    static List<String> lines(Path file) {
        try {
            return Files.exists(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList() : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }
}
