package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LinkUpTest {

    private static final Order SLOT = new Order.MoveTo(0, 64, 0, 3, 3, Risk.LOW);

    private static SoldierStatus soldier(String name, double x, double z, Report report, int deaths) {
        return new SoldierStatus(name, new Position(x, 64, z), new Order.Idle(), report,
                Optional.of(new Condition(20, 20, deaths)));
    }

    private static List<Order> slots(int n) {
        return java.util.Collections.nCopies(n, SLOT);
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

    @Test
    void aWholeSquadRespawningReformsAsItStands() {
        LinkUp linkUp = new LinkUp();
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, 0, new Report.Idle(), 1), soldier("Bob", 2, 2, new Report.Idle(), 1));

        assertEquals(squad, linkUp.reconstitute(squad));
    }
}
