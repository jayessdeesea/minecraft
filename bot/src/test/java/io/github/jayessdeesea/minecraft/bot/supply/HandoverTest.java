package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandoverTest {

    private static final Position RECEIVER = new Position(0, 64, 0);

    /** Slots thrown from, one entry per item; any other call fails the test. */
    private final List<Integer> thrown = new ArrayList<>();
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                if (!method.getName().equals("throwOne")) {
                    throw new AssertionError("unexpected " + method.getName());
                }
                thrown.add((Integer) args[2]);
                return null;
            });

    private static Inventory carrying(int... slotAndCount) {
        Inventory inventory = new Inventory();
        int log = java.util.stream.IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.LOG))).findFirst().orElseThrow();
        for (int i = 0; i < slotAndCount.length; i += 2) {
            inventory.setSlot(slotAndCount[i], new int[]{log, slotAndCount[i + 1]});
        }
        return inventory;
    }

    private static Optional<Handover.Delivery> toAlice(int logs) {
        return Optional.of(new Handover.Delivery("Alice", RECEIVER, logs));
    }

    @Test
    void walksUpThrowsWhatWasAskedFacingTheReceiverThenBacksOff() {
        Handover handover = new Handover("Bob", session, carrying(36, 16));
        Position far = new Position(10, 64, 0);

        handover.tick(far, toAlice(3));
        assertTrue(handover.active());
        assertEquals(Optional.of(new Report.CrossLeveling("Alice", 3)), handover.report());
        assertEquals(Optional.of(new Position(Handover.STAND_OFF, 64, 0)), handover.standAt());

        Position close = new Position(2, 64, 0);
        handover.tick(close, toAlice(3)); // within a throw: turn to the receiver
        Optional<Position> facing = Optional.empty();
        for (int i = 0; i < Handover.SETTLE_TICKS; i++) {
            facing = handover.tick(close, toAlice(3));
        }
        assertEquals(Optional.of(RECEIVER), facing);
        assertEquals(List.of(36, 36, 36), thrown);

        Position backTo = handover.standAt().orElseThrow();
        assertTrue(backTo.x() >= close.x() + Handover.BACK_OFF - 1e-9, "backs away from the receiver: " + backTo);
        for (int i = 0; i < Handover.BACK_OFF_TICKS; i++) {
            handover.tick(backTo, Optional.empty());
        }
        assertFalse(handover.active());
    }

    @Test
    void aDeliveryWithdrawnOnTheWayIsDropped() {
        Handover handover = new Handover("Bob", session, carrying(36, 16));

        handover.tick(new Position(10, 64, 0), toAlice(3));
        handover.tick(new Position(9, 64, 0), Optional.empty());

        assertFalse(handover.active());
        assertEquals(List.of(), thrown);
    }

    @Test
    void logsSpreadOverSlotsAreThrownSlotBySlot() {
        Inventory inventory = carrying(9, 2, 36, 16);
        Handover handover = new Handover("Bob", session, inventory);
        Position close = new Position(2, 64, 0);

        handover.tick(close, toAlice(3));
        handover.tick(close, toAlice(3));
        for (int i = 0; i < Handover.SETTLE_TICKS; i++) {
            handover.tick(close, toAlice(3));
        }
        assertEquals(List.of(9, 9), thrown);

        inventory.setSlot(9, null); // the server answers: that slot is empty now
        for (int i = 0; i < Handover.SETTLE_TICKS; i++) {
            handover.tick(close, toAlice(3));
        }
        assertEquals(List.of(9, 9, 36), thrown);
    }
}
