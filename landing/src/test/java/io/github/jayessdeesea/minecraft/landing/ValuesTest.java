package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The value types' forms: what parses, what is refused, and how an id is made from a clock and a random source. */
class ValuesTest {

    @ParameterizedTest
    @CsvSource({
            "20261126T143005Z-1a2b3c4d, true",
            "20240229T000000Z-00000000, true",
            // upper-case hex
            "20261126T143005Z-1A2B3C4D, false",
            // seven hex, nine hex
            "20261126T143005Z-1a2b3c4, false",
            "20261126T143005Z-1a2b3c4d5, false",
            // the extended form's colons are not legal in a ref name
            "2026-11-26T14:30:05Z-1a2b3c4d, false",
            // no zone, a local time
            "20261126T143005-1a2b3c4d, false",
            // not a real day or time
            "20260230T120000Z-1a2b3c4d, false",
            "20230229T120000Z-1a2b3c4d, false",
            "20261126T246000Z-1a2b3c4d, false",
            // a ref's prefix left on
            "refs/queue/20261126T143005Z-1a2b3c4d, false",
            "'', false"})
    void anEntryIdParsesOnlyInItsForm(String text, boolean parses) {
        if (parses) {
            assertEquals(text, EntryId.parse(text).value());
        } else {
            assertThrows(IllegalArgumentException.class, () -> EntryId.parse(text));
        }
    }

    @Test
    void anEntryIdIsItsClocksSecondInUtcAndEightHexFromItsRandomSource() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T14:30:05.987Z"), ZoneOffset.ofHours(-8));
        EntryId id = EntryId.next(clock, new Random(7));
        assertEquals("20261126T143005Z-" + String.format("%08x", new Random(7).nextInt()), id.value());
        assertEquals(Instant.parse("2026-11-26T14:30:05Z"), id.submitted());
    }

    @Test
    void entryIdsSortOldestFirst() {
        TreeSet<EntryId> ids = new TreeSet<>(List.of(EntryId.parse("20261126T143005Z-ffffffff"),
                EntryId.parse("20251231T235959Z-00000000"), EntryId.parse("20261126T143005Z-00000001")));
        assertEquals(List.of("20251231T235959Z-00000000", "20261126T143005Z-00000001", "20261126T143005Z-ffffffff"),
                ids.stream().map(EntryId::value).toList());
    }

    @ParameterizedTest
    @CsvSource({
            "0123456789abcdef0123456789abcdef01234567, true",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef, true",
            "0123456789ABCDEF0123456789ABCDEF01234567, false",
            "0123456789abcdef0123456789abcdef0123456, false",
            "0123456789abcdef, false",
            "'', false"})
    void aShaIsFortyOrSixtyFourLowerCaseHex(String text, boolean parses) {
        if (parses) {
            assertEquals(text, Sha.parse(text).value());
        } else {
            assertThrows(IllegalArgumentException.class, () -> Sha.parse(text));
        }
    }

    @ParameterizedTest
    @CsvSource({"00000000000000a1, true", "00000000000000A1, false", "a1, false", "'', false"})
    void aRunTokenIsSixteenLowerCaseHex(String text, boolean parses) {
        if (parses) {
            assertEquals(text, RunToken.parse(text).value());
        } else {
            assertThrows(IllegalArgumentException.class, () -> RunToken.parse(text));
        }
    }

    /**
     * A fault inside a step carries its entry (amendment 1, R2): only a fetch's and an audit's may be about no entry,
     * so the halt that bounds the integrator's faults counts every one.
     */
    @ParameterizedTest
    @EnumSource(FaultKind.class)
    void aFaultNeedsItsEntryButAFetchsOrAnAudits(FaultKind kind) {
        // a pass's own git failure (REPO) may be about no entry: the snapshot, noteMain, the audit
        boolean none = kind == FaultKind.FETCH_FAILED || kind == FaultKind.AUDIT || kind == FaultKind.REPO;
        boolean some = kind != FaultKind.AUDIT;
        if (none) {
            assertEquals(kind, new JournalRecord.Fault(Fixtures.RUN, Fixtures.T0, kind, null, "x").kind());
        } else {
            assertThrows(IllegalArgumentException.class, () -> new JournalRecord.Fault(Fixtures.RUN, Fixtures.T0, kind, null,
                    "x"));
        }
        // an audit's fault is about the journal and main, never one entry: it carries none, so it neither raises I7
        // about its own record nor keeps a try charged
        if (some) {
            assertEquals(kind, new JournalRecord.Fault(Fixtures.RUN, Fixtures.T0, kind, Fixtures.id(1), "x").kind());
        } else {
            assertThrows(IllegalArgumentException.class, () -> new JournalRecord.Fault(Fixtures.RUN, Fixtures.T0, kind,
                    Fixtures.id(1), "x"));
        }
    }

    @Test
    void aRunTokenIsSixteenHexFromItsRandomSource() {
        assertEquals(String.format("%016x", new Random(3).nextLong()), RunToken.next(new Random(3)).value());
    }
}
