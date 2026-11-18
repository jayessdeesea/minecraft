package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LinkUpTest {

    private static final Order SLOT = new Order.MoveTo(0, 64, 0, 3, 3, Risk.LOW);

    private static SoldierStatus soldier(String name, double x, double z, Report report, int deaths) {
        return new SoldierStatus(name, new Position(x, 64, z), new Order.Idle(), report,
                Optional.of(new Condition(20, 20, deaths, Optional.empty())));
    }

    private static List<Order> slots(int n) {
        return Collections.nCopies(n, SLOT);
    }

    @Test
    void aSquadThatHasNotLostAnyoneIsUnchanged() {
        LinkUp linkUp = new LinkUp();
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, 0, new Report.Moving(), 0),
                soldier("Bob", 3, 3, new Report.Moving(), 0));

        List<SoldierStatus> formed = linkUp.reconstitute(squad);

        assertEquals(squad, formed);
        assertEquals(slots(2), linkUp.orders(formed, slots(2)));
    }

    /**
     * A one-man unit's soldier, respawned, has nobody to link up with: it keeps its own order. Live,
     * a farmer alone in its detachment died, and its orders failed every tick until it rejoined.
     */
    /**
     * After dark a replacement far from its squad holds where it respawned till dawn, and closes on it by
     * day: live, three killed at the mine's faces respawned at the keep and were shot crossing the dark.
     */
    @Test
    void afterDarkAFarReplacementWaitsForDawn() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 100, 0, new Report.Moving(), 0), soldier("Bob", 103, 0, new Report.Moving(), 0)));
        List<SoldierStatus> formed = linkUp.reconstitute(List.of(
                soldier("Alice", 0, 0, new Report.Idle(), 1), soldier("Bob", 103, 0, new Report.Moving(), 0)));
        Order.MoveTo night = (Order.MoveTo) linkUp.orders(formed, slots(2), n -> false, true).get(formed.indexOf(
                formed.stream().filter(s -> s.name().equals("Alice")).findFirst().orElseThrow()));
        assertEquals(0, night.x(), 0.01, "it holds where it respawned");
        Order.MoveTo day = (Order.MoveTo) linkUp.orders(formed, slots(2), n -> false, false).get(formed.indexOf(
                formed.stream().filter(s -> s.name().equals("Alice")).findFirst().orElseThrow()));
        assertEquals(103, day.x(), 0.01, "by day it closes on its squad");
    }

    /** Killed again where it waited, it waits no more: it links up across the dark rather than die at the spawn again. */
    @Test
    void afterDarkAReplacementKilledWhereItWaitedLinksUp() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 100, 0, new Report.Moving(), 0), soldier("Bob", 103, 0, new Report.Moving(), 0)));
        List<SoldierStatus> formed = linkUp.reconstitute(List.of(
                soldier("Alice", 0, 0, new Report.Idle(), 2), soldier("Bob", 103, 0, new Report.Moving(), 0)));
        int alice = formed.indexOf(formed.stream().filter(s -> s.name().equals("Alice")).findFirst().orElseThrow());
        Order.MoveTo order = (Order.MoveTo) linkUp.orders(formed, slots(2), n -> false, true).get(alice);
        assertEquals(103, order.x(), 0.01, "twice dead: it closes on its squad");
    }

    @Test
    void aLoneReplacementKeepsItsOwnOrder() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Eli", 100, 100, new Report.Moving(), 0)));

        List<SoldierStatus> formed = linkUp.reconstitute(List.of(soldier("Eli", 0, 0, new Report.Idle(), 1)));

        assertEquals(slots(1), linkUp.orders(formed, slots(1)));
    }

    @Test
    void aDeadPointManGoesToTheRearAndTheNextSoldierTakesPoint() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 100, 100, new Report.Moving(), 0), soldier("Bob", 103, 103, new Report.Moving(), 0)));

        List<SoldierStatus> formed = linkUp.reconstitute(List.of(
                soldier("Alice", 0, 0, new Report.Idle(), 1), soldier("Bob", 103, 103, new Report.Moving(), 0)));

        assertEquals("Bob", formed.get(0).name());
        assertEquals("Alice", formed.get(1).name());
        assertInstanceOf(Report.LinkingUp.class, formed.get(1).report());
    }

    @Test
    void aReplacementHoldsAtTheRallyPointWhileTheSquadIsInContact() {
        LinkUp linkUp = new LinkUp();
        // Last secure position: Alice at (100, 100). Then Bob dies, and Alice comes under attack at (120, 100).
        linkUp.reconstitute(List.of(soldier("Alice", 100, 100, new Report.Moving(), 0), soldier("Bob", 103, 103, new Report.Moving(), 0)));
        List<SoldierStatus> formed = linkUp.reconstitute(List.of(
                soldier("Alice", 120, 100, new Report.InContact(2), 0), soldier("Bob", 0, 0, new Report.Idle(), 1)));

        Order.MoveTo bobs = (Order.MoveTo) linkUp.orders(formed, slots(2)).get(1);

        assertEquals(100, bobs.x());
        assertEquals(100, bobs.z());
    }

    @Test
    void onceContactClearsTheReplacementClosesOnTheSquadThenRejoinsTheFormation() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 100, 100, new Report.Moving(), 0), soldier("Bob", 103, 103, new Report.Moving(), 0)));

        List<SoldierStatus> away = linkUp.reconstitute(List.of(
                soldier("Alice", 120, 100, new Report.Moving(), 0), soldier("Bob", 0, 0, new Report.Idle(), 1)));
        Order.MoveTo closing = (Order.MoveTo) linkUp.orders(away, slots(2)).get(1);
        assertEquals(120, closing.x(), "closes on Alice, not the rally point, once nobody is in contact");

        List<SoldierStatus> arrived = linkUp.reconstitute(List.of(
                soldier("Alice", 120, 100, new Report.Moving(), 0), soldier("Bob", 116, 100, new Report.Moving(), 1)));
        assertInstanceOf(Report.Moving.class, arrived.get(1).report(), "linked up within 6 blocks");
        assertEquals(slots(2), linkUp.orders(arrived, slots(2)));
    }

    @Test
    void aReplacementDoesNotCloseOnAStuckSoldier() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 160, 150, new Report.Moving(), 0), soldier("Bob", 163, 153, new Report.Moving(), 0),
                soldier("Carol", 84, 29, new Report.Cantco("no route out"), 0)));
        List<SoldierStatus> formed = linkUp.reconstitute(List.of(soldier("Alice", 160, 150, new Report.Moving(), 0),
                soldier("Bob", 0, 0, new Report.Idle(), 1), soldier("Carol", 84, 29, new Report.Cantco("no route out"), 0)));

        Order.MoveTo bobs = (Order.MoveTo) linkUp.orders(formed, slots(3)).get(2);

        assertEquals(160, bobs.x(), "closes on Alice, though stuck Carol is nearer");

        List<SoldierStatus> passing = linkUp.reconstitute(List.of(soldier("Alice", 160, 150, new Report.Moving(), 0),
                soldier("Bob", 86, 31, new Report.Moving(), 1), soldier("Carol", 84, 29, new Report.Cantco("no route out"), 0)));
        assertInstanceOf(Report.LinkingUp.class, passing.get(2).report(), "passing stuck Carol isn't linking up");
    }

    /**
     * Nor on one the recovery drill has, whatever it reports: live, a soldier rejoining closed on a
     * builder in a dead end at a mine's bottom, reporting the dig it couldn't reach, and stood on the
     * stair above him at "no route".
     */
    @Test
    void aReplacementDoesNotCloseOnOneTheRecoveryDrillHas() {
        LinkUp linkUp = new LinkUp();
        linkUp.reconstitute(List.of(soldier("Alice", 160, 150, new Report.Moving(), 0), soldier("Bob", 163, 153, new Report.Moving(), 0),
                soldier("Carol", 84, 29, new Report.Working("dig the keep", 77), 0)));
        List<SoldierStatus> formed = linkUp.reconstitute(List.of(soldier("Alice", 160, 150, new Report.Moving(), 0),
                soldier("Bob", 0, 0, new Report.Idle(), 1), soldier("Carol", 84, 29, new Report.Working("dig the keep", 77), 0)));

        Order.MoveTo bobs = (Order.MoveTo) linkUp.orders(formed, slots(3), "Carol"::equals).get(2);

        assertEquals(160, bobs.x(), "closes on Alice, though Carol, in the drill's hands, is nearer");
    }

    @Test
    void aWholeSquadRespawningReformsAsItStands() {
        LinkUp linkUp = new LinkUp();
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, 0, new Report.Idle(), 1), soldier("Bob", 2, 2, new Report.Idle(), 1));

        assertEquals(squad, linkUp.reconstitute(squad));
    }
}
