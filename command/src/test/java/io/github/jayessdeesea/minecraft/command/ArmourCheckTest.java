package io.github.jayessdeesea.minecraft.command;

import io.github.jayessdeesea.minecraft.command.ArmourCheck.Inventory;
import io.github.jayessdeesea.minecraft.command.ArmourCheck.Piece;
import io.github.jayessdeesea.minecraft.command.ArmourCheck.Stores;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The armour check's table (docs/echelons/platoon-leader/decisions.md, "Armour"): one soldier's inventory and the stores, its verdict. */
class ArmourCheckTest {

    private static final Set<Piece> NONE = Set.of();
    private static final Set<Piece> ALL = EnumSet.allOf(Piece.class);

    static Stream<Arguments> table() {
        return Stream.of(
                // held, copper, iron, room, stores copper, stores iron, iron issued -> verdict
                Arguments.of(ALL, 0, 0, true, 0, 0, true, "Armoured"),
                Arguments.of(ALL, 64, 64, false, 99, 99, true, "Armoured"),
                // Live (2026-11-26): Hal, 533 copper ingots, none on.
                Arguments.of(NONE, 533, 0, true, 0, 0, false, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                Arguments.of(NONE, 24, 0, true, 0, 0, false, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                // The best the metal allows: 12 makes a chestplate and boots, not the leggings after the chestplate.
                Arguments.of(NONE, 12, 0, true, 0, 0, false, "Ordered [CHESTPLATE, BOOTS] 0"),
                Arguments.of(NONE, 4, 0, true, 0, 0, false, "Ordered [BOOTS] 0"),
                Arguments.of(NONE, 3, 0, true, 0, 0, false, "Unable " + ArmourCheck.NO_METAL),
                // Only the slots empty: boots and helmet on, chestplate and leggings ordered.
                Arguments.of(EnumSet.of(Piece.BOOTS, Piece.HELMET), 15, 0, true, 0, 0, false, "Ordered [CHESTPLATE, LEGGINGS] 0"),
                // Pack first, then the stores.
                Arguments.of(NONE, 8, 0, true, 16, 0, false, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 3"),
                Arguments.of(NONE, 0, 0, true, 24, 0, false, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 4"),
                // Pack and stores together for a piece neither pays for alone, as a draw makes it up: 3 and 2 make boots.
                Arguments.of(NONE, 3, 0, true, 2, 0, false, "Ordered [BOOTS] 1"),
                // Iron only where the camp's level issues it.
                Arguments.of(NONE, 0, 24, true, 0, 0, false, "Unable " + ArmourCheck.NO_METAL),
                Arguments.of(NONE, 0, 24, true, 0, 0, true, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                Arguments.of(NONE, 0, 0, true, 0, 30, false, "Unable " + ArmourCheck.NO_METAL),
                Arguments.of(NONE, 0, 0, true, 0, 30, true, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 4"),
                // A piece is one metal: 6 copper and 6 iron make boots and a helmet, not a chestplate.
                Arguments.of(NONE, 6, 6, true, 0, 0, true, "Ordered [BOOTS, HELMET] 0"),
                // No room: nothing ordered, and said - the full pack is the fault (Crafter: "no room in its pack").
                Arguments.of(NONE, 533, 0, false, 0, 0, false, "Unable " + ArmourCheck.NO_ROOM),
                Arguments.of(NONE, 0, 0, false, 0, 0, false, "Unable " + ArmourCheck.NO_METAL));
    }

    @ParameterizedTest
    @MethodSource("table")
    void oneSoldier(Set<Piece> worn, int copper, int iron, boolean room, int storesCopper, int storesIron, boolean ironIssued, String expected) {
        ArmourCheck.Verdict verdict = ArmourCheck.check(List.of(new Inventory("Hal", "Bravo", worn, copper, iron, room)),
                new Stores(storesCopper, storesIron), ironIssued).getFirst();
        assertEquals(expected, line(verdict));
    }

    private static String line(ArmourCheck.Verdict verdict) {
        return switch (verdict) {
            case ArmourCheck.Armoured a -> "Armoured";
            case ArmourCheck.Ordered o -> "Ordered " + o.pieces() + " " + o.fromStores();
            case ArmourCheck.Unable u -> "Unable " + u.why();
        };
    }

    /** The stores dealt out, least armoured first: no two sent for the same ingots, and the answers in the order asked. */
    @Test
    void theStoresAreDealtOutTheLeastArmouredFirst() {
        List<ArmourCheck.Verdict> verdicts = ArmourCheck.check(List.of(
                        new Inventory("Alice", "Alpha", EnumSet.of(Piece.CHESTPLATE, Piece.LEGGINGS, Piece.BOOTS), 0, 0, true),
                        new Inventory("Bob", "Alpha", NONE, 0, 0, true),
                        new Inventory("Dana", "Bravo", NONE, 0, 0, true)),
                new Stores(13, 0), false);
        assertEquals(List.of("Unable " + ArmourCheck.NO_METAL, "Ordered [CHESTPLATE, BOOTS] 2", "Unable " + ArmourCheck.NO_METAL),
                verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    /** One with no room takes none of the stores: the next is dealt them. */
    @Test
    void aFullPackLeavesTheStoresToTheNext() {
        List<ArmourCheck.Verdict> verdicts = ArmourCheck.check(List.of(
                        new Inventory("Alice", "Alpha", NONE, 0, 0, false),
                        new Inventory("Bob", "Alpha", NONE, 0, 0, true)),
                new Stores(8, 0), false);
        assertEquals(List.of("Unable " + ArmourCheck.NO_ROOM, "Ordered [CHESTPLATE] 1"), verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    @Test
    void saidAsTheSquadsReportAndThePlatoonLeadersOrder() {
        Inventory hal = new Inventory("Hal", "Bravo", NONE, 533, 0, true);
        assertEquals("[Bravo -> PL] Hal: armour 0 of 4 held, carries 533 copper, 0 iron; [PL -> Bravo] Hal: make and put on"
                        + " [chestplate, leggings, boots, helmet], from its pack",
                ArmourCheck.said(ArmourCheck.check(List.of(hal), new Stores(0, 0), false).getFirst(), hal));
        Inventory full = new Inventory("Hal", "Bravo", NONE, 533, 0, false);
        assertEquals("[Bravo -> PL] Hal: armour 0 of 4 held, carries 533 copper, 0 iron, its pack full; no armour ordered: "
                + ArmourCheck.NO_ROOM, ArmourCheck.said(ArmourCheck.check(List.of(full), new Stores(0, 0), false).getFirst(), full));
    }
}
