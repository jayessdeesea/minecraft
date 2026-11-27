package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Sha;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Where the queue's things are and how it runs, read once by {@code main()} from {@code C:/mc-state/landing/config}
 * (a Java properties file; {@code LANDING_CONFIG} names another). Every path can be set; only {@code repo} must be.
 * A command is its words split on white space, so no word of it holds a space.
 *
 * @param repo             a path in the shared repository: the main checkout the landing worktree is added from
 * @param remote           the remote main is fetched from and pushed to
 * @param worktree         the landing worktree, the integrator's alone
 * @param journal          the journal, kept whole
 * @param lock             the integrator's lock file, never deleted
 * @param verify           the verify command, run in the landing worktree with {@code VERIFY_LOG} set
 * @param verifyTimeout    past this the verify's process tree is killed
 * @param verifyLog        where the verify writes Maven's whole output
 * @param poke             the command that wakes the integrator now, as the scheduler's task, outside this process: one
 *                         that starts the integrator as its own child is wrong - it would die in its submitter's job
 * @param poll             how often a waiter looks
 * @param hook             the pre-push hook as installed, if it is
 * @param hookHash         the hook's SHA-256 when installed (empty before)
 * @param jarLandingChange the last commit touching {@code landing/} as of the commit the jar was built from
 * @param committerName    the integrator's name on the candidates it makes
 * @param committerEmail   and its address
 * @param gitTimeout       how long a local git call may take before its process tree is killed
 * @param gitRemoteTimeout how long a fetch or a push may take
 */
public record Config(Path repo, String remote, Path worktree, Path journal, Path lock, List<String> verify,
                     Duration verifyTimeout, Path verifyLog, List<String> poke, Duration poll, Optional<Path> hook,
                     String hookHash, Optional<Sha> jarLandingChange, String committerName, String committerEmail,
                     Duration gitTimeout, Duration gitRemoteTimeout) {

    /** The config's home when {@code LANDING_CONFIG} names none. */
    public static final Path DEFAULT = Path.of("C:/mc-state/landing/config");
    private static final String STATE = "C:/mc-state/landing/";

    public Config {
        verify = List.copyOf(verify);
        poke = List.copyOf(poke);
    }

    public static Config load(Path file) {
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("no landing config readable at " + file, e);
        }
        return of(properties);
    }

    public static Config of(Properties p) {
        String repo = p.getProperty("repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("the landing config names no repo");
        }
        return new Config(Path.of(repo), p.getProperty("remote", "origin"),
                Path.of(p.getProperty("worktree", "C:/mc-worktrees/landing")),
                Path.of(p.getProperty("journal", STATE + "journal.jsonl")),
                Path.of(p.getProperty("lock", STATE + "integrator.lock")),
                words(p.getProperty("verify", "sh tools/ops/verify.sh")),
                Duration.ofMinutes(Long.parseLong(p.getProperty("verify.timeout.minutes", "45"))),
                Path.of(p.getProperty("verify.log", STATE + "verify.log")),
                words(p.getProperty("poke", "schtasks /run /tn mc-landing")),
                Duration.ofSeconds(Long.parseLong(p.getProperty("poll.seconds", "15"))),
                Optional.ofNullable(p.getProperty("hook")).map(Path::of),
                p.getProperty("hook.hash", ""),
                Optional.ofNullable(p.getProperty("jar.landing-change")).map(Sha::parse),
                p.getProperty("committer.name", "landing"),
                p.getProperty("committer.email", "landing@localhost"),
                Duration.ofMinutes(Long.parseLong(p.getProperty("git.timeout.minutes", "2"))),
                Duration.ofMinutes(Long.parseLong(p.getProperty("git.remote-timeout.minutes", "10"))));
    }

    private static List<String> words(String command) {
        return Arrays.stream(command.trim().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }
}
