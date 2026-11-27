package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code landing audit}: the invariants of design section 4 checked over what the shell gathered, each one broken a
 * {@link Violation}. The journal is one sequence, kept whole, and given whole; K, the cutover commit, is its last
 * {@code Cutover}. With none, main's invariants (I1 to I3) and the hook (I8) are not yet the queue's to keep, and the
 * report says so rather than passing them in silence.
 */
public final class Audit {

    /** How long a ref may sit with nothing journaled, the lock free and no halt, before the queue is stranded (I7). */
    static final Duration QUIET = Duration.ofMinutes(30);
    /** The journal's length past which the report asks for it to be compacted. */
    static final int COMPACT_PAST = 100_000;

    private Audit() {
    }

    /** The shell's answer that {@code ancestor} is an ancestor of {@code descendant} ({@code git merge-base --is-ancestor}). */
    public record Edge(Sha ancestor, Sha descendant) {
    }

    /**
     * The installed tool (I8): the pre-push hook's hash now (none if absent) and as installed; the last commit touching
     * {@code landing/} as of the commit the jar was built from ({@code git log -1 <built-from> -- landing}), and as of
     * main now ({@code git log -1 origin/main -- landing}, none if main has none). The jar is current when the two are
     * the same commit, whatever later commit it was built from.
     */
    public record Install(Optional<String> hookHash, String installedHookHash, Optional<Sha> builtLandingChange,
                          Optional<Sha> mainLandingChange) {
    }

    /**
     * What the audit reads: the whole journal; main since its K, for I1 to I3; where entries landed, from all of main,
     * for I4 and I7; the request refs; the ancestry the shell confirmed of {@link #questions}; whether no integrator is
     * mid-step - false for a watch that finds the lock held, true for the integrator's own audit or with the lock
     * free; the time; the install; and the refs under {@code refs/queue/} and {@code refs/queue-withdraw/} that name no
     * entry, which every run skips.
     */
    public record Input(List<JournalRecord> journal, Main sinceCutover, Landings landed, Set<EntryId> queued,
                        Set<Edge> ancestors, boolean noIntegratorMidStep, Instant now, Install install,
                        List<String> strayRefs) {
        public Input {
            journal = List.copyOf(journal);
            queued = Set.copyOf(queued);
            ancestors = Set.copyOf(ancestors);
            strayRefs = List.copyOf(strayRefs);
        }

        /** With no stray refs. */
        public Input(List<JournalRecord> journal, Main sinceCutover, Landings landed, Set<EntryId> queued,
                     Set<Edge> ancestors, boolean noIntegratorMidStep, Instant now, Install install) {
            this(journal, sinceCutover, landed, queued, ancestors, noIntegratorMidStep, now, install, List.of());
        }
    }

    /** The violations, in the order of the invariants, and what was not checked and why. */
    public record Report(List<Violation> violations, List<String> notes) {
        public Report {
            violations = List.copyOf(violations);
            notes = List.copyOf(notes);
        }
    }

    /** One invariant broken - "I1" to "I8", or "HALTED" for a halt in force - and where. */
    public record Violation(String invariant, String detail) {
        @Override
        public String toString() {
            return "[Fault] " + invariant + ": " + detail;
        }
    }

    /** K: the journal's last {@code Cutover}, none before the first. */
    public static Optional<Sha> cutover(List<JournalRecord> journal) {
        Sha k = null;
        for (JournalRecord record : journal) {
            if (record instanceof JournalRecord.Cutover c) {
                k = c.sha();
            }
        }
        return Optional.ofNullable(k);
    }

    /** The ancestry I6 needs: each SeenMain against the next that differs, and the last against main's tip. */
    public static List<Edge> questions(List<JournalRecord> journal, Sha tip) {
        List<Edge> edges = new ArrayList<>();
        Sha previous = null;
        for (JournalRecord record : journal) {
            if (record instanceof JournalRecord.SeenMain seen) {
                if (previous != null && !previous.equals(seen.sha())) {
                    edges.add(new Edge(previous, seen.sha()));
                }
                previous = seen.sha();
            }
        }
        if (previous != null && !previous.equals(tip)) {
            edges.add(new Edge(previous, tip));
        }
        return edges;
    }

    /** The report; throws if main was gathered from another K than the journal's - the shell's bug, said loud. */
    public static Report check(Input in) {
        Optional<Sha> k = cutover(in.journal());
        if (!in.sinceCutover().since().equals(k)) {
            throw new IllegalArgumentException("main gathered since " + in.sinceCutover().since().map(Sha::value).orElse("none")
                    + ", but the journal's K is " + k.map(Sha::value).orElse("none"));
        }
        Map<EntryId, List<JournalRecord>> byEntry = byEntry(in.journal());
        List<Violation> found = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (k.isPresent()) {
            linear(in, found);
            landedVerified(in, found);
            onceOnMain(in, found);
        } else {
            notes.add("not yet cut over: I1 to I3 and the pre-push hook (I8) not checked");
        }
        settledOnce(in, byEntry, found);
        runsApart(in, found);
        neverRewound(in, found);
        refsAreTheUnsettled(in, byEntry, found, notes);
        installed(in, k.isPresent(), found);
        if (in.journal().size() > COMPACT_PAST) {
            notes.add("the journal holds " + in.journal().size() + " records, past " + COMPACT_PAST + ": compact it");
        }
        return new Report(found, notes);
    }

    /** I1: main is linear. */
    private static void linear(Input in, List<Violation> found) {
        in.sinceCutover().commits().stream().filter(Commit::merges)
                .forEach(c -> found.add(new Violation("I1", "merge commit " + c.sha() + " on main")));
    }

    /** I2: every commit lands exactly one id, verified as that very commit - so on that parent - or is adopted. */
    private static void landedVerified(Input in, List<Violation> found) {
        Set<Sha> adopted = new HashSet<>();
        Set<String> verified = new HashSet<>();
        for (JournalRecord record : in.journal()) {
            if (record instanceof JournalRecord.Adopted a) {
                adopted.add(a.sha());
            } else if (record instanceof JournalRecord.Verified v) {
                verified.add(v.id() + " " + v.candidate());
            }
        }
        for (Commit commit : in.sinceCutover().commits()) {
            if (adopted.contains(commit.sha())) {
                continue;
            }
            if (commit.landingIds().size() != 1) {
                found.add(new Violation("I2", commit.sha() + " has " + commit.landingIds().size()
                        + " Landing-Id trailers, not one, and is not adopted"));
                continue;
            }
            EntryId id;
            try {
                id = EntryId.parse(commit.landingIds().getFirst());
            } catch (IllegalArgumentException e) {
                found.add(new Violation("I2", commit.sha() + " has a malformed Landing-Id: " + e.getMessage()));
                continue;
            }
            if (!verified.contains(id + " " + commit.sha())) {
                found.add(new Violation("I2", commit.sha() + " lands " + id + " with no Verified record for that commit"));
            }
        }
    }

    /** I3: no id on two commits of main. */
    private static void onceOnMain(Input in, List<Violation> found) {
        Map<String, List<Sha>> commits = new LinkedHashMap<>();
        for (Commit commit : in.sinceCutover().commits()) {
            commit.landingIds().forEach(id -> commits.computeIfAbsent(id, k -> new ArrayList<>()).add(commit.sha()));
        }
        commits.forEach((id, shas) -> {
            if (shas.size() > 1) {
                found.add(new Violation("I3", id + " is on " + shas.size() + " commits of main: " + shas));
            }
        });
    }

    /** I4: at most one settling record an id, none for an id on main, and no attempt after one. */
    private static void settledOnce(Input in, Map<EntryId, List<JournalRecord>> byEntry, List<Violation> found) {
        byEntry.forEach((id, records) -> {
            int settled = 0;
            boolean attemptedAfter = false;
            for (JournalRecord record : records) {
                if (record.settles()) {
                    settled++;
                } else if (record instanceof JournalRecord.Attempt && settled > 0) {
                    attemptedAfter = true;
                }
            }
            if (settled > 1) {
                found.add(new Violation("I4", id + " is settled " + settled + " times in the journal"));
            }
            Optional<Sha> landed = in.landed().of(id);
            if (settled > 0 && landed.isPresent()) {
                found.add(new Violation("I4", id + " is on main as " + landed.get() + " and settled in the journal"));
            }
            if (attemptedAfter) {
                found.add(new Violation("I4", id + " was attempted after it was settled"));
            }
        });
    }

    /** I5: the records of two runs never interleave - a run's token, once another follows it, never comes back. */
    private static void runsApart(Input in, List<Violation> found) {
        Set<RunToken> over = new HashSet<>();
        RunToken current = null;
        for (JournalRecord record : in.journal()) {
            if (record.run().equals(current)) {
                continue;
            }
            if (current != null) {
                over.add(current);
            }
            current = record.run();
            if (over.contains(current)) {
                found.add(new Violation("I5", "run " + current + " wrote again at " + record.at() + " after another run"));
            }
        }
    }

    /** I6: main is never rewound - each SeenMain an ancestor of the next, the last of main now. */
    private static void neverRewound(Input in, List<Violation> found) {
        for (Edge edge : questions(in.journal(), in.sinceCutover().tip())) {
            if (!in.ancestors().contains(edge)) {
                found.add(new Violation("I6", "main went from " + edge.ancestor() + " to " + edge.descendant()
                        + ", which does not descend from it"));
            }
        }
    }

    /**
     * I7 flags a queue nobody is working, not a state the next step clears. Checked only while no integrator is
     * mid-step. A ref left for a settled entry is a note - the next run's first Settle clears it; an unsettled entry
     * with no ref is a violation. A halt in force is its own line. Else the queue is stranded - one violation naming
     * the oldest ref and the count - when a ref is older than {@link #QUIET} and nothing has been journaled for as long;
     * an audit's own fault is no sign of work and does not end the quiet.
     */
    private static void refsAreTheUnsettled(Input in, Map<EntryId, List<JournalRecord>> byEntry, List<Violation> found,
                                            List<String> notes) {
        in.strayRefs().forEach(ref -> found.add(new Violation("I7", ref + " is not an entry")));
        if (!in.noIntegratorMidStep()) {
            notes.add("an integrator is mid-step: I7 not checked");
            return;
        }
        TreeSet<EntryId> queued = new TreeSet<>(in.queued());
        for (EntryId id : queued) {
            EntryState state = Entries.state(id, byEntry.getOrDefault(id, List.of()), in.landed());
            if (state.settled()) {
                notes.add("refs/queue/" + id + " is left for an entry settled as " + state + ": to be cleared by the next run");
            }
        }
        byEntry.forEach((id, records) -> {
            if (!queued.contains(id) && !Entries.state(id, records, in.landed()).settled()) {
                found.add(new Violation("I7", id + " is unsettled and has no request ref"));
            }
        });
        Optional<JournalRecord.Halted> halted = Integrator.halted(in.journal());
        if (halted.isPresent()) {
            found.add(new Violation("HALTED", halted.get().reason()));
            return;
        }
        Instant quietSince = in.now().minus(QUIET);
        boolean quiet = in.journal().stream().filter(r -> !(r instanceof JournalRecord.Fault f && f.kind() == FaultKind.AUDIT))
                .reduce((first, second) -> second).map(r -> r.at().isBefore(quietSince)).orElse(true);
        if (!queued.isEmpty() && queued.first().submitted().isBefore(quietSince) && quiet) {
            found.add(new Violation("I7", "stranded: refs/queue/" + queued.first() + " is the oldest of " + queued.size()
                    + " refs, and nothing has been journaled for " + QUIET.toMinutes() + " minutes"));
        }
    }

    /** I8: the hook as installed, once cut over; the jar built from the last change to {@code landing/} on main. */
    private static void installed(Input in, boolean cutOver, List<Violation> found) {
        Install install = in.install();
        if (cutOver && install.hookHash().isEmpty()) {
            found.add(new Violation("I8", "no pre-push hook is installed"));
        } else if (cutOver && !install.hookHash().get().equals(install.installedHookHash())) {
            found.add(new Violation("I8", "the pre-push hook's hash is " + install.hookHash().get() + ", not the "
                    + install.installedHookHash() + " installed"));
        }
        if (install.builtLandingChange().isEmpty()) {
            found.add(new Violation("I8", "no installed jar recorded"));
            return;
        }
        Sha built = install.builtLandingChange().get();
        install.mainLandingChange().filter(last -> !last.equals(built)).ifPresent(last ->
                found.add(new Violation("I8", "the jar's last change to landing/ is " + built + "; main's is " + last)));
    }

    /** Each entry the journal names, in the order first named, with its records in journal order. */
    private static Map<EntryId, List<JournalRecord>> byEntry(List<JournalRecord> journal) {
        Map<EntryId, List<JournalRecord>> byEntry = new LinkedHashMap<>();
        journal.forEach(r -> r.entry().ifPresent(id -> byEntry.computeIfAbsent(id, k -> new ArrayList<>()).add(r)));
        return byEntry;
    }
}
