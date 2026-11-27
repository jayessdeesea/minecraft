package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A waiter's exit (design section 3, the one table): 0 landed, 1 failed, 2 usage or nothing to land, 3 bounced,
 * 4 halted, 5 still queued at {@code --timeout}.
 */
class ExitCodeTest {

    static Stream<Arguments> table() {
        return Stream.of(
                // state, integrator halted -> exit
                Arguments.of(new EntryState.Landed(sha(3)), false, 0),
                // landed before the halt: the entry's own answer stands
                Arguments.of(new EntryState.Landed(sha(3)), true, 0),
                Arguments.of(new EntryState.Failed(FailReason.BUILD_FAILED), false, 1),
                Arguments.of(new EntryState.Failed(FailReason.ATTEMPTS), true, 1),
                Arguments.of(new EntryState.Bounced(BounceReason.NOTHING_TO_LAND), false, 2),
                Arguments.of(new EntryState.Bounced(BounceReason.CONFLICT), false, 3),
                Arguments.of(new EntryState.Bounced(BounceReason.CARRIES_LANDED), false, 3),
                Arguments.of(new EntryState.Bounced(BounceReason.MERGE_COMMIT), false, 3),
                Arguments.of(new EntryState.Bounced(BounceReason.LANDING_ID), false, 3),
                // not in the design's table: taken back by its author, it comes back as a bounce does
                Arguments.of(new EntryState.Withdrawn(), false, 3),
                Arguments.of(new EntryState.Queued(), true, 4),
                Arguments.of(new EntryState.Verifying(2), true, 4),
                Arguments.of(new EntryState.Queued(), false, 5),
                Arguments.of(new EntryState.Verifying(1), false, 5));
    }

    @ParameterizedTest
    @MethodSource("table")
    void aWaitersExit(EntryState state, boolean halted, int expected) {
        assertEquals(expected, ExitCode.of(state, halted));
    }

    @Test
    void usageIsTwo() {
        assertEquals(2, ExitCode.USAGE);
    }
}
