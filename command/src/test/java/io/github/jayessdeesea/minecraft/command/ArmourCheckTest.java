package io.github.jayessdeesea.minecraft.command;

import io.github.jayessdeesea.minecraft.command.ArmourCheck.Inventory;
import io.github.jayessdeesea.minecraft.command.ArmourCheck.Issue;
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
    private static final Set<Piece> ARMOUR = EnumSet.of(Piece.CHESTPLATE, Piece.LEGGINGS, Piece.BOOTS, Piece.HELMET);
    /** The basis of issue (docs/military-doctrine.md § 11): copper armour only; iron armour too; the shield too; the shield and copper. */
    private static final Issue COPPER = new Issue(false, false), IRON = new Issue(true, false), BUILDUP = new Issue(true, true),
            SHIELD = new Issue(false, true);

    static Stream<Arguments> table() {
        return Stream.of(
                // held, copper, iron, room, stores copper, stores iron, issued -> verdict
                Arguments.of(ALL, 0, 0, true, 0, 0, IRON, "Armoured"),
                Arguments.of(ALL, 64, 64, false, 99, 99, IRON, "Armoured"),
                // Live (2026-11-26): Hal, 533 copper ingots, none on.
                Arguments.of(NONE, 533, 0, true, 0, 0, COPPER, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                Arguments.of(NONE, 24, 0, true, 0, 0, COPPER, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                // The best the metal allows: 12 makes a chestplate and boots, not the leggings after the chestplate.
                Arguments.of(NONE, 12, 0, true, 0, 0, COPPER, "Ordered [CHESTPLATE, BOOTS] 0"),
                Arguments.of(NONE, 4, 0, true, 0, 0, COPPER, "Ordered [BOOTS] 0"),
                Arguments.of(NONE, 3, 0, true, 0, 0, COPPER, "Unable " + ArmourCheck.NO_METAL),
                // Only the slots empty: boots and helmet on, chestplate and leggings ordered.
                Arguments.of(EnumSet.of(Piece.BOOTS, Piece.HELMET), 15, 0, true, 0, 0, COPPER, "Ordered [CHESTPLATE, LEGGINGS] 0"),
                // Pack first, then the stores.
                Arguments.of(NONE, 8, 0, true, 16, 0, COPPER, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 3"),
                Arguments.of(NONE, 0, 0, true, 24, 0, COPPER, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 4"),
                // Pack and stores together for a piece neither pays for alone, as a draw makes it up: 3 and 2 make boots.
                Arguments.of(NONE, 3, 0, true, 2, 0, COPPER, "Ordered [BOOTS] 1"),
                // Iron only where the camp's level issues it.
                Arguments.of(NONE, 0, 24, true, 0, 0, COPPER, "Unable " + ArmourCheck.NO_METAL),
                Arguments.of(NONE, 0, 24, true, 0, 0, IRON, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                Arguments.of(NONE, 0, 0, true, 0, 30, COPPER, "Unable " + ArmourCheck.NO_METAL),
                Arguments.of(NONE, 0, 0, true, 0, 30, IRON, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 4"),
                // A piece is one metal: 6 copper and 6 iron make boots and a helmet, not a chestplate.
                Arguments.of(NONE, 6, 6, true, 0, 0, IRON, "Ordered [BOOTS, HELMET] 0"),
                // No room: nothing ordered, and said - the full pack is the fault (Crafter: "no room in its pack").
                Arguments.of(NONE, 533, 0, false, 0, 0, COPPER, "Unable " + ArmourCheck.NO_ROOM),
                Arguments.of(NONE, 0, 0, false, 0, 0, COPPER, "Unable " + ArmourCheck.NO_METAL),
                // The shield, one iron ingot, in the off hand - where the camp's level issues it, as iron armour.
                // Live (2026-11-26): armoured in copper, no iron carried, 384 iron ingots in the stores, at SHELTER.
                Arguments.of(ARMOUR, 0, 0, true, 0, 384, COPPER, "Armoured"),
                Arguments.of(ARMOUR, 0, 0, true, 0, 384, SHIELD, "Ordered [SHIELD] 1"),
                Arguments.of(ARMOUR, 0, 1, true, 0, 0, BUILDUP, "Ordered [SHIELD] 0"),
                Arguments.of(ALL, 0, 0, true, 0, 384, BUILDUP, "Armoured"),
                // Iron only: copper pays for no shield.
                Arguments.of(ARMOUR, 50, 0, true, 99, 0, SHIELD, "Unable " + ArmourCheck.NO_METAL),
                // No iron for it: the shield passed over, the armour the copper pays for ordered.
                Arguments.of(NONE, 24, 0, true, 0, 0, SHIELD, "Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0"),
                // The shield issued and iron armour not: the iron carried makes the shield, not a piece.
                Arguments.of(NONE, 0, 24, true, 0, 0, SHIELD, "Ordered [SHIELD] 0"),
                // Both: the shield first, as the ladder climbs - 9 iron a shield and a chestplate.
                Arguments.of(NONE, 0, 9, true, 0, 0, BUILDUP, "Ordered [SHIELD, CHESTPLATE] 0"),
                Arguments.of(ARMOUR, 0, 0, false, 0, 384, SHIELD, "Unable " + ArmourCheck.NO_ROOM));
    }

    @ParameterizedTest
    @MethodSource("table")
    void oneSoldier(Set<Piece> worn, int copper, int iron, boolean room, int storesCopper, int storesIron, Issue issued, String expected) {
        ArmourCheck.Verdict verdict = ArmourCheck.check(List.of(new Inventory("Hal", "Bravo", worn, copper, iron, 8, room)),
                new Stores(storesCopper, storesIron), issued).getFirst();
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
                        new Inventory("Alice", "Alpha", EnumSet.of(Piece.CHESTPLATE, Piece.LEGGINGS, Piece.BOOTS), 0, 0, 8, true),
                        new Inventory("Bob", "Alpha", NONE, 0, 0, 8, true),
                        new Inventory("Dana", "Bravo", NONE, 0, 0, 8, true)),
                new Stores(13, 0), COPPER);
        assertEquals(List.of("Unable " + ArmourCheck.NO_METAL, "Ordered [CHESTPLATE, BOOTS] 2", "Unable " + ArmourCheck.NO_METAL),
                verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    /** The shield's planks are the pack's: without them it's passed over for the armour, or said if it's all that's missing. */
    @Test
    void aShieldWithNoWoodInThePackIsPassedOver() {
        List<ArmourCheck.Verdict> verdicts = ArmourCheck.check(List.of(
                        new Inventory("Alice", "Alpha", NONE, 24, 1, 5, true),
                        new Inventory("Bob", "Alpha", ARMOUR, 0, 1, 5, true),
                        new Inventory("Carol", "Alpha", ARMOUR, 0, 1, 8, true)),
                new Stores(0, 0), BUILDUP);
        assertEquals(List.of("Ordered [CHESTPLATE, LEGGINGS, BOOTS, HELMET] 0", "Unable " + ArmourCheck.NO_WOOD, "Ordered [SHIELD] 0"),
                verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    /** A shield where it isn't issued counts for nothing in the deal: least armoured is of what's issued. */
    @Test
    void aShieldNotIssuedDoesntCountInTheDeal() {
        List<ArmourCheck.Verdict> verdicts = ArmourCheck.check(List.of(
                        new Inventory("Abe", "Alpha", EnumSet.of(Piece.HELMET), 0, 0, 8, true),
                        new Inventory("Zed", "Alpha", EnumSet.of(Piece.SHIELD), 0, 0, 8, true)),
                new Stores(8, 0), COPPER);
        assertEquals(List.of("Unable " + ArmourCheck.NO_METAL, "Ordered [CHESTPLATE] 1"), verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    /** One with no room takes none of the stores: the next is dealt them. */
    @Test
    void aFullPackLeavesTheStoresToTheNext() {
        List<ArmourCheck.Verdict> verdicts = ArmourCheck.check(List.of(
                        new Inventory("Alice", "Alpha", NONE, 0, 0, 8, false),
                        new Inventory("Bob", "Alpha", NONE, 0, 0, 8, true)),
                new Stores(8, 0), COPPER);
        assertEquals(List.of("Unable " + ArmourCheck.NO_ROOM, "Ordered [CHESTPLATE] 1"), verdicts.stream().map(ArmourCheckTest::line).toList());
    }

    @Test
    void saidAsTheSquadsReportAndThePlatoonLeadersOrder() {
        Inventory hal = new Inventory("Hal", "Bravo", NONE, 533, 0, 8, true);
        assertEquals("[Bravo -> PL] Hal: armour 0 of 4 held, no shield, carries 533 copper, 0 iron; [PL -> Bravo] Hal: make and put on"
                        + " [chestplate, leggings, boots, helmet], from its pack",
                ArmourCheck.said(ArmourCheck.check(List.of(hal), new Stores(0, 0), COPPER).getFirst(), hal));
        Inventory full = new Inventory("Hal", "Bravo", NONE, 533, 0, 8, false);
        assertEquals("[Bravo -> PL] Hal: armour 0 of 4 held, no shield, carries 533 copper, 0 iron, its pack full; no armour ordered: "
                + ArmourCheck.NO_ROOM, ArmourCheck.said(ArmourCheck.check(List.of(full), new Stores(0, 0), COPPER).getFirst(), full));
        Inventory eli = new Inventory("Eli", "Bravo", ARMOUR, 0, 0, 8, true);
        assertEquals("[Bravo -> PL] Eli: armour 4 of 4 held, no shield, carries 0 copper, 0 iron; [PL -> Bravo] Eli: make and put on"
                + " [shield], 1 of the stores' metal", ArmourCheck.said(ArmourCheck.check(List.of(eli), new Stores(0, 9), SHIELD).getFirst(), eli));
        Inventory gus = new Inventory("Gus", "Alpha", ALL, 0, 0, 8, true);
        assertEquals("[Alpha -> PL] Gus: armour 4 of 4 held and a shield, carries 0 copper, 0 iron; armoured",
                ArmourCheck.said(ArmourCheck.check(List.of(gus), new Stores(0, 0), SHIELD).getFirst(), gus));
    }
}
