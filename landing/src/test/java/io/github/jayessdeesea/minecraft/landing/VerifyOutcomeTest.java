package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A verify's outcome (design section 3): Maven's BUILD SUCCESS line, no BUILD FAILURE line, exit 0, HEAD the candidate
 * before and after, the tracked tree clean - Verified. A BUILD FAILURE line - failed. No BUILD line, or SUCCESS with
 * another exit - an integrator fault. Ended by the watchdog - a fault against the entry: quiet past the stall limit,
 * VERIFY_STALLED; past the cap, VERIFY_TIMEOUT - whatever its log says. HEAD moved or the tree dirty - a fault, nothing
 * pushed, whatever the build said. A line counts only whole, as Maven prints it ({@code [INFO] BUILD SUCCESS},
 * {@code [INFO]} or {@code [ERROR] BUILD FAILURE}), its colour codes and surrounding space stripped. The candidate is
 * commit 7; an exit of -1 stands for a verify ended at its cap, -2 for one ended as stalled.
 */
class VerifyOutcomeTest {

    private static final List<String> SUCCESS = List.of("[INFO] Reactor Summary", "[INFO] BUILD SUCCESS", "[INFO] Total");
    private static final List<String> FAILURE = List.of("[ERROR] Tests run: 3, Failures: 1", "[INFO] BUILD FAILURE");
    private static final List<String> BOTH = List.of("[INFO] BUILD SUCCESS", "[INFO] BUILD FAILURE");
    private static final List<String> NEITHER = List.of("[INFO] Scanning for projects...", "Killed");
    private static final int CAPPED = -1;
    private static final int STALLED = -2;
    private static final String WHERE = "app [7/7], 47 test classes done, 0 failing";

    static Stream<Arguments> table() {
        return Stream.of(
                // output, exit (or how it was ended), HEAD before, HEAD after, clean -> outcome
                Arguments.of(SUCCESS, 0, 7, 7, true, "Verified"),
                Arguments.of(FAILURE, 1, 7, 7, true, "BuildFailed"),
                Arguments.of(List.of("[ERROR] BUILD FAILURE"), 1, 7, 7, true, "BuildFailed"),
                // a failure line decides it whatever the exit; a line in each, the failure stands
                Arguments.of(FAILURE, 0, 7, 7, true, "BuildFailed"),
                Arguments.of(BOTH, 1, 7, 7, true, "BuildFailed"),
                // SUCCESS, then a nonzero exit with no further BUILD line: not verified
                Arguments.of(SUCCESS, 1, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(NEITHER, 0, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(NEITHER, 1, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(List.of(), 0, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                // a test printing the words mid-line decides nothing
                Arguments.of(List.of("[INFO] Running FooTest", "expected no BUILD FAILURE here", "[INFO] BUILD SUCCESS"), 0,
                        7, 7, true, "Verified"),
                Arguments.of(List.of("[INFO] Running FooTest", "it said [INFO] BUILD SUCCESS"), 0, 7, 7, true,
                        "Fault VERIFY_NO_RESULT"),
                // a colour code, a carriage return, the line's surrounding space: still the line
                Arguments.of(List.of("\u001b[1;32m[INFO] BUILD SUCCESS\u001b[m"), 0, 7, 7, true, "Verified"),
                Arguments.of(List.of("[\u001b[1;34mINFO\u001b[m] \u001b[1;32mBUILD SUCCESS\u001b[m\r"), 0, 7, 7, true,
                        "Verified"),
                Arguments.of(List.of("  [INFO] BUILD FAILURE  "), 1, 7, 7, true, "BuildFailed"),
                // ended by the watchdog, past the cap or quiet past the stall limit: a BUILD line in the log decides nothing
                Arguments.of(SUCCESS, CAPPED, 7, 7, true, "Fault VERIFY_TIMEOUT"),
                Arguments.of(NEITHER, CAPPED, 7, 7, true, "Fault VERIFY_TIMEOUT"),
                Arguments.of(SUCCESS, STALLED, 7, 7, true, "Fault VERIFY_STALLED"),
                Arguments.of(FAILURE, STALLED, 7, 7, true, "Fault VERIFY_STALLED"),
                Arguments.of(NEITHER, STALLED, 7, 7, true, "Fault VERIFY_STALLED"),
                Arguments.of(SUCCESS, 0, 6, 7, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(SUCCESS, 0, 7, 8, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(SUCCESS, 0, 7, 7, false, "Fault VERIFY_TAMPERED"),
                // HEAD moved during a verify that was ended or failed: the tampering is what is said, over both ends
                Arguments.of(SUCCESS, CAPPED, 7, 8, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(SUCCESS, STALLED, 7, 8, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(NEITHER, STALLED, 7, 7, false, "Fault VERIFY_TAMPERED"),
                Arguments.of(FAILURE, 1, 7, 7, false, "Fault VERIFY_TAMPERED"));
    }

    private static VerifyEnd end(int exit) {
        return switch (exit) {
            case CAPPED -> new VerifyEnd.Capped(Duration.ofMinutes(90), WHERE);
            case STALLED -> new VerifyEnd.Stalled(Duration.ofMinutes(10), WHERE);
            default -> new VerifyEnd.Exited(exit);
        };
    }

    private static String line(VerifyOutcome outcome) {
        return switch (outcome) {
            case VerifyOutcome.Verified v -> "Verified";
            case VerifyOutcome.BuildFailed f -> "BuildFailed";
            case Faulted f -> "Fault " + f.kind();
        };
    }

    /** The tracked tree dirty before the verify: what is verified may not be the candidate - whatever the build says. */
    @ParameterizedTest
    @MethodSource("table")
    void aTreeDirtyBeforeTheVerifyIsTampered(List<String> output, int exit, int before, int after, boolean clean,
                                             String ignored) {
        assertEquals("Fault VERIFY_TAMPERED", line(VerifyOutcome.of(output, end(exit), sha(7), sha(before), sha(after), false,
                clean)));
    }

    @ParameterizedTest
    @MethodSource("table")
    void whatTheVerifySaid(List<String> output, int exit, int before, int after, boolean clean, String expected) {
        assertEquals(expected, line(VerifyOutcome.of(output, end(exit), sha(7), sha(before), sha(after), true, clean)));
    }

    /** The watchdog's faults say how long, and where the build was when it was ended. */
    @Test
    void anEndedVerifysFaultSaysWhereItWas() {
        assertEquals(new Faulted(FaultKind.VERIFY_STALLED, "the verify of " + sha(7) + " wrote nothing for 10m at " + WHERE
                + "; ended"), VerifyOutcome.of(SUCCESS, end(STALLED), sha(7), sha(7), sha(7), true, true));
        assertEquals(new Faulted(FaultKind.VERIFY_TIMEOUT, "the verify of " + sha(7) + " ran 90m, still writing, at " + WHERE
                + "; ended"), VerifyOutcome.of(SUCCESS, end(CAPPED), sha(7), sha(7), sha(7), true, true));
    }
}
