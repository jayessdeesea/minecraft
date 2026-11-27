package io.github.jayessdeesea.minecraft.landing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One line of the journal ({@code C:/mc-state/landing/journal.jsonl}), written only by the integrator holding the
 * lock. Every record carries its run's token (I5) and when it was written. Landed is never a record: it is a commit on
 * main bearing the entry's {@code Landing-Id}, so nothing is written after a push and nothing is guessed back.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JournalRecord.Attempt.class, name = "Attempt"),
        @JsonSubTypes.Type(value = JournalRecord.Verified.class, name = "Verified"),
        @JsonSubTypes.Type(value = JournalRecord.Failed.class, name = "Failed"),
        @JsonSubTypes.Type(value = JournalRecord.Bounced.class, name = "Bounced"),
        @JsonSubTypes.Type(value = JournalRecord.Withdrawn.class, name = "Withdrawn"),
        @JsonSubTypes.Type(value = JournalRecord.Fault.class, name = "Fault"),
        @JsonSubTypes.Type(value = JournalRecord.Halted.class, name = "Halted"),
        @JsonSubTypes.Type(value = JournalRecord.Resumed.class, name = "Resumed"),
        @JsonSubTypes.Type(value = JournalRecord.SeenMain.class, name = "SeenMain"),
        @JsonSubTypes.Type(value = JournalRecord.Adopted.class, name = "Adopted"),
        @JsonSubTypes.Type(value = JournalRecord.Cutover.class, name = "Cutover")})
public sealed interface JournalRecord {

    RunToken run();

    Instant at();

    /** The entry the record is about; none for one about main or the integrator. */
    default Optional<EntryId> entry() {
        return Optional.empty();
    }

    /** Whether it settles its entry - Failed, Bounced, Withdrawn: at most one an entry (I4). */
    default boolean settles() {
        return false;
    }

    /** Step 5: candidate {@code candidate}, built on main at {@code parent}, about to be verified. */
    record Attempt(RunToken run, Instant at, EntryId id, Sha candidate, Sha parent) implements JournalRecord {
        public Attempt {
            present("Attempt", run, at, id, candidate, parent);
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.of(id);
        }
    }

    /** Step 7: the candidate verified on {@code parent} - the one commit the integrator may push for the entry. */
    record Verified(RunToken run, Instant at, EntryId id, Sha candidate, Sha parent) implements JournalRecord {
        public Verified {
            present("Verified", run, at, id, candidate, parent);
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.of(id);
        }
    }

    record Failed(RunToken run, Instant at, EntryId id, FailReason reason, String detail) implements JournalRecord {
        public Failed {
            present("Failed", run, at, id, reason, detail);
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.of(id);
        }

        @Override
        public boolean settles() {
            return true;
        }
    }

    /** Sent back unverified; {@code named} the conflicting files, or the commits at fault. */
    record Bounced(RunToken run, Instant at, EntryId id, BounceReason reason, List<String> named) implements JournalRecord {
        public Bounced {
            present("Bounced", run, at, id, reason, named);
            named = List.copyOf(named);
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.of(id);
        }

        @Override
        public boolean settles() {
            return true;
        }
    }

    /** Taken back by its author's request ({@code refs/queue-withdraw/<id>}), settled by the integrator. */
    record Withdrawn(RunToken run, Instant at, EntryId id) implements JournalRecord {
        public Withdrawn {
            present("Withdrawn", run, at, id);
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.of(id);
        }

        @Override
        public boolean settles() {
            return true;
        }
    }

    /**
     * A {@code [Fault]}: its kind says whose it is. A fault inside a step carries its entry, so the halt that bounds
     * the integrator's faults counts every one. A fetch's, and a pass's own git failure's, may be about one or none;
     * an audit's is about the journal
     * and main, never one entry, so it carries none ({@code id} null) - it neither raises I7 about its own record nor
     * keeps a try charged.
     */
    record Fault(RunToken run, Instant at, FaultKind kind, EntryId id, String detail) implements JournalRecord {
        public Fault {
            present("Fault", run, at, kind, detail);
            if (id == null && kind != FaultKind.FETCH_FAILED && kind != FaultKind.AUDIT && kind != FaultKind.REPO) {
                throw new IllegalArgumentException("a " + kind + " fault about no entry");
            }
            if (id != null && kind == FaultKind.AUDIT) {
                throw new IllegalArgumentException("an AUDIT fault about entry " + id + ": an audit's is about no entry");
            }
        }

        @Override
        public Optional<EntryId> entry() {
            return Optional.ofNullable(id);
        }
    }

    /** The integrator stops attempting until a {@code Resumed} follows: every waiter exits 4. */
    record Halted(RunToken run, Instant at, String reason) implements JournalRecord {
        public Halted {
            present("Halted", run, at, reason);
        }
    }

    /** The operator lifts a halt ({@code landing resume <reason>}); the refusals that halted it count from here again. */
    record Resumed(RunToken run, Instant at, String reason) implements JournalRecord {
        public Resumed {
            present("Resumed", run, at, reason);
        }
    }

    /** Main's tip as a run found it: each an ancestor of the next (I6). */
    record SeenMain(RunToken run, Instant at, Sha sha) implements JournalRecord {
        public SeenMain {
            present("SeenMain", run, at, sha);
        }
    }

    /** An outside commit on main accepted ({@code landing adopt <sha> <reason>}): no longer a fault under I2. */
    record Adopted(RunToken run, Instant at, Sha sha, String reason) implements JournalRecord {
        public Adopted {
            present("Adopted", run, at, sha, reason);
        }
    }

    /**
     * K: from this commit on, every commit of main is the queue's (I1 to I3) and the pre-push hook is installed. K is
     * the journal's last Cutover ({@link Audit#cutover}); the journal is kept whole, so it is never lost to a rotation.
     */
    record Cutover(RunToken run, Instant at, Sha sha) implements JournalRecord {
        public Cutover {
            present("Cutover", run, at, sha);
        }
    }

    private static void present(String record, Object... fields) {
        for (Object field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("a " + record + " record with a field missing");
            }
        }
    }
}
