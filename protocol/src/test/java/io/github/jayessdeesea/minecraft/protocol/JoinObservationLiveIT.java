package io.github.jayessdeesea.minecraft.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Exploratory tool, not a fixed-behavior test: logs in against a real local server and prints
 * every Configuration-state packet ID/length it observes. See DEVELOPMENT.md for how to have a
 * server running on localhost:25565.
 *
 * <p>Named {@code *IT} so {@code mvn verify} skips it by default (see StatusPingLiveIT's javadoc
 * for why). Run explicitly:
 *
 * <pre>{@code mvn -pl protocol test -Dtest=JoinObservationLiveIT}</pre>
 */
class JoinObservationLiveIT {

    private static final Logger LOG = LogManager.getLogger(JoinObservationLiveIT.class);

    @Test
    void observesConfigurationPacketsAfterLogin() throws Exception {
        List<ObservedPacket> observed = MinecraftClient.observeJoin("localhost", 25565, "ScoutBot")
                .get(15, TimeUnit.SECONDS);

        LOG.info("Observed {} Configuration packet(s):", observed.size());
        for (ObservedPacket packet : observed) {
            LOG.info("  id=0x{} payloadLength={}",
                    Integer.toHexString(packet.packetId()).toUpperCase(), packet.payloadLength());
        }

        assertFalse(observed.isEmpty(), "expected at least one Configuration packet from the server");
    }
}
