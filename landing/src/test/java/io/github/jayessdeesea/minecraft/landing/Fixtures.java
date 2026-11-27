package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Ids, shas, records and mains for the tables of evals, named by small numbers so a row reads as a sentence. */
final class Fixtures {

    static final RunToken RUN = new RunToken("00000000000000a1");
    static final RunToken RUN2 = new RunToken("00000000000000b2");
    static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

    private Fixtures() {
    }

    /** Entry {@code n}, submitted {@code n} seconds after noon: a lower number is an older request. */
    static EntryId id(int n) {
        return new EntryId(String.format("20261126T1200%02dZ-%08x", n, n));
    }

    static Sha sha(int n) {
        return new Sha(String.format("%040x", n));
    }

    static Instant at(int minutes) {
        return T0.plus(Duration.ofMinutes(minutes));
    }

    static JournalRecord.Attempt attempt(int id) {
        return new JournalRecord.Attempt(RUN, T0, id(id), sha(100 + id), sha(1));
    }

    static JournalRecord.Verified verified(int id, int candidate) {
        return new JournalRecord.Verified(RUN, T0, id(id), sha(candidate), sha(1));
    }

    static JournalRecord.Failed failed(int id) {
        return new JournalRecord.Failed(RUN, T0, id(id), FailReason.BUILD_FAILED, "BUILD FAILURE");
    }

    static JournalRecord.Bounced bounced(int id, BounceReason reason) {
        return new JournalRecord.Bounced(RUN, T0, id(id), reason, List.of());
    }

    static JournalRecord.Withdrawn withdrawn(int id) {
        return new JournalRecord.Withdrawn(RUN, T0, id(id));
    }

    /** A fault about entry {@code id} at {@code minutes} past noon. */
    static JournalRecord.Fault fault(FaultKind kind, int id, int minutes) {
        return new JournalRecord.Fault(RUN, at(minutes), kind, id(id), kind.name());
    }

    /** A fault about no entry at {@code minutes} past noon: only a fetch's or an audit's. */
    static JournalRecord.Fault fault(FaultKind kind, int minutes) {
        return new JournalRecord.Fault(RUN, at(minutes), kind, null, kind.name());
    }

    /** A try at entry {@code id} that the push's refusal, main unmoved, ended at {@code minutes} past noon. */
    static List<JournalRecord> refused(int id, int minutes) {
        return List.of(new JournalRecord.Attempt(RUN, at(minutes), id(id), sha(100 + id), sha(1)),
                new JournalRecord.Verified(RUN, at(minutes), id(id), sha(100 + id), sha(1)),
                fault(FaultKind.PUSH_REFUSED, id, minutes));
    }

    static JournalRecord.Halted halted() {
        return new JournalRecord.Halted(RUN, T0, "push refused three times running with main unmoved");
    }

    static JournalRecord.Resumed resumed() {
        return new JournalRecord.Resumed(RUN, T0, "the deploy key put back");
    }

    static JournalRecord.SeenMain seen(int sha) {
        return new JournalRecord.SeenMain(RUN, T0, sha(sha));
    }

    static JournalRecord.Cutover cutover(int sha) {
        return new JournalRecord.Cutover(RUN, T0, sha(sha));
    }

    static JournalRecord.Adopted adopted(int sha) {
        return new JournalRecord.Adopted(RUN, T0, sha(sha), "an outside commit");
    }

    /** A journal of records, and of lists or arrays of them, in order. */
    static List<JournalRecord> journal(Object... parts) {
        List<JournalRecord> records = new ArrayList<>();
        for (Object part : parts) {
            switch (part) {
                case JournalRecord r -> records.add(r);
                case List<?> l -> l.forEach(r -> records.add((JournalRecord) r));
                case JournalRecord[] a -> records.addAll(Arrays.asList(a));
                default -> throw new IllegalArgumentException("not a record: " + part);
            }
        }
        return List.copyOf(records);
    }

    /** Commit {@code sha}, landing entry {@code id} from source {@code 500 + id}, on parent {@code sha - 1}. */
    static Commit landing(int sha, int id) {
        return new Commit(sha(sha), List.of(sha(sha - 1)), List.of(id(id).value()), List.of(sha(500 + id).value()));
    }

    /** Commit {@code sha} with no trailers, on parent {@code sha - 1}. */
    static Commit plain(int sha) {
        return new Commit(sha(sha), List.of(sha(sha - 1)), List.of(), List.of());
    }

    static Commit merge(int sha) {
        return new Commit(sha(sha), List.of(sha(sha - 1), sha(sha + 1000)), List.of(), List.of());
    }

    /** Where entries landed, from these commits of main, oldest first. */
    static Landings landed(Commit... commits) {
        return Landings.of(List.of(commits));
    }

    /** A main since K, commit 1, whose tip is the last commit given (or commit 1 when none is), oldest first. */
    static Main main(Commit... commits) {
        return mainSince(Optional.of(sha(1)), commits);
    }

    /** Main gathered since {@code k} (none before the cutover): its tip the last commit given, or K's, or commit 1. */
    static Main mainSince(Optional<Sha> k, Commit... commits) {
        List<Commit> list = new ArrayList<>(Arrays.asList(commits));
        return new Main(list.isEmpty() ? k.orElse(sha(1)) : list.getLast().sha(), k, list);
    }
}
