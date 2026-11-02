package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.protocol.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperatorOrdersTest {

    private final OperatorOrders orders = new OperatorOrders("Jake, Pat");

    @Test
    void offlineUuidsMatchTheServers() {
        // Notch's offline-mode UUID, as any offline server assigns it.
        assertEquals(UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"), OperatorOrders.offlineUuid("Notch"));
    }

    @Test
    void aMissionFromAnOperatorIsAnOrder() {
        assertEquals(Optional.of(new OperatorOrders.Order("Pat", "hold the bridge")),
                orders.missionFrom(new ChatMessage(OperatorOrders.offlineUuid("Pat"), "!mission  hold the bridge ")));
    }

    @Test
    void everythingElseIsIgnored() {
        UUID jake = OperatorOrders.offlineUuid("Jake");
        assertEquals(Optional.empty(), orders.missionFrom(new ChatMessage(jake, "nice work, squad")));
        assertEquals(Optional.empty(), orders.missionFrom(new ChatMessage(jake, "!mission ")));
        assertEquals(Optional.empty(), orders.missionFrom(new ChatMessage(OperatorOrders.offlineUuid("Alice"), "!mission go home")));
        assertEquals(Optional.empty(), new OperatorOrders("").missionFrom(new ChatMessage(jake, "!mission go home")));
    }
}
