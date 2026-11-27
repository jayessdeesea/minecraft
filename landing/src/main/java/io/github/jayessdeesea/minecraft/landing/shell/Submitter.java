package io.github.jayessdeesea.minecraft.landing.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.landing.EntryId;
import io.github.jayessdeesea.minecraft.landing.EntryState;
import io.github.jayessdeesea.minecraft.landing.Entries;
import io.github.jayessdeesea.minecraft.landing.ExitCode;
import io.github.jayessdeesea.minecraft.landing.Integrator;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Landings;
import io.github.jayessdeesea.minecraft.landing.Sha;
import io.github.jayessdeesea.minecraft.landing.Snapshot;
import io.github.jayessdeesea.minecraft.landing.Step;
import io.github.jayessdeesea.minecraft.landing.Waiting;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * {@code landing land <message file>}, from a writer's worktree: 1 commit the tracked
 * changes; 2 if its last submission landed and the branch was left where it was, replay only what is new onto the
 * landed commit; 3 squash against the merge-base with main, with its trailers; 4 create the request ref, poke the
 * integrator and wait on the entry's state; 5 landed, move the branch to it; 6 not landed, the commit stays. The writer's
 * last submission is kept in its worktree's git-dir as {@code landing.json}. A stream has one landing in flight: while
 * the last submission is unsettled, {@code land} takes up waiting on it again, squashing nothing - new commits wait for
 * the land after it.
 */
public final class Submitter {

    private static final int MOST_ID_TRIES = 5;
    private static final Duration REPOKE = Duration.ofMinutes(1);

    /** A writer's last submission: its id, the head it was squashed from and the base it was squashed onto. */
    record LastSubmission(String id, String source, String base) {
    }

    private final Git git;
    private final Gather gather;
    private final String remote;
    private final Journal journal;
    private final Poke poke;
    private final Sleeper sleeper;
    private final Clock clock;
    private final RandomGenerator random;
    private final Duration poll;
    private final PrintStream out;
    private final WatchFiles progress;
    private final Duration feedback;
    private final ObjectMapper json = new ObjectMapper();

    public Submitter(Git git, String remote, Journal journal, Poke poke, Sleeper sleeper, Clock clock, RandomGenerator random,
                     Duration poll, PrintStream out, WatchFiles progress, Duration feedback) {
        this.git = git;
        this.gather = new Gather(git, remote, clock);
        this.remote = remote;
        this.journal = journal;
        this.poke = poke;
        this.sleeper = sleeper;
        this.clock = clock;
        this.random = random;
        this.poll = poll;
        this.out = out;
        this.progress = progress;
        this.feedback = feedback;
    }

    /** Submits, pokes and waits, up to {@code timeout} if given: the exit of {@link ExitCode}. */
    public int land(Path messageFile, Optional<Duration> timeout) {
        String message;
        try {
            message = Files.readString(messageFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("no message file readable at " + messageFile);
            return ExitCode.USAGE;
        }
        if (message.isBlank()) {
            out.println("the message file " + messageFile + " is empty");
            return ExitCode.USAGE;
        }
        // main into this worktree's own FETCH_HEAD: the shared remote-tracking ref has one writer, the lock's holder
        Optional<Sha> fetched = fetchMain();
        if (fetched.isEmpty()) {
            out.println("the fetch failed: nothing submitted, nothing committed");
            return ExitCode.LANDING_FAILED;
        }
        Sha tip = fetched.get();
        Landings landings = gather.landings(tip);
        Optional<LastSubmission> last = lastSubmission();
        if (last.isPresent()) {
            EntryId inFlight = EntryId.parse(last.get().id());
            if (git.resolve(Gather.QUEUE + inFlight).isPresent()
                    && !Entries.state(inFlight, journal.read(), landings).settled()) {
                out.println("waiting again on " + inFlight + ", still in flight; new commits wait for the next land");
                poke.poke();
                return await(inFlight, Sha.parse(last.get().source()), timeout);
            }
        }
        git.ok("add", "-u");
        if (git.run(List.of("diff", "--cached", "--quiet")).exit() == 1) {
            git.ok("commit", "-q", "-F", messageFile.toAbsolutePath().toString());
        }
        if (!replay(last, landings)) {
            return ExitCode.BOUNCED;
        }
        Sha head = git.resolve("HEAD").orElseThrow();
        Sha base = Sha.parse(git.ok("merge-base", head.value(), tip.value()).strip());
        if (git.tree(head).equals(git.tree(base))) {
            out.println("nothing to land: " + head + " changes nothing on main");
            return ExitCode.USAGE;
        }
        EntryId id = queue(message, head, base);
        out.println("queued " + id);
        poke.poke();
        return await(id, head, timeout);
    }

    /**
     * Step 2: the last submission landed as L from source S, and L is not in this branch - the branch was left at S, so
     * what is new since S is replayed onto L. A conflict sends it back (exit 3), the rebase undone; so does a branch that
     * no longer holds S, which only its writer can say how to put on L.
     */
    private boolean replay(Optional<LastSubmission> last, Landings landings) {
        if (last.isEmpty()) {
            return true;
        }
        Optional<Sha> landed = landings.of(EntryId.parse(last.get().id()));
        Sha head = git.resolve("HEAD").orElseThrow();
        Sha source = Sha.parse(last.get().source());
        if (landed.isEmpty() || git.isAncestor(landed.get(), head)) {
            return true;
        }
        if (!git.isAncestor(source, head)) {
            out.println("the landed source " + source + " is not in HEAD's history: rebase onto " + landed.get());
            return false;
        }
        if (!git.run(List.of("rebase", "-q", "--onto", landed.get().value(), source.value())).ok()) {
            git.run(List.of("rebase", "--abort"));
            out.println("replaying onto the landed " + landed.get() + " conflicts: rebase onto origin/main by hand");
            return false;
        }
        out.println("replayed onto the landed " + landed.get() + " only what is new since " + source);
        return true;
    }

    /** Step 3 and 4: the squash, with its trailers, and the request ref made create-only. */
    private EntryId queue(String message, Sha head, Sha base) {
        String by = git.ok("rev-parse", "--show-toplevel").strip();
        for (int tries = 0; tries < MOST_ID_TRIES; tries++) {
            EntryId id = EntryId.next(clock, random);
            // ended by a newline, and no "---" line taken for a patch's divider: the trailers go at the message's end
            Git.Result trailed = git.run(List.of("interpret-trailers", "--no-divider", "--trailer", "Landing-Id: " + id,
                    "--trailer", "Landing-Source: " + head, "--trailer", "Landing-By: " + Path.of(by).getFileName()),
                    message.endsWith("\n") ? message : message + "\n");
            if (!trailed.ok()) {
                throw new Git.Failed("git interpret-trailers exited " + trailed.exit() + ": " + trailed.err().strip());
            }
            Git.Result squash = git.run(List.of("commit-tree", git.tree(head).value(), "-p", base.value(), "-F", "-"),
                    trailed.out());
            if (!squash.ok()) {
                throw new Git.Failed("git commit-tree exited " + squash.exit() + ": " + squash.err().strip());
            }
            if (git.createRef(Gather.QUEUE + id, Sha.parse(squash.line()))) {
                writeLast(new LastSubmission(id.value(), head.value(), base.value()));
                return id;
            }
        }
        throw new IllegalStateException(MOST_ID_TRIES + " ids drawn and each already queued");
    }

    /**
     * Waits on the entry: its state, from the journal and main, until settled; halted, exit 4; past the timeout, exit 5.
     * The integrator is poked again each minute while it waits. Where the entry is - who is ahead, the module and test
     * classes done, how old the last output is - is said when that changes and every {@code feedback} otherwise. Main is read as the integrator last fetched it into the
     * shared repository - a waiter's own fetch every look would contend with the integrator's for the ref's lock.
     */
    public int await(EntryId id, Sha source, Optional<Duration> timeout) {
        Instant deadline = timeout.map(clock.instant()::plus).orElse(Instant.MAX);
        Instant poked = clock.instant();
        Optional<Waiting.Said> said = Optional.empty();
        Instant saidAt = clock.instant();
        while (true) {
            List<JournalRecord> records = journal.read();
            Landings landings = gather.landings(gather.mainTip());
            boolean looking = !clock.instant().isBefore(poked.plus(REPOKE)) || !clock.instant().isBefore(deadline);
            if (looking && Entries.pushed(id, records)) {
                // verified, maybe pushed, and the integrator's view not saying so: it may be its fetch that fails
                Optional<Sha> own = fetchMain();
                if (own.isPresent()) {
                    landings = gather.landings(own.get());
                }
            }
            EntryState state = Entries.state(id, records, landings);
            boolean halted = Integrator.halted(records).isPresent();
            if (state.settled()) {
                return said(id, state, records, source);
            }
            if (halted) {
                out.println(id + " waits on a halted queue: " + Integrator.halted(records).get().reason());
                return ExitCode.of(state, true);
            }
            if (!clock.instant().isBefore(deadline)) {
                out.println(id + " still queued: " + state);
                return ExitCode.of(state, false);
            }
            Waiting.Said line = where(id, state, records);
            if (Waiting.due(said, saidAt, line, clock.instant(), feedback)) {
                out.println(line.text());
                said = Optional.of(line);
                saidAt = clock.instant();
            }
            if (!clock.instant().isBefore(poked.plus(REPOKE))) {
                poke.poke();
                poked = clock.instant();
            }
            sleeper.sleep(poll);
        }
    }

    /**
     * Where the entry is, in a waiter's words ({@link Waiting#line}): the queue's refs and the integrator's next step are
     * read only while it is queued; the running verify from the progress file its integrator writes.
     */
    Waiting.Said where(EntryId id, EntryState state, List<JournalRecord> records) {
        Optional<Step> next = Optional.empty();
        List<EntryId> ahead = List.of();
        if (state instanceof EntryState.Queued) {
            Snapshot snapshot = gather.snapshot(records);
            next = Optional.of(Integrator.next(snapshot));
            ahead = snapshot.queued().stream().filter(q -> q.compareTo(id) < 0).sorted().toList();
        }
        return Waiting.line(id, state, records, progress.readProgress(), next, ahead, clock.instant());
    }

    /** Main as this worktree fetches it into its own FETCH_HEAD, the shared ref being the integrator's; none if it fails. */
    private Optional<Sha> fetchMain() {
        if (!git.remote().run(List.of("fetch", "-q", "--refmap=", remote, "refs/heads/main")).ok()) {
            return Optional.empty();
        }
        return git.resolve("FETCH_HEAD");
    }

    /**
     * Says how the entry settled, and its exit. Landed, 0 only when the worktree holds nothing but what landed - HEAD
     * the source, the tracked tree clean - and the branch is moved to the landing; else 5, land again, the branch left.
     */
    private int said(EntryId id, EntryState state, List<JournalRecord> records, Sha source) {
        boolean more = false;
        switch (state) {
            case EntryState.Landed landed -> {
                more = !git.resolve("HEAD").orElseThrow().equals(source)
                        || !git.ok("status", "--porcelain", "--untracked-files=no").isBlank()
                        || !git.run(List.of("reset", "-q", "--keep", landed.sha().value())).ok();
                out.println(more ? id + " landed as " + landed.sha() + "; this worktree holds more since " + source
                        + ": land again" : id + " landed as " + landed.sha());
            }
            case EntryState.Bounced bounced -> records.stream()
                    .filter(r -> r instanceof JournalRecord.Bounced b && b.id().equals(id)).findFirst()
                    .ifPresent(r -> out.println(id + " bounced: " + bounced.reason() + " " + ((JournalRecord.Bounced) r).named()));
            case EntryState.Failed failed -> out.println(id + " failed: " + failed.reason() + "; the commit stays");
            default -> out.println(id + ": " + state);
        }
        return ExitCode.of(state, false, more);
    }

    private Path lastFile() {
        return Path.of(git.ok("rev-parse", "--absolute-git-dir").strip()).resolve("landing.json");
    }

    private Optional<LastSubmission> lastSubmission() {
        Path file = lastFile();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(file.toFile(), LastSubmission.class));
        } catch (IOException e) {
            throw new UncheckedIOException("the last submission " + file + " unreadable", e);
        }
    }

    private void writeLast(LastSubmission last) {
        try {
            json.writeValue(lastFile().toFile(), last);
        } catch (IOException e) {
            throw new UncheckedIOException("the last submission not kept", e);
        }
    }
}
