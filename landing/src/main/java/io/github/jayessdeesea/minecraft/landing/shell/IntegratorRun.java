package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Bounce;
import io.github.jayessdeesea.minecraft.landing.Commit;
import io.github.jayessdeesea.minecraft.landing.EntryId;
import io.github.jayessdeesea.minecraft.landing.EntryState;
import io.github.jayessdeesea.minecraft.landing.ExitCode;
import io.github.jayessdeesea.minecraft.landing.FailReason;
import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.Faulted;
import io.github.jayessdeesea.minecraft.landing.Integrator;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.MergeOutcome;
import io.github.jayessdeesea.minecraft.landing.PushOutcome;
import io.github.jayessdeesea.minecraft.landing.RunToken;
import io.github.jayessdeesea.minecraft.landing.Sha;
import io.github.jayessdeesea.minecraft.landing.Stale;
import io.github.jayessdeesea.minecraft.landing.Step;
import io.github.jayessdeesea.minecraft.landing.VerifyOutcome;

import java.io.PrintStream;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * {@code landing integrate}: whoever holds the lock integrates, until the queue is empty, a back-off or a halt
 * (design section 3). Each pass: 1 fetch; 2 the core's next step; for an attempt, 3 the request and stale checks, 4
 * merge-tree and commit-tree into a candidate held as a value, 5 the Attempt journaled, 6 the candidate checked out in
 * the landing worktree and verified, 7 the Verified journaled, 8 the push, 9 a fetch and the push classified by what it
 * found, 10 the request's refs deleted. A crash anywhere is the next run's to reconcile. Audited at the start and the
 * end.
 */
public final class IntegratorRun {

    /** Passes past which a run that has settled nothing is a bug, said loud rather than looped. */
    static final int MOST_PASSES = 1000;

    private final Config config;
    private final Git repo;
    private final Git tree;
    private final Journal journal;
    private final IntegratorLock lock;
    private final Verifier verifier;
    private final Clock clock;
    private final RandomGenerator random;
    private final Steps steps;
    private final PrintStream out;
    private final Gather gather;

    public IntegratorRun(Config config, Git repo, Journal journal, IntegratorLock lock, Verifier verifier, Clock clock,
                         RandomGenerator random, Steps steps, PrintStream out) {
        this.config = config;
        this.repo = repo;
        this.tree = repo.at(config.worktree());
        this.journal = journal;
        this.lock = lock;
        this.verifier = verifier;
        this.clock = clock;
        this.random = random;
        this.steps = steps;
        this.out = out;
        this.gather = new Gather(repo, config.remote(), clock);
    }

    /**
     * 0 the queue empty, backing off, or a failing fetch's or repository's back-off running; 4 the lock held by another,
     * or the queue halted; 6 a git call the pass needed failed (journaled REPO, and Halted if it is the third running)
     * - and a process tree not proven gone throws {@link Children.NotGone}, journaled as the timeout it is.
     */
    public int integrate() {
        Optional<IntegratorLock.Held> held = lock.tryAcquire();
        if (held.isEmpty()) {
            out.println("another integrator holds " + config.lock());
            return ExitCode.HALTED;
        }
        try (IntegratorLock.Held h = held.get()) {
            Optional<JournalRecord.Halted> halted = Integrator.halted(journal.read());
            if (halted.isPresent()) {
                // read before any git call: a halted run writes nothing, however broken the repository
                out.println("halted: " + halted.get().reason());
                return ExitCode.HALTED;
            }
            if (!Integrator.runDue(journal.read(), clock.instant())) {
                out.println("a failing fetch's or repository's back-off is running: nothing tried, nothing written");
                return ExitCode.OK;
            }
            RunToken run = RunToken.next(random);
            EntryId[] working = {null};
            try {
                return passes(run, working);
            } catch (Git.Failed e) {
                fault(run, FaultKind.REPO, working[0], e.getMessage());
                Integrator.haltDue(journal.read()).ifPresent(reason -> {
                    journal.append(new JournalRecord.Halted(run, clock.instant(), reason));
                    out.println("halted: " + reason);
                });
                return ExitCode.LANDING_FAILED;
            } catch (Children.NotGone e) {
                List<JournalRecord> records = journal.read();
                boolean said = !records.isEmpty() && records.getLast() instanceof JournalRecord.Fault f
                        && f.detail().endsWith(e.getMessage());
                if (!said) {
                    fault(run, FaultKind.REPO, working[0], e.getMessage());
                }
                throw e;
            }
        }
    }

    /** The passes of one run, until the queue is empty, a back-off or a halt; {@code working} the entry in hand. */
    private int passes(RunToken run, EntryId[] working) {
        Auditor auditor = new Auditor(gather, journal, config, clock, out);
        boolean audited = false;
        int exit = ExitCode.OK;
        for (int pass = 0; ; pass++) {
            working[0] = null;
            if (pass == MOST_PASSES) {
                throw new IllegalStateException(MOST_PASSES + " passes and the queue not settled: a bug");
            }
            if (!fetch(run, null)) {
                break;
            }
            steps.reached(1);
            if (!audited) {
                ensureWorktree();
                auditor.audit(Optional.of(run), true);
                audited = true;
            }
            noteMain(run);
            Step step = Integrator.next(gather.snapshot(journal.read()));
            steps.reached(2);
            boolean going = switch (step) {
                case Step.Halt halt -> {
                    if (halt.record()) {
                        journal.append(new JournalRecord.Halted(run, clock.instant(), halt.reason()));
                    }
                    out.println("halted: " + halt.reason());
                    exit = ExitCode.HALTED;
                    yield false;
                }
                case Step.Idle idle -> {
                    out.println(idle.until().map(t -> "backing off until " + t).orElse("the queue is empty"));
                    yield false;
                }
                case Step.Settle settle -> {
                    working[0] = settle.id();
                    settle(run, settle);
                    yield true;
                }
                case Step.Reconcile again -> {
                    working[0] = again.id();
                    attempt(run, again.id());
                    yield true;
                }
                case Step.Attempt attempt -> {
                    working[0] = attempt.id();
                    attempt(run, attempt.id());
                    yield true;
                }
            };
            if (!going) {
                break;
            }
        }
        if (audited) {
            auditor.audit(Optional.of(run), true);
        }
        return exit;
    }

    private boolean fetch(RunToken run, EntryId about) {
        Git.Result r;
        try {
            r = repo.remote().run(List.of("fetch", "-q", config.remote(),
                    "+refs/heads/main:refs/remotes/" + config.remote() + "/main"));
        } catch (Children.NotGone e) {
            fault(run, FaultKind.FETCH_FAILED, about, "the fetch timed out, and " + e.getMessage());
            throw e;
        }
        if (!r.ok()) {
            fault(run, FaultKind.FETCH_FAILED, about, "the fetch exited " + r.exit() + ": " + r.err().strip());
        }
        return r.ok();
    }

    private void ensureWorktree() {
        if (!Files.exists(config.worktree().resolve(".git"))) {
            repo.ok("worktree", "prune");
            repo.ok("worktree", "add", "-q", "--detach", config.worktree().toString(), gather.mainTip().value());
        }
    }

    /** A SeenMain only when main moved since the last: the journal grows by what is new. */
    private void noteMain(RunToken run) {
        Sha tip = gather.mainTip();
        Optional<Sha> last = journal.read().stream().filter(r -> r instanceof JournalRecord.SeenMain)
                .map(r -> ((JournalRecord.SeenMain) r).sha()).reduce((first, second) -> second);
        if (!last.equals(Optional.of(tip))) {
            journal.append(new JournalRecord.SeenMain(run, clock.instant(), tip));
        }
    }

    private void settle(RunToken run, Step.Settle settle) {
        if (settle.record()) {
            switch (settle.outcome()) {
                case EntryState.Withdrawn w -> journal.append(new JournalRecord.Withdrawn(run, clock.instant(), settle.id()));
                case EntryState.Failed f -> journal.append(new JournalRecord.Failed(run, clock.instant(), settle.id(),
                        f.reason(), "the entry's cap"));
                default -> throw new IllegalStateException("no record to write for " + settle);
            }
        }
        deleteRefs(settle.id());
        out.println(settle.id() + " settled: " + settle.outcome());
    }

    /**
     * Steps 3 to 10 for entry {@code id}. A git call that fails where nothing reads its outcome - it timed out, or the
     * repository failed it - is the step's integrator fault about the entry: building the candidate's before the
     * Attempt, the verify's up to the push. From the push on, main says what happened: a failed look is the push's
     * FETCH_FAILED, and a ref deletion failing after a landing is REPO, which ends the run.
     */
    private void attempt(RunToken run, EntryId id) {
        FaultKind[] step = {FaultKind.CANDIDATE};
        try {
            attempt(run, id, step);
        } catch (Git.Failed e) {
            if (step[0] == FaultKind.REPO) {
                throw e;
            }
            fault(run, step[0], id, e.getMessage());
        }
    }

    private void attempt(RunToken run, EntryId id, FaultKind[] step) {
        Sha mainTip = gather.mainTip();
        Optional<Commit> squash = repo.commit(Gather.QUEUE + id);
        boolean sourcePresent = squash.filter(c -> c.landingSources().size() == 1 && Sha.valid(c.landingSources().getFirst()))
                .map(c -> repo.exists(c.landingSources().getFirst())).orElse(false);
        Optional<Bounce> refused = Stale.request(id, squash, sourcePresent);
        if (refused.isPresent()) {
            bounce(run, id, refused.get(), step);
            return;
        }
        Commit request = squash.orElseThrow();
        Sha base = request.parents().getFirst();
        Sha source = Sha.parse(request.landingSources().getFirst());
        Optional<Bounce> stale;
        try {
            List<Commit> landed = repo.log(base.value() + ".." + mainTip.value());
            Set<String> inHistory = Stale.questions(landed).stream()
                    .filter(q -> repo.exists(q) && repo.isAncestor(Sha.parse(q), source)).collect(Collectors.toSet());
            stale = Stale.check(landed, inHistory, repo.log(base.value() + ".." + source.value()));
        } catch (Git.Failed e) {
            fault(run, FaultKind.CANDIDATE, id, e.getMessage());
            return;
        }
        if (stale.isPresent()) {
            bounce(run, id, stale.get(), step);
            return;
        }
        steps.reached(3);
        Git.Result merged = repo.run(List.of("merge-tree", "--write-tree", "-z", "--name-only", "--no-messages",
                "--merge-base=" + base.value(), mainTip.value(), request.sha().value()));
        Sha treeSha;
        switch (MergeOutcome.of(merged.exit(), merged.out(), repo.tree(mainTip))) {
            case Bounce b -> {
                bounce(run, id, b, step);
                return;
            }
            case Faulted f -> {
                fault(run, f.kind(), id, f.detail() + " " + merged.err().strip());
                return;
            }
            case MergeOutcome.Candidate c -> treeSha = c.tree();
        }
        Optional<Sha> candidate = commitTree(treeSha, mainTip, request);
        if (candidate.isEmpty()) {
            fault(run, FaultKind.CANDIDATE, id, "git commit-tree made no candidate on " + mainTip);
            return;
        }
        steps.reached(4);
        journal.append(new JournalRecord.Attempt(run, clock.instant(), id, candidate.get(), mainTip));
        steps.reached(5);
        step[0] = FaultKind.VERIFY_TAMPERED;
        verifyAndPush(run, id, candidate.get(), mainTip, step);
    }

    /** The candidate: main's tip as its one parent, the merged tree, the request's message and author. */
    private Optional<Sha> commitTree(Sha treeSha, Sha parent, Commit request) {
        Git.Result who = repo.run(List.of("log", "-1", "--format=%an%x00%ae%x00%aI", request.sha().value()));
        Git.Result message = repo.run(List.of("log", "-1", "--format=%B", request.sha().value()));
        if (!who.ok() || !message.ok()) {
            return Optional.empty();
        }
        String[] author = who.line().split("\u0000", -1);
        Git.Result made = repo.with(Map.of("GIT_AUTHOR_NAME", author[0], "GIT_AUTHOR_EMAIL", author[1], "GIT_AUTHOR_DATE",
                        author[2], "GIT_COMMITTER_NAME", config.committerName(), "GIT_COMMITTER_EMAIL", config.committerEmail()))
                .run(List.of("commit-tree", treeSha.value(), "-p", parent.value(), "-F", "-"), message.out());
        return made.ok() && Sha.valid(made.line()) ? Optional.of(Sha.parse(made.line())) : Optional.empty();
    }

    private void verifyAndPush(RunToken run, EntryId id, Sha candidate, Sha mainTip, FaultKind[] step) {
        if (!tree.run(List.of("checkout", "-q", "-f", "--detach", candidate.value())).ok()
                || !tree.run(List.of("clean", "-q", "-fd")).ok()) {
            fault(run, FaultKind.VERIFY_TAMPERED, id, "the landing worktree could not be put on " + candidate);
            return;
        }
        Sha before = tree.resolve("HEAD").orElseThrow();
        boolean cleanBefore = clean();
        Verifier.Run verify;
        try {
            verify = verifier.run(config.worktree());
        } catch (Children.NotGone e) {
            fault(run, FaultKind.VERIFY_TIMEOUT, id, "the verify of " + candidate + " timed out, and " + e.getMessage());
            throw e;
        }
        Sha after = tree.resolve("HEAD").orElseThrow();
        boolean cleanAfter = clean();
        steps.reached(6);
        switch (VerifyOutcome.of(verify.lines(), verify.exit(), verify.timedOut(), candidate, before, after, cleanBefore,
                cleanAfter)) {
            case VerifyOutcome.BuildFailed f -> {
                journal.append(new JournalRecord.Failed(run, clock.instant(), id, FailReason.BUILD_FAILED, errors(verify.lines())));
                step[0] = FaultKind.REPO;
                deleteRefs(id);
                out.println(id + " failed its verify");
                return;
            }
            case Faulted f -> {
                fault(run, f.kind(), id, f.detail());
                return;
            }
            case VerifyOutcome.Verified v -> journal.append(new JournalRecord.Verified(run, clock.instant(), id, candidate, mainTip));
        }
        steps.reached(7);
        step[0] = FaultKind.FETCH_FAILED;
        Git.Result push;
        boolean fetchOk;
        boolean onMain = false;
        boolean moved = false;
        try {
            push = repo.remote().run(List.of("push", "-q", config.remote(), candidate.value() + ":refs/heads/main"));
        } catch (Children.NotGone e) {
            fault(run, FaultKind.FETCH_FAILED, id, "the push timed out, and " + e.getMessage());
            throw e;
        }
        boolean pushOk = steps.pushReported(push.ok());
        steps.reached(8);
        try {
            fetchOk = repo.remote().run(List.of("fetch", "-q", config.remote(),
                    "+refs/heads/main:refs/remotes/" + config.remote() + "/main")).ok();
            if (fetchOk) {
                Sha tip = gather.mainTip();
                onMain = gather.landings(tip).of(id).isPresent();
                moved = !tip.equals(mainTip);
            }
        } catch (Git.Failed e) {
            // the fetch and the look at main are one observation: either failing is the push's "fetch fails"
            fetchOk = false;
        } catch (Children.NotGone e) {
            fault(run, FaultKind.FETCH_FAILED, id, "the fetch after the push timed out, and " + e.getMessage());
            throw e;
        }
        switch (PushOutcome.of(pushOk, fetchOk, onMain, moved)) {
            case PushOutcome.Landed l -> out.println(id + " landed as " + candidate);
            case PushOutcome.Halt h -> {
                journal.append(new JournalRecord.Halted(run, clock.instant(), h.reason() + ": " + id));
                return;
            }
            case Faulted f -> {
                fault(run, f.kind(), id, f.detail() + (push.ok() ? "" : ": " + push.err().strip()));
                return;
            }
        }
        steps.reached(9);
        step[0] = FaultKind.REPO;
        deleteRefs(id);
        steps.reached(10);
    }

    /** The landing worktree's tracked files as the candidate has them. */
    private boolean clean() {
        return tree.ok("status", "--porcelain", "--untracked-files=no").isBlank();
    }

    /** Bounced: the record, then the refs - a deletion failing once the record is written is REPO, the run's end. */
    private void bounce(RunToken run, EntryId id, Bounce bounce, FaultKind[] step) {
        journal.append(new JournalRecord.Bounced(run, clock.instant(), id, bounce.reason(), bounce.named()));
        step[0] = FaultKind.REPO;
        deleteRefs(id);
        out.println(id + " bounced: " + bounce.reason() + " " + bounce.named());
    }

    private void fault(RunToken run, FaultKind kind, EntryId id, String detail) {
        journal.append(new JournalRecord.Fault(run, clock.instant(), kind, id, detail));
        out.println("[Fault] " + kind + (id == null ? "" : " " + id) + ": " + detail);
    }

    private void deleteRefs(EntryId id) {
        repo.deleteRef(Gather.QUEUE + id);
        repo.deleteRef(Gather.WITHDRAW + id);
    }

    /** What a failed verify's log said of its errors, the last ten lines of them. */
    private static String errors(List<String> lines) {
        List<String> errors = lines.stream().filter(l -> l.contains("ERROR") || l.contains("BUILD")).toList();
        return String.join("\n", errors.subList(Math.max(0, errors.size() - 10), errors.size()));
    }
}
