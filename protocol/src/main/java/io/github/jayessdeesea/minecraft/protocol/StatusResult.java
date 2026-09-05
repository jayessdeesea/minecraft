package io.github.jayessdeesea.minecraft.protocol;

/**
 * Result of a Status-state server-list ping: the raw JSON response body and the measured
 * round-trip time. We don't parse the JSON here - no JSON library dependency in this module -
 * callers that care about specific fields (version, player count, MOTD) parse it themselves.
 */
public record StatusResult(String json, long roundTripMillis) {
}
