package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Integrator;
import io.github.jayessdeesea.minecraft.landing.Sha;
import io.github.jayessdeesea.minecraft.landing.VerifyWatch;

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
 * Where the queue's things are and how it runs, read once by {@code main()}: a Java properties file, {@code config}
 * beside the jar the tool runs from, or the one {@code LANDING_CONFIG} names ({@link #file}). Every path can be set; only
 * {@code repo} must be. A command is its words split on white space, so no word of it holds a space. The task is made
 * from it at every install, so a changed {@code run.budget.minutes} or a moved JDK takes at the next install.
 *
 * @param repo             a path in the shared repository: the main checkout the landing worktree is added from
 * @param remote           the remote main is fetched from and pushed to
 * @param worktree         the landing worktree, the integrator's alone
 * @param journal          the journal, kept whole
 * @param lock             the integrator's lock file, never deleted
 * @param verify           the verify command, run in the landing worktree with {@code VERIFY_LOG} set
 * @param verifyLimits     the verify's watchdog: quiet for {@code verify.stall.minutes} (15) ends it, a look each
 *                         {@code verify.look.seconds} (10); its cap is {@code verify.timeout.minutes} (90), a floor that
 *                         twice the expected total raises ({@link Integrator#cap}, at each attempt)
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
 * @param runBudget        how long one integrator run may go on starting attempts; the task's time limit is past it
 * @param toolPath         where git, sh and mvn are ({@code tool.path}, written by {@code landing install}): a scheduled
 *                         task's PATH has none of them; empty, the inherited PATH finds them
 * @param verifyProgress   the running verify's progress for waiters ({@code verify.progress}), there only while one runs
 * @param verifyHistory    the last five verifies that passed ({@code verify.history}), for the estimate and the cap
 * @param feedback         how often a waiter says where its entry is when nothing has changed ({@code feedback.seconds})
 */
public record Config(Path repo, String remote, Path worktree, Path journal, Path lock, List<String> verify,
                     VerifyWatch.Limits verifyLimits, Path verifyLog, List<String> poke, Duration poll, Optional<Path> hook,
                     String hookHash, Optional<Sha> jarLandingChange, String committerName, String committerEmail,
                     Duration gitTimeout, Duration gitRemoteTimeout, Duration runBudget, List<Path> toolPath, Path verifyProgress,
                     Path verifyHistory, Duration feedback) {

    public Config {
        verify = List.copyOf(verify);
        poke = List.copyOf(poke);
        toolPath = List.copyOf(toolPath);
    }

    /**
     * The config this process reads: {@code LANDING_CONFIG}'s file if set; else {@code config} beside the jar it runs
     * from - the installed tool's home; else there is none, and that is landing itself failing.
     */
    public static Path file(Optional<String> env, Optional<Path> jar) {
        return env.filter(e -> !e.isBlank()).map(Path::of)
                .or(() -> jar.map(j -> j.resolveSibling("config")))
                .orElseThrow(() -> new IllegalStateException("no config named: LANDING_CONFIG is not set, and no jar to find "
                        + "one beside"));
    }

    public static Config load(Path file) {
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("no landing config readable at " + file, e);
        }
        return of(properties, file.toAbsolutePath().getParent());
    }

    /**
     * The config; refused if it names no repo, or gives a run budget with no room for one attempt. The journal, the lock
     * and the verify's log are in {@code home}, the config file's own directory, unless named: home is one place.
     */
    public static Config of(Properties p, Path home) {
        String repo = p.getProperty("repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("the landing config names no repo");
        }
        VerifyWatch.Limits limits = new VerifyWatch.Limits(
                Duration.ofMinutes(Long.parseLong(p.getProperty("verify.stall.minutes", "15"))),
                Duration.ofMinutes(Long.parseLong(p.getProperty("verify.timeout.minutes", "90"))),
                Duration.ofSeconds(Long.parseLong(p.getProperty("verify.look.seconds", "10"))));
        Duration remote = Duration.ofMinutes(Long.parseLong(p.getProperty("git.remote-timeout.minutes", "10")));
        Duration budget = Duration.ofMinutes(Long.parseLong(p.getProperty("run.budget.minutes", "180")));
        // the cap's floor must fit: a larger cap from the history is held to what is left at the attempt (Integrator.cap)
        Duration least = Integrator.leastBudget(limits.cap(), remote);
        if (budget.compareTo(least) < 0) {
            throw new IllegalArgumentException("run.budget.minutes " + budget.toMinutes() + " has no room for one attempt, "
                    + "which takes up to " + least + ": every start would be refused at its first attempt");
        }
        return new Config(Path.of(repo), p.getProperty("remote", "origin"),
                Path.of(p.getProperty("worktree", "C:/mc-worktrees/landing")),
                p.getProperty("journal") == null ? home.resolve("journal.jsonl") : Path.of(p.getProperty("journal")),
                p.getProperty("lock") == null ? home.resolve("integrator.lock") : Path.of(p.getProperty("lock")),
                words(p.getProperty("verify", "sh tools/ops/verify.sh")),
                limits,
                p.getProperty("verify.log") == null ? home.resolve("verify.log") : Path.of(p.getProperty("verify.log")),
                words(p.getProperty("poke", "schtasks /run /tn mc-landing")),
                Duration.ofSeconds(Long.parseLong(p.getProperty("poll.seconds", "15"))),
                Optional.ofNullable(p.getProperty("hook")).map(Path::of),
                p.getProperty("hook.hash", ""),
                Optional.ofNullable(p.getProperty("jar.landing-change")).map(Sha::parse),
                p.getProperty("committer.name", "landing"),
                p.getProperty("committer.email", "landing@localhost"),
                Duration.ofMinutes(Long.parseLong(p.getProperty("git.timeout.minutes", "2"))),
                remote,
                budget,
                Arrays.stream(p.getProperty("tool.path", "").split(";")).filter(d -> !d.isBlank()).map(Path::of).toList(),
                p.getProperty("verify.progress") == null ? home.resolve("verify-progress.json")
                        : Path.of(p.getProperty("verify.progress")),
                p.getProperty("verify.history") == null ? home.resolve("verify-history.json")
                        : Path.of(p.getProperty("verify.history")),
                Duration.ofSeconds(Long.parseLong(p.getProperty("feedback.seconds", "60"))));
    }

    /** The cap's floor, {@code verify.timeout.minutes}: what a verify may run with no history to raise it. */
    public Duration verifyTimeout() {
        return verifyLimits.cap();
    }

    private static List<String> words(String command) {
        return Arrays.stream(command.trim().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }
}
