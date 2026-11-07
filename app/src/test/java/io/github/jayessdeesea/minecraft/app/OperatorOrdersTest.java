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
        assertEquals(Optional.of(new OperatorOrders.Order.Mission("Pat", "hold the bridge")),
                orders.orderFrom(new ChatMessage(OperatorOrders.offlineUuid("Pat"), "!mission  hold the bridge ")));
    }

    @Test
    void escortProtectsTheOperatorOrTheNamedPlayer() {
        UUID jake = OperatorOrders.offlineUuid("Jake");
        assertEquals(Optional.of(new OperatorOrders.Order.Escort("Jake", "Jake")), orders.orderFrom(new ChatMessage(jake, "!escort")));
        assertEquals(Optional.of(new OperatorOrders.Order.Escort("Jake", "Pat")), orders.orderFrom(new ChatMessage(jake, "!escort Pat")));
    }

    @Test
    void anOperatorTalkingIsARemarkAndAnyoneElseTalkingIsJustChat() {
        OperatorOrders orders = new OperatorOrders("Jake");
        UUID jake = OperatorOrders.offlineUuid("Jake");
        assertEquals(Optional.of(new OperatorOrders.Order.Remark("Jake", "it's night, get inside")),
                orders.orderFrom(new ChatMessage(jake, " it's night, get inside ")));
        assertEquals(Optional.empty(), orders.orderFrom(new ChatMessage(OperatorOrders.offlineUuid("Alice"), "it's night")));
    }

    @Test
    void everythingElseIsIgnored() {
        UUID jake = OperatorOrders.offlineUuid("Jake");
        assertEquals(Optional.empty(), orders.orderFrom(new ChatMessage(jake, "!mission ")));
        assertEquals(Optional.empty(), orders.orderFrom(new ChatMessage(jake, "!missionary work")));
        assertEquals(Optional.empty(), orders.orderFrom(new ChatMessage(OperatorOrders.offlineUuid("Alice"), "!mission go home")));
        assertEquals(Optional.empty(), orders.orderFrom(new ChatMessage(OperatorOrders.offlineUuid("Alice"), "!escort")));
        assertEquals(Optional.empty(), new OperatorOrders("").orderFrom(new ChatMessage(jake, "!mission go home")));
    }
}
