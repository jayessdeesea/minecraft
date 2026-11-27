package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.RUN;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.T0;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.id;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The journal's lines: each record one JSON object on one line, read back to the record it was; anything else refused. */
class JournalCodecTest {

    private final JournalCodec codec = new JournalCodec();

    static Stream<Arguments> records() {
        Instant late = Instant.parse("2026-11-26T12:34:56.789Z");
        return Stream.of(
                Arguments.of(new JournalRecord.Attempt(RUN, T0, id(1), sha(7), sha(6))),
                Arguments.of(new JournalRecord.Verified(RUN, late, id(1), sha(7), sha(6))),
                Arguments.of(new JournalRecord.Failed(RUN, T0, id(1), FailReason.TIMED_OUT, "45 min, twice")),
                Arguments.of(new JournalRecord.Failed(RUN, T0, id(1), FailReason.STALLED, "quiet 10m, twice")),
                Arguments.of(new JournalRecord.Fault(RUN, T0, FaultKind.VERIFY_STALLED, id(1), "wrote nothing for 10m")),
                Arguments.of(new JournalRecord.Bounced(RUN, T0, id(1), BounceReason.CONFLICT,
                        List.of("app/pom.xml", "docs/a \"b\".md", "ünïcode/π.txt"))),
                Arguments.of(new JournalRecord.Bounced(RUN, T0, id(1), BounceReason.NOTHING_TO_LAND, List.of())),
                Arguments.of(new JournalRecord.Withdrawn(RUN, T0, id(1))),
                Arguments.of(new JournalRecord.Fault(RUN, T0, FaultKind.OUTSIDE_PUSH, id(1), "main moved\nunder it")),
                Arguments.of(new JournalRecord.Fault(RUN, T0, FaultKind.AUDIT, null, "I1: merge commit on main")),
                Arguments.of(new JournalRecord.Halted(RUN, T0, "push refused three times")),
                Arguments.of(new JournalRecord.Resumed(RUN, T0, "the deploy key put back")),
                Arguments.of(new JournalRecord.SeenMain(RUN, T0, sha(9))),
                Arguments.of(new JournalRecord.Adopted(RUN, T0, sha(9), "pushed by hand in an outage")),
                Arguments.of(new JournalRecord.Cutover(RUN, T0, sha(9))));
    }

    @ParameterizedTest
    @MethodSource("records")
    void aRecordReadsBackAsItWasWritten(JournalRecord record) {
        String line = codec.write(record);
        assertFalse(line.contains("\n") || line.contains("\r"), line);
        assertEquals(record, codec.read(line));
    }

    /** The form on disk, pinned: a change to it is a change to every journal already written. */
    @Test
    void theLineForm() {
        assertEquals("{\"type\":\"Attempt\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\","
                        + "\"id\":\"20261126T120001Z-00000001\",\"candidate\":\"" + sha(7).value() + "\",\"parent\":\""
                        + sha(6).value() + "\"}",
                codec.write(new JournalRecord.Attempt(RUN, T0, id(1), sha(7), sha(6))));
        assertEquals("{\"type\":\"Fault\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"AUDIT\","
                        + "\"id\":null,\"detail\":\"I5\"}",
                codec.write(new JournalRecord.Fault(RUN, T0, FaultKind.AUDIT, null, "I5")));
    }

    /**
     * A line is a record only if writing the record read gives the line back, character for character (amendment 1,
     * R4): each of these reads as an Attempt in some reader, and none is what this codec writes.
     */
    static Stream<String> notItsOwnForm() {
        String line = new JournalCodec().write(new JournalRecord.Attempt(RUN, T0, id(1), sha(7), sha(6)));
        String at = "\"at\":\"2026-11-26T12:00:00Z\"", run = "\"run\":\"00000000000000a1\"";
        return Stream.of(
                line.replace("T12:00:00Z", "t12:00:00Z"),
                line.replace("T12:00:00Z", "T12:00:00z"),
                line.replace("2026-11-26T12:00:00Z", "2026-11-25T24:00:00Z"),
                line.replace("T12:00:00Z", "T23:59:60Z"),
                line.replace("T12:00:00Z", "T12:00:00.5Z"),
                line.replace("T12:00:00Z", "T12:00:00.000Z"),
                line.replace("T12:00:00Z", "T13:00:00+01:00"),
                "[\"Attempt\"," + line.replace("\"type\":\"Attempt\",", "") + "]",
                line.replace("\"run\":", "\"run\": "),
                line.replaceFirst("\\{", "{ "),
                line + " ",
                line.replace(run + "," + at, at + "," + run),
                line.replace(run + ",", run + "," + run + ","),
                line.replace("\"type\":\"Attempt\",", "").replace("}", ",\"type\":\"Attempt\"}"),
                line.replace("00000000000000a1", "\\u0030000000000000a1"));
    }

    private static final String SHA = "0000000000000000000000000000000000000009";
    private static final String FAULT = "{\"type\":\"Fault\",\"run\":\"00000000000000a1\",";
    private static final String BOUNCED = "{\"type\":\"Bounced\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\","
            + "\"id\":\"20261126T120001Z-00000001\",";

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not json",
            // an unknown kind of record
            "{\"type\":\"Landed\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA + "\"}",
            // no kind
            "{\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA + "\"}",
            // a field missing, a field null, a field unknown
            "{\"type\":\"SeenMain\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\"}",
            "{\"type\":\"SeenMain\",\"run\":\"00000000000000a1\",\"at\":null,\"sha\":\"" + SHA + "\"}",
            "{\"type\":\"SeenMain\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA
                    + "\",\"by\":\"me\"}",
            // a fault's entry may be null but not absent
            "{\"type\":\"Fault\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"AUDIT\",\"detail\":\"x\"}",
            // a value out of its form
            "{\"type\":\"SeenMain\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"abc\"}",
            "{\"type\":\"Withdrawn\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"id\":\"1\"}",
            "{\"type\":\"Fault\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"NO_SUCH\","
                    + "\"id\":null,\"detail\":\"x\"}",
            // nothing coerced: a number for a time, an enum or text; a boolean for an enum; numbers in a list of text
            FAULT + "\"at\":1764158400,\"kind\":\"AUDIT\",\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"2026-11-26T12:00:00Z\",\"kind\":0,\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"0\",\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":5}",
            FAULT + "\"at\":\"2026-11-26T12:00:00Z\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":true}",
            BOUNCED + "\"reason\":true,\"named\":[]}",
            BOUNCED + "\"reason\":\"CONFLICT\",\"named\":[1,2]}",
            BOUNCED + "\"reason\":\"CONFLICT\",\"named\":\"a.txt\"}",
            // a time only in UTC, ending in Z: no offset, no local time, no date alone
            FAULT + "\"at\":\"2026-11-26T13:00:00+01:00\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"2026-11-26T12:00:00\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"2026-11-26\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":\"x\"}",
            FAULT + "\"at\":\"\",\"kind\":\"AUDIT\",\"id\":null,\"detail\":\"x\"}",
            // two records on a line, or one cut short (a crash mid-write)
            "{\"type\":\"Cutover\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA + "\"}"
                    + "{\"type\":\"Cutover\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA + "\"}",
            "{\"type\":\"Cutover\",\"run\":\"00000000000000a1\",\"at\":\"2026-11-26T12:00:00Z\",\"sha\":\"" + SHA})
    @MethodSource("notItsOwnForm")
    void aLineNotARecordIsRefused(String line) {
        assertThrows(JournalFormatException.class, () -> codec.read(line));
    }
}
