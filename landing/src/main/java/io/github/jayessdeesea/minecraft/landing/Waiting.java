package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a waiter says of its entry, from three sources each with its one writer: the journal (state, attempts, faults,
 * back-off, halt), the progress file (the verify running) and the queue's refs (who is ahead). A line is said when its
 * key changes and every {@code feedback.seconds} otherwise; the key is the state, the attempt, the module, the newest
 * fault, the number ahead and the quiet and last-looked warnings - no counts, percents or ages - so progress reads as
 * counts moving and a small output age, a stuck verify as a growing quiet, a dead integrator as a growing last look,
 * and a queue not started as a journal growing old.
 */
public final class Waiting {

    /** Past this the integrator's last look is said: it may be gone. */
    static final Duration LOOKED = Duration.ofMinutes(1);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm'Z'").withZone(ZoneOffset.UTC);
    private static final Set<FaultKind> ENDED = Set.of(FaultKind.VERIFY_STALLED, FaultKind.VERIFY_TIMEOUT);

    private Waiting() {
    }

    /** A line and what about it is a change: {@code key}, the text without its counts and ages. */
    public record Said(String key, String text) {
    }

    /**
     * The entry's line. {@code next} and {@code ahead} - the integrator's next step and the requests older than this
     * one - are read only while it is queued; {@code progress} is the running verify's, whoever's it is, and is said of
     * this entry only if it is this entry's, of the one ahead only if it is one of {@code ahead}'s.
     */
    public static Said line(EntryId id, EntryState state, List<JournalRecord> journal, Optional<VerifyProgress> progress,
                            Optional<Step> next, List<EntryId> ahead, Instant now) {
        if (state.settled()) {
            return new Said("settled", id + " settled: " + state);
        }
        if (Entries.pushed(id, journal)) {
            return new Said("verified", id + " verified; being pushed");
        }
        Optional<JournalRecord.Fault> fault = newestFault(id, journal);
        if (fault.isPresent()) {
            JournalRecord.Fault f = fault.get();
            return new Said("fault " + f.kind() + " " + f.at(), id + " [Fault] " + f.kind() + ": " + f.detail() + strikes(id, f,
                    journal));
        }
        Optional<VerifyProgress> mine = progress.filter(p -> p.id().equals(id));
        if (state instanceof EntryState.Verifying v) {
            if (mine.isEmpty()) {
                Instant since = journal.stream().filter(r -> r instanceof JournalRecord.Attempt && Entries.about(r, id))
                        .map(JournalRecord::at).reduce((a, b) -> b).orElseThrow();
                return new Said("attempt " + v.attempts(), id + " attempt " + v.attempts() + " since " + CLOCK.format(since)
                        + "; no verify running yet");
            }
            return verifying(id, v.attempts(), mine.get(), now);
        }
        String queued = id + " queued " + Span.of(Duration.between(id.submitted(), now));
        if (next.orElse(null) instanceof Step.Halt h) {
            return new Said("queued halted", queued + "; the queue is halted: " + h.reason());
        }
        if (next.orElse(null) instanceof Step.Idle idle && idle.until().isPresent()) {
            String after = journal.stream().filter(r -> r instanceof JournalRecord.Fault f
                            && f.kind().blame() == FaultKind.Blame.INTEGRATOR).map(r -> (JournalRecord.Fault) r)
                    .reduce((a, b) -> b).map(f -> " after " + f.kind() + ": " + f.detail()).orElse("");
            return new Said("queued backing off", queued + "; the integrator backs off until "
                    + CLOCK.format(idle.until().get()) + after);
        }
        if (!ahead.isEmpty()) {
            String text = queued + ", " + ahead.size() + " ahead";
            String key = "queued " + ahead.size() + " ahead";
            // only a verify of an entry ahead is the one ahead: a file of any other is no one's
            Optional<VerifyProgress> theirs = progress.filter(p -> ahead.contains(p.id()));
            if (theirs.isPresent()) {
                VerifyProgress p = theirs.get();
                Duration looked = Duration.between(p.at(), now);
                boolean lookedSaid = looked.compareTo(LOOKED) > 0;
                key += " " + p.maven().where() + (lookedSaid ? " unlooked" : "");
                text += ": " + p.id() + " verifying " + Span.of(Duration.between(p.started(), now)) + ", " + p.estimate()
                        .map(e -> e.words() + " - " + p.maven().where() + "; this one about "
                                + Span.about(e.left().plus(e.expected().multipliedBy(ahead.size()))))
                        .orElse(p.maven().words())
                        + (lookedSaid ? unlooked(looked) : "");
            }
            return new Said(key, text);
        }
        String last = journal.isEmpty() ? "the journal is empty"
                : "the journal's last line is " + Span.of(Duration.between(journal.getLast().at(), now)) + " old";
        return new Said("queued next", queued + "; next to be attempted; " + last);
    }

    private static Said verifying(EntryId id, int attempts, VerifyProgress p, Instant now) {
        Duration quiet = Duration.between(p.lastGrew(), now);
        Duration looked = Duration.between(p.at(), now);
        boolean quietSaid = quiet.compareTo(p.stall().dividedBy(2)) > 0;
        boolean lookedSaid = looked.compareTo(LOOKED) > 0;
        String where = p.estimate().map(e -> e.words() + " - " + p.maven().where() + ", " + p.maven().classesDone() + " of "
                        + e.classes() + " test classes" + (p.maven().classesFailing() > 0 ? ", " + p.maven().classesFailing()
                        + " failing" : ""))
                .orElse(p.maven().words() + " " + VerifyEstimate.NONE);
        String text = id + " verifying, attempt " + attempts + ", " + Span.of(Duration.between(p.started(), now)) + ": " + where
                + "; last output " + Span.of(quiet) + " ago"
                + (quietSaid ? "; quiet " + Span.of(quiet) + " - ended at " + Span.of(p.stall()) : "")
                + (lookedSaid ? unlooked(looked) : "");
        return new Said("verifying " + attempts + " " + p.maven().where() + (quietSaid ? " quiet" : "")
                + (lookedSaid ? " unlooked" : ""), text);
    }

    /** A verify's integrator not looking for over a minute: it may be gone, and the task starts another. */
    private static String unlooked(Duration looked) {
        return "; the integrator last looked " + Span.of(looked) + " ago - it may be gone; the task starts one within 5m";
    }

    /** The entry's last record, if it is a fault: a fault since its last attempt, the newest thing said of it. */
    private static Optional<JournalRecord.Fault> newestFault(EntryId id, List<JournalRecord> journal) {
        JournalRecord last = null;
        for (JournalRecord r : journal) {
            if (Entries.about(r, id)) {
                last = r;
            }
        }
        return last instanceof JournalRecord.Fault f ? Optional.of(f) : Optional.empty();
    }

    /** "; 1 of 2" for an ended verify, "; 1 of 3" for main moving under the push; the integrator's own count no strike. */
    private static String strikes(EntryId id, JournalRecord.Fault f, List<JournalRecord> journal) {
        if (ENDED.contains(f.kind())) {
            return "; " + (Entries.faults(id, FaultKind.VERIFY_STALLED, journal) + Entries.faults(id, FaultKind.VERIFY_TIMEOUT,
                    journal)) + " of " + Integrator.MOST_ENDED_VERIFIES;
        }
        if (f.kind() == FaultKind.OUTSIDE_PUSH) {
            return "; " + Entries.faults(id, f.kind(), journal) + " of " + Integrator.MOST_MAIN_MOVES;
        }
        return "";
    }

    /** Said when nothing has been, when the key changed, or once {@code every} has gone since the last. */
    public static boolean due(Optional<Said> last, Instant lastAt, Said now, Instant at, Duration every) {
        return last.isEmpty() || !last.get().key().equals(now.key()) || !at.isBefore(lastAt.plus(every));
    }
}
