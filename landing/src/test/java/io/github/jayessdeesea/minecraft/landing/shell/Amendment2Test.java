package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalCodec;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.RunToken;
import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Amendment 2's rows that run in this JVM: what land's 0 means, git's answers whole or failed, the shared ref. */
@Tag("slow")
class Amendment2Test {

    @TempDir
    Path dir;

    /**
     * R7: the entry landed, and the worktree holds more since its source - the tracked tree changed, a commit ahead of
     * the source, a branch not from it - exits 5, land again, the branch left where it is.
     */
    @ParameterizedTest
    @ValueSource(strings = {"changed", "ahead", "elsewhere"})
    void landedWithMoreInTheWorktreeIsLandAgain(String more) {
        ScratchRepo s = new ScratchRepo(dir, 51);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        switch (more) {
            case "changed" -> s.write(s.a.resolve("y"), "changed\n");
            case "ahead" -> {
                s.write(s.a.resolve("z"), "z\n");
                s.git(s.a).ok("add", "z");
                s.git(s.a).ok("commit", "-q", "-m", "more");
            }
            default -> {
                s.git(s.a).ok("reset", "-q", "--hard", s.init.value());
                s.write(s.a.resolve("w"), "w\n");
                s.git(s.a).ok("add", "w");
                s.git(s.a).ok("commit", "-q", "-m", "elsewhere");
            }
        }
        Sha head = s.git(s.a).resolve("HEAD").orElseThrow();
        assertEquals(5, s.land(s.a, "again", s.inline()), s.printed());
        assertTrue(s.printed().contains("land again"), s.printed());
        assertEquals("A edits x", s.subjects().getFirst());
        assertEquals(head, s.git(s.a).resolve("HEAD").orElseThrow(), "the branch left");
    }

    /** R7: HEAD the source and the tree clean - 0, the branch moved to the landing. */
    @Test
    void landedWithNothingMoreIsZero() {
        ScratchRepo s = new ScratchRepo(dir, 52);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        assertEquals(0, s.land(s.a, "again", s.inline()), s.printed());
        assertEquals(s.mainTip(), s.git(s.a).resolve("HEAD").orElseThrow());
    }

    /**
     * R7, pinned: while the entry is unsettled on a halted queue, every land exits 4, queueing and committing nothing;
     * with the remote unreachable it is 6, before the halt is read.
     */
    @Test
    void onAHaltedQueueEveryLandIsFourAndQueuesNothing() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 53);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        new Journal(s.state.resolve("journal.jsonl"), new JournalCodec())
                .append(new JournalRecord.Halted(new RunToken("00000000000000c3"), s.clock.instant(), "a test's halt"));
        s.write(s.a.resolve("z"), "z\n");
        s.git(s.a).ok("add", "z");
        s.git(s.a).ok("commit", "-q", "-m", "more");
        Sha head = s.git(s.a).resolve("HEAD").orElseThrow();
        List<String> refs = s.requestRefs();
        for (int i = 0; i < 3; i++) {
            assertEquals(4, s.land(s.a, "again " + i, s.inline()), s.printed());
        }
        assertEquals(refs, s.requestRefs());
        assertEquals(head, s.git(s.a).resolve("HEAD").orElseThrow());
        Files.move(s.origin, s.root.resolve("origin.gone"));
        assertEquals(6, s.land(s.a, "unreachable", s.inline()), s.printed());
    }

    /** R8: a git that exits leaving its output held open by a child answers a timeout - never an empty answer. */
    @Test
    void outputHeldOpenAfterGitExitsIsATimeout() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 54);
        Path child = s.root.resolve("held.pid");
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  for-each-ref) " + ScratchRepo.NATIVE_CHILD + " cat /proc/$!/winpid > '"
                + ScratchRepo.slash(child) + "'; exit 0;;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(5);
        Git git = new Git(s.a, s.tool());
        try {
            assertThrows(Git.Failed.class, () -> git.ok("for-each-ref", "refs/queue/"));
            assertThrows(Git.Failed.class, () -> git.refs("refs/queue/"));
        } finally {
            if (Files.exists(child)) {
                ProcessHandle.of(Long.parseLong(Files.readString(child).strip())).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    /** R8: exists has three answers - absent is false; a git that cannot answer throws. */
    @Test
    void existsIsTrueFalseOrAFailure() {
        ScratchRepo s = new ScratchRepo(dir, 55);
        assertTrue(s.git(s.a).exists(s.init.value()));
        assertFalse(s.git(s.a).exists("1234567890123456789012345678901234567890"));
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  rev-parse|cat-file) exec " + ScratchRepo.NATIVE_CHILD.replace(" &", "") + ";;\nesac\n"
                + "exec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(2);
        assertThrows(Git.Failed.class, () -> new Git(s.a, s.tool()).exists(s.init.value()));
    }

    /** R8: a request whose source git cannot answer for is the integrator's fault about it, never bounced MALFORMED. */
    @Test
    void aSourceGitCannotAnswerForIsNotMalformed() {
        ScratchRepo s = new ScratchRepo(dir, 56);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        String source = s.git(s.a).resolve("HEAD").orElseThrow().value();
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$*\" in\n  *" + source + "^{commit}*) exec " + ScratchRepo.NATIVE_CHILD.replace(" &", "")
                + ";;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(3);
        s.integrate();
        assertFalse(s.printed().contains("MALFORMED"), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.CANDIDATE
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
    }

    /**
     * R8: a pass's own git failure - for-each-ref failing - is REPO and the run ends 6; a start inside the back-off
     * writes nothing; the third REPO running halts the queue.
     */
    @Test
    void aPasssOwnGitFailureIsJournaledBacksOffAndHalts() {
        ScratchRepo s = new ScratchRepo(dir, 57);
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  for-each-ref) exit 1;;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        assertEquals(6, s.integrate(), s.printed());
        assertEquals(1, s.faults(FaultKind.REPO));
        int written = s.journal().size();
        s.clock.advance(Duration.ofMinutes(1));
        s.integrate();
        assertEquals(written, s.journal().size(), "inside the back-off nothing is written");
        s.clock.advance(Duration.ofMinutes(5));
        assertEquals(6, s.integrate(), s.printed());
        s.clock.advance(Duration.ofMinutes(11));
        assertEquals(6, s.integrate(), s.printed());
        assertEquals(3, s.faults(FaultKind.REPO));
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Halted), s.printed());
    }

    /** R8: a settle's ref deletion failing is REPO about the entry. */
    @Test
    void aSettlesRefDeletionFailingIsRepoAboutTheEntry() {
        ScratchRepo s = new ScratchRepo(dir, 58);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(0, s.run(s.a, Poke.NONE, "withdraw", id));
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "if [ \"$1\" = update-ref ] && [ \"$2\" = -d ]; then exit 1; fi\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        assertEquals(6, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.REPO
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
    }

    /** R8: after the push, main says what happened - a look that fails is FETCH_FAILED about the entry, never tampering. */
    @Test
    void aLookAfterThePushFailingIsFetchFailed() {
        ScratchRepo s = new ScratchRepo(dir, 59);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        String pushed = ScratchRepo.slash(s.root.resolve("pushed"));
        String failed = ScratchRepo.slash(s.root.resolve("failed"));
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  push) git \"$@\"; r=$?; touch '" + pushed + "'; exit $r;;\n"
                + "  log) if [ -e '" + pushed + "' ] && [ ! -e '" + failed + "' ]; then touch '" + failed + "'; exit 1; fi;;\n"
                + "esac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.integrate();
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
        assertEquals(0, s.faults(FaultKind.VERIFY_TAMPERED), s.printed());
        s.clock.advance(Duration.ofMinutes(30));
        s.integrate();
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
    }

    /**
     * R9: the push went, its reply was lost, and the integrator's own fetch fails: the waiter, its entry verified and
     * unsettled, looks at main for itself and finds it landed - 0, not 5.
     */
    @Test
    void aWaiterInDoubtLooksForItself() {
        ScratchRepo s = new ScratchRepo(dir, 60);
        String pushed = ScratchRepo.slash(s.root.resolve("pushed"));
        Path fake = s.root.resolve("git.sh");
        // the push's own update of the tracking ref undone: only a look of one's own may see the landing
        s.write(fake, "case \"$1\" in\n  push) old=$(git rev-parse refs/remotes/origin/main); git \"$@\"; r=$?; "
                + "git update-ref refs/remotes/origin/main $old; touch '" + pushed + "'; exit $r;;\n"
                + "  fetch) if [ -e '" + pushed + "' ] && [ \"$(pwd -W)\" = '" + ScratchRepo.slash(s.a) + "' ]; then exit 1; fi;;\n"
                + "esac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.steps = new Steps() {
            @Override
            public boolean pushReported(boolean ok) {
                return false;
            }
        };
        s.write(s.b.resolve("x"), "1\nE\n3\n");
        assertEquals(0, s.land(s.b, "B edits x", s.inline(), "--timeout", "1"), s.printed());
        assertEquals("B edits x", s.subjects().getFirst());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.entry().isPresent()), "the integrator's own look failed: " + s.printed());
    }

    /**
     * R6: every failure of landing itself is 6 - no config, a config with no repo, a number that is not one, a job not
     * entered - never the JVM's 1, which reads as FAILED.
     */
    @Test
    void everyFailureOfLandingItselfIsSix() {
        ScratchRepo s = new ScratchRepo(dir, 62);
        java.io.ByteArrayOutputStream said = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(said, true, java.nio.charset.StandardCharsets.UTF_8);
        Path good = s.configFile("sh -c true");
        Path noRepo = s.root.resolve("no-repo");
        s.write(noRepo, "journal=" + ScratchRepo.slash(s.root.resolve("j")) + "\n");
        Path badNumber = s.root.resolve("bad-number");
        s.write(badNumber, "repo=" + ScratchRepo.slash(s.a) + "\nverify.timeout.minutes=abc\n");
        java.util.function.Supplier<Children> tree = ProcessTree::new;
        assertEquals(6, Landing.launch(new String[]{"audit"}, java.util.Map.of("LANDING_CONFIG",
                s.root.resolve("absent").toString()), s.a, out, Optional.empty(), tree));
        assertEquals(6, Landing.launch(new String[]{"audit"}, java.util.Map.of("LANDING_CONFIG", noRepo.toString()), s.a, out, Optional.empty(), tree));
        assertEquals(6, Landing.launch(new String[]{"audit"}, java.util.Map.of("LANDING_CONFIG", badNumber.toString()), s.a, out, Optional.empty(),
                tree));
        assertEquals(6, Landing.launch(new String[]{"audit"}, java.util.Map.of("LANDING_CONFIG", good.toString()), s.a, out, Optional.empty(),
                () -> {
                    throw new IllegalStateException("AssignProcessToJobObject failed: 5");
                }));
        assertTrue(said.toString(java.nio.charset.StandardCharsets.UTF_8).contains("landing itself failed"));
    }

    /** R5 in this JVM: a tree not proven gone at the verify's timeout is VERIFY_TIMEOUT, "not gone", and the run ends 6. */
    @Test
    void aVerifyTreeNotProvenGoneEndsTheRun() {
        ScratchRepo s = new ScratchRepo(dir, 63);
        s.children = root -> {
            new ProcessTree().kill(root);
            return false;
        };
        s.verify = ScratchRepo.NATIVE_CHILD + " wait";
        s.verifyTimeout = Duration.ofSeconds(2);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(6, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.VERIFY_TIMEOUT
                && f.entry().map(e -> e.value().equals(id)).orElse(false) && f.detail().contains("not proven gone")), s.printed());
        Sha candidate = s.journal().stream().filter(r -> r instanceof JournalRecord.Attempt).map(r -> ((JournalRecord.Attempt) r)
                .candidate()).reduce((x, y) -> y).orElseThrow();
        assertEquals(candidate, s.git(s.landingTree).resolve("HEAD").orElseThrow(), "no checkout after");
    }

    /** R5 in this JVM: a fetch's tree not proven gone is FETCH_FAILED, "not gone", and the run ends 6. */
    @Test
    void aFetchTreeNotProvenGoneEndsTheRun() {
        ScratchRepo s = new ScratchRepo(dir, 64);
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  fetch) exec " + ScratchRepo.NATIVE_CHILD.replace(" &", "") + ";;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(2);
        s.children = root -> {
            new ProcessTree().kill(root);
            return false;
        };
        assertEquals(6, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.detail().contains("not proven gone")), s.printed());
    }

    /** S1: a ref deletion failing right after a settling record - a bounce, a failed verify - is REPO about the entry. */
    @ParameterizedTest
    @ValueSource(strings = {"bounced", "failed"})
    void aRefDeletionFailingAfterASettlingRecordIsRepo(String how) {
        ScratchRepo s = new ScratchRepo(dir, 65);
        String id;
        if (how.equals("bounced")) {
            id = "20261126T120000Z-0000b0b0";
            s.rawRequest(id, List.of(s.init), "Landing-Id: 20261126T120000Z-0000ffff\nLanding-Source: " + s.init);
        } else {
            s.write(s.a.resolve("fail"), "\n");
            s.git(s.a).ok("add", "fail");
            assertEquals(5, s.queue(s.a, "A breaks it"));
            id = s.lastQueued();
        }
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "if [ \"$1\" = update-ref ] && [ \"$2\" = -d ]; then exit 1; fi\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        assertEquals(6, s.integrate(), s.printed());
        String settled = id;
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.REPO
                && f.entry().map(e -> e.value().equals(settled)).orElse(false)), s.printed());
        assertEquals(0, s.faults(FaultKind.VERIFY_TAMPERED) + s.faults(FaultKind.CANDIDATE), s.printed());
    }

    /** S2: a push whose kill is not proven is the push's FETCH_FAILED about the entry, "not proven gone" - never REPO. */
    @Test
    void aPushNotProvenGoneIsFetchFailed() {
        ScratchRepo s = new ScratchRepo(dir, 66);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  push) exec " + ScratchRepo.NATIVE_CHILD.replace(" &", "") + ";;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(3);
        s.children = root -> {
            new ProcessTree().kill(root);
            return false;
        };
        assertEquals(6, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.entry().map(e -> e.value().equals(id)).orElse(false) && f.detail().contains("not proven gone")), s.printed());
        assertEquals(0, s.faults(FaultKind.REPO), s.printed());
    }

    /** S3: halted, a run reads the halt before any git call - with the repository broken it writes nothing and exits 4. */
    @Test
    void aHaltedRunTouchesNoGit() {
        ScratchRepo s = new ScratchRepo(dir, 67);
        new Journal(s.state.resolve("journal.jsonl"), new JournalCodec())
                .append(new JournalRecord.Halted(new RunToken("00000000000000c3"), s.clock.instant(), "a test's halt"));
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "exit 1\n");
        s.gitProgram = List.of("sh", fake.toString());
        int written = s.journal().size();
        for (int i = 0; i < 3; i++) {
            assertEquals(4, s.integrate(), s.printed());
            s.clock.advance(Duration.ofMinutes(30));
        }
        assertEquals(written, s.journal().size(), s.printed());
    }

    /**
     * S4: landing state looks for itself as a waiter does - the entry verified, the integrator's fetch failing after a
     * push whose reply was lost: state fetches main into its own FETCH_HEAD and answers 0.
     */
    @Test
    void stateInDoubtLooksForItself() {
        ScratchRepo s = new ScratchRepo(dir, 68);
        String pushed = ScratchRepo.slash(s.root.resolve("pushed"));
        Path fake = s.root.resolve("git.sh");
        // the push's own update of the tracking ref undone: only a look of one's own may see the landing
        s.write(fake, "case \"$1\" in\n  push) old=$(git rev-parse refs/remotes/origin/main); git \"$@\"; r=$?; "
                + "git update-ref refs/remotes/origin/main $old; touch '" + pushed + "'; exit $r;;\n"
                + "  fetch) if [ -e '" + pushed + "' ] && [ \"$(pwd -W)\" = '" + ScratchRepo.slash(s.a) + "' ]; then exit 1; fi;;\n"
                + "esac\nexec git \"$@\"\n");
        s.write(s.b.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.b, "B edits x"));
        String id = s.lastQueued();
        s.gitProgram = List.of("sh", fake.toString());
        s.steps = new Steps() {
            @Override
            public boolean pushReported(boolean ok) {
                return false;
            }
        };
        s.integrate();
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.entry().isPresent()), s.printed());
        assertEquals(0, s.run(s.b, Poke.NONE, "state", id), s.printed());
    }

    /**
     * C3 F2: a verify that cannot start - its command not there, as sh is not on a scheduled task's PATH - is the
     * integrator's VERIFY_NO_RESULT about the entry, the run carrying on to its halt rule: three halt the queue, and no
     * entry is failed or left an Attempt charged.
     */
    @Test
    void aVerifyThatCannotStartIsTheIntegratorsFaultAndHalts() {
        ScratchRepo s = new ScratchRepo(dir, 69);
        s.verifyCommand = Optional.of(List.of("no-such-verify-command-anywhere"));
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        for (int minutes : new int[]{0, 6, 11}) {
            s.clock.advance(Duration.ofMinutes(minutes));
            s.integrate();
        }
        assertEquals(3, s.faults(FaultKind.VERIFY_NO_RESULT), s.printed());
        assertTrue(s.printed().contains("the verify did not start"), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Halted), s.printed());
        assertFalse(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Failed), s.printed());
        assertEquals(4, s.run(s.a, Poke.NONE, "state", id));
    }

    /** Amendment 3: the verify's log path a directory, not clearable - VERIFY_NO_RESULT "could not be cleared", not "did not start". */
    @Test
    void aVerifyLogThatCannotBeClearedSaysSo() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 71);
        Files.createDirectories(s.state.resolve("verify.log"));
        Files.writeString(s.state.resolve("verify.log").resolve("in-the-way"), "x");
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        s.integrate();
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.VERIFY_NO_RESULT
                && f.detail().contains("could not be cleared")), s.printed());
        assertFalse(s.printed().contains("did not start"), s.printed());
    }

    /** C3 F3: a run without the budget for a whole attempt starts none - 0, the entry left queued for the next start. */
    @Test
    void aRunWithoutTheBudgetForAnAttemptStartsNone() {
        ScratchRepo s = new ScratchRepo(dir, 70);
        s.runBudget = Duration.ofMinutes(5);
        s.write(s.a.resolve("x"), "1\nE\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        assertEquals(0, s.integrate(), s.printed());
        assertTrue(s.printed().contains("budget"), s.printed());
        assertFalse(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Attempt), s.printed());
        assertEquals(1, s.requestRefs().size());
    }

    /** R9: adopt resolves under the lock - held by another, it is 4, the shared ref unmoved and nothing journaled. */
    @Test
    void adoptWithTheLockHeldTouchesNothing() {
        ScratchRepo s = new ScratchRepo(dir, 61);
        Sha outside = s.outsidePush("o");
        Optional<Sha> shared = s.git(s.a).resolve("refs/remotes/origin/main");
        int written = s.journal().size();
        try (IntegratorLock.Held held = new IntegratorLock(s.state.resolve("integrator.lock")).tryAcquire().orElseThrow()) {
            assertEquals(4, s.run(s.a, Poke.NONE, "adopt", outside.value(), "held"), s.printed());
        }
        assertEquals(shared, s.git(s.a).resolve("refs/remotes/origin/main"));
        assertEquals(written, s.journal().size());
    }
}
