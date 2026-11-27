package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;

/** How a verify ended: it exited, or its watchdog ended it - quiet for the stall limit, or at its cap. */
public sealed interface VerifyEnd {

    /** Its process exited with {@code exit}: its log says the rest. */
    record Exited(int exit) implements VerifyEnd {
    }

    /** Ended after writing nothing for {@code quiet}; {@code where} the build was, in Maven's words. */
    record Stalled(Duration quiet, String where) implements VerifyEnd {
    }

    /** Ended at its cap, having run {@code ran} still writing; {@code where} the build was. */
    record Capped(Duration ran, String where) implements VerifyEnd {
    }
}
