package io.github.jayessdeesea.minecraft.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects to a real, locally running Minecraft server and performs a Status-state ping - see
 * DEVELOPMENT.md for how to have one running on localhost:25565 (offline mode, matching our
 * pinned protocol version).
 *
 * <p>Named {@code *IT} rather than {@code *Test} on purpose: Surefire's default include pattern
 * only matches {@code *Test}/{@code *Tests}/{@code *TestCase}, so {@code mvn verify} skips this
 * everywhere by default - it needs a live server this project doesn't start for you, so it must
 * never fail a plain build on a machine without one running. Run it explicitly instead:
 *
 * <pre>{@code mvn -pl protocol test -Dtest=StatusPingLiveIT}</pre>
 */
class StatusPingLiveIT {

    private static final Logger LOG = LogManager.getLogger(StatusPingLiveIT.class);

    @Test
    void pingsTheLocalDevServer() throws Exception {
        StatusResult result = MinecraftClient.pingStatus("localhost", 25565)
                .get(10, TimeUnit.SECONDS);

        LOG.info("Status JSON: {}", result.json());
        LOG.info("Round trip: {} ms", result.roundTripMillis());

        assertTrue(result.json().contains("\"version\""), "status response should describe the server version");
    }
}
