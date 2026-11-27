package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The lock holder's word on the verify it is running, for waiters ({@code verify-progress.json}): written at each of
 * the watchdog's looks, there only while a verify runs. A waiter reads this one file - never verify.log, which does not
 * say whose verify it is, nor the history.
 *
 * @param id        the entry verified
 * @param candidate its candidate
 * @param started   when the verify began
 * @param at        the integrator's last look - over a minute old, it may be gone
 * @param lastGrew  when the log last changed
 * @param logBytes  the log's size then
 * @param stall     the stall limit
 * @param cap       this verify's cap
 * @param maven     where the build is
 * @param estimate  percent done and time left, if a verify has passed
 */
public record VerifyProgress(EntryId id, Sha candidate, Instant started, Instant at, Instant lastGrew, long logBytes,
                             Duration stall, Duration cap, MavenProgress maven, Optional<VerifyEstimate> estimate) {
}
