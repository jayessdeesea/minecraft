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

/**
 * Against a fake server that answers as the real one does: a throw with a stale state id is answered
 * a tick later by the whole inventory - unless it's silent.
 */
class HandoverTest {

    private static final Position RECEIVER = new Position(0, 64, 0);
    private static final Position CLOSE = new Position(2, 64, 0);

    /** Slots thrown from, one entry per item; any other call fails the test. */
    private final List<Integer> thrown = new ArrayList<>();
    private final List<Integer> unanswered = new ArrayList<>();
    private boolean silent;
    private Inventory inventory;

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                if (!method.getName().equals("throwOne")) {
                    throw new AssertionError("unexpected " + method.getName());
                }
                thrown.add((Integer) args[2]);
                unanswered.add((Integer) args[2]);
                return null;
            });

    private Inventory carrying(int... slotAndCount) {
        inventory = new Inventory();
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

    /** One server tick - the inventory resent for the throws since the last - then one soldier tick. */
    private Optional<Position> tick(Handover handover, Position soldier, Optional<Handover.Delivery> delivery) {
        if (!silent) {
            for (int slot : unanswered) {
                int left = inventory.countAt(slot) - 1;
                inventory.setSlot(slot, left == 0 ? null : new int[]{inventory.itemAt(slot), left});
            }
        }
        unanswered.clear();
        return handover.tick(soldier, delivery);
    }

    @Test
    void walksUpThrowsWhatWasAskedFacingTheReceiverThenBacksOff() {
        Handover handover = new Handover("Bob", session, carrying(36, 16));
        Position far = new Position(10, 64, 0);

        tick(handover, far, toAlice(3));
        assertTrue(handover.active());
        assertEquals(Optional.of(new Report.CrossLeveling("Alice", 3)), handover.report());
        assertEquals(Optional.of(new Position(Handover.STAND_OFF, 64, 0)), handover.standAt());

        tick(handover, CLOSE, toAlice(3)); // within a throw
        assertEquals(Optional.of(RECEIVER), tick(handover, CLOSE, toAlice(3)), "turns to the receiver first");
        assertEquals(List.of(), thrown);
        tick(handover, CLOSE, toAlice(3)); // the facing has gone out
        tick(handover, CLOSE, toAlice(3));
        assertEquals(List.of(36, 36, 36), thrown);

        tick(handover, CLOSE, Optional.empty()); // the server's answer; the receiver no longer asks
        tick(handover, CLOSE, Optional.empty());
        Position backTo = handover.standAt().orElseThrow();
        assertTrue(backTo.x() >= CLOSE.x() + Handover.BACK_OFF - 1e-9, "backs away from the receiver: " + backTo);
        for (int i = 0; i < Handover.PICKUP_DELAY_TICKS; i++) {
            tick(handover, backTo, Optional.empty());
        }
        assertFalse(handover.active());
        assertEquals(13, inventory.countAt(36));
    }

    @Test
    void aGiverThatPicksItsOwnLogsBackUpFailsLoudly() {
        Handover handover = new Handover("Bob", session, carrying(36, 16));
        for (int i = 0; i < 7; i++) { // walk confirmed, face, facing out, throw, answer, back off
            tick(handover, CLOSE, toAlice(3));
        }
        assertTrue(handover.active());

        inventory.setSlot(36, new int[]{inventory.itemAt(36), 16}); // nowhere to go: it walked over them
        tick(handover, CLOSE, toAlice(3));

        assertFalse(handover.active());
    }

    @Test
    void aDeliveryWithdrawnOnTheWayIsDropped() {
        Handover handover = new Handover("Bob", session, carrying(36, 16));

        tick(handover, new Position(10, 64, 0), toAlice(3));
        tick(handover, new Position(9, 64, 0), Optional.empty());

        assertFalse(handover.active());
        assertEquals(List.of(), thrown);
    }

    @Test
    void logsSpreadOverSlotsAreThrownSlotBySlotEachOnTheServersCount() {
        Handover handover = new Handover("Bob", session, carrying(9, 2, 36, 16));

        for (int i = 0; i < 5; i++) { // walk confirmed, face, facing out, throw
            tick(handover, CLOSE, toAlice(3));
        }
        assertEquals(List.of(9, 9), thrown);
        tick(handover, CLOSE, toAlice(3)); // the server answers: slot 9 is empty now
        assertEquals(List.of(9, 9), thrown, "nothing more until the answer is read");
        tick(handover, CLOSE, toAlice(3));
        assertEquals(List.of(9, 9, 36), thrown);
    }

    @Test
    void aThrowTheServerNeverAnswersFailsLoudly() {
        silent = true;
        Handover handover = new Handover("Bob", session, carrying(36, 16));

        for (int i = 0; i < 6 + Handover.ANSWER_TICKS; i++) {
            tick(handover, CLOSE, toAlice(3));
        }

        assertFalse(handover.active());
        assertEquals(List.of(36, 36, 36), thrown);
    }
}
