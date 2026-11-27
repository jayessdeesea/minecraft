package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Audit;
import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.RunToken;

import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code landing audit}, run: the violations said on stdout every time, each journaled once for its text by a holder
 * of the lock, so the journal grows by what is new, not by each look.
 */
final class Auditor {

    private final Gather gather;
    private final Journal journal;
    private final Config config;
    private final Clock clock;
    private final PrintStream out;

    Auditor(Gather gather, Journal journal, Config config, Clock clock, PrintStream out) {
        this.gather = gather;
        this.journal = journal;
        this.config = config;
        this.clock = clock;
        this.out = out;
    }

    /** The report; {@code run} the lock holder's token, to journal what is new, or none for a watch that has not the lock. */
    Audit.Report audit(Optional<RunToken> run, boolean noIntegratorMidStep) {
        List<JournalRecord> records = journal.read();
        Audit.Report report = Audit.check(gather.auditInput(records, noIntegratorMidStep, config));
        Set<String> journaled = records.stream()
                .filter(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.AUDIT)
                .map(r -> ((JournalRecord.Fault) r).detail()).collect(Collectors.toSet());
        for (Audit.Violation violation : report.violations()) {
            out.println(violation);
            if (run.isPresent() && !journaled.contains(violation.toString())) {
                journal.append(new JournalRecord.Fault(run.get(), clock.instant(), FaultKind.AUDIT, null, violation.toString()));
            }
        }
        report.notes().forEach(note -> out.println("note: " + note));
        return report;
    }
}
