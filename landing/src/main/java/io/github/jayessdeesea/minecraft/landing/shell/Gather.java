package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Audit;
import io.github.jayessdeesea.minecraft.landing.Commit;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Landings;
import io.github.jayessdeesea.minecraft.landing.Main;
import io.github.jayessdeesea.minecraft.landing.Sha;
import io.github.jayessdeesea.minecraft.landing.Snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the pure core decides over, gathered from git and the journal: the integrator's snapshot after a fetch, and the
 * audit's input. Every part of one gathering is read from the same main tip, so they agree.
 */
public final class Gather {

    static final String QUEUE = "refs/queue/";
    static final String WITHDRAW = "refs/queue-withdraw/";

    private final Git git;
    private final String remote;
    private final Clock clock;

    public Gather(Git git, String remote, Clock clock) {
        this.git = git;
        this.remote = remote;
        this.clock = clock;
    }

    /** Main as last fetched. */
    public Sha mainTip() {
        return git.resolve("refs/remotes/" + remote + "/main")
                .orElseThrow(() -> new Git.Failed("no refs/remotes/" + remote + "/main: never fetched"));
    }

    /** Where each entry landed: a trailer scan of all of main's history at {@code tip}. */
    public Landings landings(Sha tip) {
        return Landings.of(git.log(tip.value()));
    }

    /** The integrator's snapshot; only after a fetch that worked. */
    public Snapshot snapshot(List<JournalRecord> journal) {
        return new Snapshot(journal, landings(mainTip()), git.refs(QUEUE), git.refs(WITHDRAW), clock.instant());
    }

    /** The audit's input: main since the journal's K, the landings, the ancestry and the install, all at one tip. */
    public Audit.Input auditInput(List<JournalRecord> journal, boolean noIntegratorMidStep, Config config) {
        Sha tip = mainTip();
        Optional<Sha> k = Audit.cutover(journal);
        List<Commit> sinceK = k.map(c -> git.log(c.value() + ".." + tip.value())).orElse(List.of());
        Set<Audit.Edge> confirmed = new HashSet<>();
        for (Audit.Edge edge : Audit.questions(journal, tip)) {
            if (git.exists(edge.ancestor().value()) && git.exists(edge.descendant().value())
                    && git.isAncestor(edge.ancestor(), edge.descendant())) {
                confirmed.add(edge);
            }
        }
        List<String> strays = new java.util.ArrayList<>(git.strays(QUEUE));
        strays.addAll(git.strays(WITHDRAW));
        return new Audit.Input(journal, new Main(tip, k, sinceK), landings(tip), git.refs(QUEUE), confirmed,
                noIntegratorMidStep, clock.instant(), install(config, tip), strays);
    }

    /** The installed tool as the audit's I8 sees it, main's last change to {@code landing/} at {@code tip}. */
    private Audit.Install install(Config config, Sha tip) {
        Optional<String> hookHash = config.hook().filter(Files::isRegularFile).map(Gather::sha256);
        String last = git.ok("log", "-1", "--format=%H", tip.value(), "--", "landing").strip();
        return new Audit.Install(hookHash, config.hookHash(), config.jarLandingChange(),
                last.isEmpty() ? Optional.empty() : Optional.of(Sha.parse(last)));
    }

    private static String sha256(Path file) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("the hook " + file + " unreadable", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-256 in this JVM", e);
        }
    }
}
