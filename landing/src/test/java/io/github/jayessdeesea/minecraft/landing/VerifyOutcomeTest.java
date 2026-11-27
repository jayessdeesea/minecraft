package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A verify's outcome (design section 3): Maven's BUILD SUCCESS line, no BUILD FAILURE line, exit 0, HEAD the candidate
 * before and after, the tracked tree clean - Verified. A BUILD FAILURE line - failed. No BUILD line, or SUCCESS with
 * another exit - an integrator fault. Timed out - a fault against the entry. HEAD moved or the tree dirty - a fault,
 * nothing pushed, whatever the build said. A line counts only whole, as Maven prints it ({@code [INFO] BUILD SUCCESS},
 * {@code [INFO]} or {@code [ERROR] BUILD FAILURE}), its colour codes and surrounding space stripped. The candidate is
 * commit 7.
 */
class VerifyOutcomeTest {

    private static final List<String> SUCCESS = List.of("[INFO] Reactor Summary", "[INFO] BUILD SUCCESS", "[INFO] Total");
    private static final List<String> FAILURE = List.of("[ERROR] Tests run: 3, Failures: 1", "[INFO] BUILD FAILURE");
    private static final List<String> BOTH = List.of("[INFO] BUILD SUCCESS", "[INFO] BUILD FAILURE");
    private static final List<String> NEITHER = List.of("[INFO] Scanning for projects...", "Killed");

    static Stream<Arguments> table() {
        return Stream.of(
                // output, exit, timed out, HEAD before, HEAD after, clean -> outcome
                Arguments.of(SUCCESS, 0, false, 7, 7, true, "Verified"),
                Arguments.of(FAILURE, 1, false, 7, 7, true, "BuildFailed"),
                Arguments.of(List.of("[ERROR] BUILD FAILURE"), 1, false, 7, 7, true, "BuildFailed"),
                // a failure line decides it whatever the exit; a line in each, the failure stands
                Arguments.of(FAILURE, 0, false, 7, 7, true, "BuildFailed"),
                Arguments.of(BOTH, 1, false, 7, 7, true, "BuildFailed"),
                // SUCCESS, then a nonzero exit with no further BUILD line: not verified
                Arguments.of(SUCCESS, 1, false, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(NEITHER, 0, false, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(NEITHER, 1, false, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                Arguments.of(List.of(), 0, false, 7, 7, true, "Fault VERIFY_NO_RESULT"),
                // a test printing the words mid-line decides nothing
                Arguments.of(List.of("[INFO] Running FooTest", "expected no BUILD FAILURE here", "[INFO] BUILD SUCCESS"), 0,
                        false, 7, 7, true, "Verified"),
                Arguments.of(List.of("[INFO] Running FooTest", "it said [INFO] BUILD SUCCESS"), 0, false, 7, 7, true,
                        "Fault VERIFY_NO_RESULT"),
                // a colour code, a carriage return, the line's surrounding space: still the line
                Arguments.of(List.of("\u001b[1;32m[INFO] BUILD SUCCESS\u001b[m"), 0, false, 7, 7, true, "Verified"),
                Arguments.of(List.of("[\u001b[1;34mINFO\u001b[m] \u001b[1;32mBUILD SUCCESS\u001b[m\r"), 0, false, 7, 7, true,
                        "Verified"),
                Arguments.of(List.of("  [INFO] BUILD FAILURE  "), 1, false, 7, 7, true, "BuildFailed"),
                Arguments.of(SUCCESS, 0, true, 7, 7, true, "Fault VERIFY_TIMEOUT"),
                Arguments.of(NEITHER, 137, true, 7, 7, true, "Fault VERIFY_TIMEOUT"),
                Arguments.of(SUCCESS, 0, false, 6, 7, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(SUCCESS, 0, false, 7, 8, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(SUCCESS, 0, false, 7, 7, false, "Fault VERIFY_TAMPERED"),
                // HEAD moved during a verify that timed out or failed: the tampering is what is said
                Arguments.of(SUCCESS, 0, true, 7, 8, true, "Fault VERIFY_TAMPERED"),
                Arguments.of(FAILURE, 1, false, 7, 7, false, "Fault VERIFY_TAMPERED"));
    }

    @ParameterizedTest
    @MethodSource("table")
    void whatTheVerifySaid(List<String> output, int exit, boolean timedOut, int before, int after, boolean clean,
                           String expected) {
        VerifyOutcome outcome = VerifyOutcome.of(output, exit, timedOut, sha(7), sha(before), sha(after), clean);
        assertEquals(expected, switch (outcome) {
            case VerifyOutcome.Verified v -> "Verified";
            case VerifyOutcome.BuildFailed f -> "BuildFailed";
            case Faulted f -> "Fault " + f.kind();
        });
    }
}
