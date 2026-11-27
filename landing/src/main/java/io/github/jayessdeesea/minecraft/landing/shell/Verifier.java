package io.github.jayessdeesea.minecraft.landing.shell;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The verify: the configured command, run in the landing worktree with {@code VERIFY_LOG} naming where Maven's whole
 * output goes. What it prints is nobody's - the outcome is the log's lines and its exit. Past its time everything it
 * started is ended by {@link Children}; a tree not proven gone throws {@link Children.NotGone}. A verify outliving a
 * killed integrator cannot be: the integrator's job ends it with the integrator. A verify that never ran throws
 * {@link NoRun}, saying at which phase.
 */
public final class Verifier {

    private final List<String> command;
    private final Duration timeout;
    private final Path log;
    private final Map<String, String> env;
    private final Children children;

    public Verifier(List<String> command, Duration timeout, Path log, Map<String, String> env, Children children) {
        this.command = List.copyOf(command);
        this.timeout = timeout;
        this.log = log;
        this.env = Map.copyOf(env);
        this.children = children;
    }

    /** The log's lines, the exit (-1 if ended at its time) and whether it ran past its time. */
    public record Run(List<String> lines, int exit, boolean timedOut) {
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

    /** A verify that gave no outcome, and the phase it failed at. */
    public static final class NoRun extends RuntimeException {
        private final Phase phase;

        NoRun(Phase phase, Throwable cause) {
            super(phase.words() + ": " + cause.getMessage(), cause);
            this.phase = phase;
        }

        public Phase phase() {
            return phase;
        }
    }

    public Run run(Path worktree) {
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
        try {
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                if (!children.kill(p.toHandle())) {
                    throw new Children.NotGone("the verify " + command + " past its " + timeout
                            + ": its processes not proven gone");
                }
                return new Run(List.of(), -1, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during the verify", e);
        }
        try {
            List<String> lines = Files.exists(log)
                    ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList() : List.of();
            return new Run(lines, p.exitValue(), false);
        } catch (IOException e) {
            throw new NoRun(Phase.LOG_NOT_READ, e);
        }
    }
}
