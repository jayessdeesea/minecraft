package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.jayessdeesea.minecraft.command.ReviewOrders.DAY_MILLIS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The table of evals of {@link ReviewOrders} (operator, 2026-11-26: anti-entropy is ordered by the platoon leader - the
 * squads review the constructed chunks near them, each at least once every four days, and put right what's found).
 * Each row: the constructed chunks with when each was last reviewed, the squads with where they stand and what they
 * hold, the reviews open - and the reviews that stand after the decision.
 */
class ReviewOrdersTest {

    private static final long NOW = 100 * DAY_MILLIS;

    private static ReviewOrders.Site site(String name, int chunkX, int chunkZ, double daysAgo) {
        return new ReviewOrders.Site(name, chunkX, chunkZ, NOW - (long) (daysAgo * DAY_MILLIS));
    }

    /** A squad of one soldier standing in chunk ({@code chunkX}, {@code chunkZ}). */
    private static ReviewOrders.Squad squad(String name, int chunkX, int chunkZ, ReviewOrders.Standing standing) {
        return new ReviewOrders.Squad(name, List.of(new Position(chunkX * 16 + 8.5, 64, chunkZ * 16 + 8.5)), standing);
    }

    private static ReviewOrders.Order order(String squad, String site, double daysAgo) {
        return new ReviewOrders.Order(squad, site, NOW - (long) (daysAgo * DAY_MILLIS));
    }

    private static final ReviewOrders.Standing FREE = ReviewOrders.Standing.FREE, STEP = ReviewOrders.Standing.ON_A_STEP,
            HELD = ReviewOrders.Standing.HELD;

    private record Row(String what, List<ReviewOrders.Site> sites, List<ReviewOrders.Squad> squads, List<ReviewOrders.Order> open,
                       List<ReviewOrders.Order> gaveUp, List<String> expected) {
    }

    /** The reviews standing after the decision, each said "squad -> site". */
    private static List<String> said(ReviewOrders.Decision decision) {
        return decision.orders().stream().map(o -> o.squad() + " -> " + o.site()).toList();
    }

    @Test
    void theReviewsOrdered() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("reviewed two days ago: not due", List.of(site("A", 0, 0, 2)), List.of(squad("Alpha", 1, 0, FREE)),
                List.of(), List.of(), List.of()));
        rows.add(new Row("due at three days: the free squad near it ordered to it", List.of(site("A", 0, 0, 3)), List.of(squad("Alpha", 3, 0, FREE)),
                List.of(), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("eight chunks off is in reach", List.of(site("A", 0, 0, 3)), List.of(squad("Alpha", 8, -8, FREE)),
                List.of(), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("nine chunks off is out of reach: no outpost reviewed with nobody near it", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 9, 0, FREE)), List.of(), List.of(), List.of()));
        rows.add(new Row("the nearer free squad", List.of(site("A", 0, 0, 3)), List.of(squad("Alpha", 5, 0, FREE), squad("Bravo", 0, 2, FREE)),
                List.of(), List.of(), List.of("Bravo -> A")));
        rows.add(new Row("as near: the first in the order of battle", List.of(site("A", 0, 0, 3)), List.of(squad("Alpha", 2, 0, FREE), squad("Bravo", 0, -2, FREE)),
                List.of(), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("the nearest of its soldiers counts", List.of(site("A", 0, 0, 3)),
                List.of(new ReviewOrders.Squad("Alpha", List.of(new Position(200.5, 64, 0.5), new Position(20.5, 64, 0.5)), FREE), squad("Bravo", 2, 0, FREE)),
                List.of(), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("one review a squad: the longest unreviewed first, the other waits",
                List.of(site("A", 0, 0, 3.2), site("B", 1, 0, 3.6)), List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(), List.of("Alpha -> B")));
        rows.add(new Row("two squads, two reviews", List.of(site("A", 0, 0, 3.2), site("B", 1, 0, 3.6)),
                List.of(squad("Alpha", 0, 0, FREE), squad("Bravo", 1, 0, FREE)), List.of(), List.of(), List.of("Bravo -> B", "Alpha -> A")));
        rows.add(new Row("due, only a squad on a mission's step near: it keeps its step", List.of(site("A", 0, 0, 3.4)),
                List.of(squad("Alpha", 1, 0, STEP)), List.of(), List.of(), List.of()));
        rows.add(new Row("three and a half days: the squad on a step is taken off it for the review", List.of(site("A", 0, 0, 3.5)),
                List.of(squad("Alpha", 1, 0, STEP)), List.of(), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("urgent, a free squad in reach goes before a nearer one on a step", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, STEP), squad("Bravo", 6, 0, FREE)), List.of(), List.of(), List.of("Bravo -> A")));
        rows.add(new Row("a squad held - the food, the operator's scouting - never", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Delta", 0, 0, HELD)), List.of(), List.of(), List.of()));
        rows.add(new Row("a review open is kept, not dealt again to a nearer squad", List.of(site("A", 0, 0, 3.3)),
                List.of(squad("Alpha", 6, 0, FREE), squad("Bravo", 0, 0, FREE)), List.of(order("Alpha", "A", 0.1)), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("a squad reviewing is given no second", List.of(site("A", 0, 0, 3.3), site("B", 1, 0, 3.3)),
                List.of(squad("Alpha", 1, 0, FREE)), List.of(order("Alpha", "A", 0.1)), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("a review open is kept though its site's no longer due", List.of(site("A", 0, 0, 0)),
                List.of(squad("Alpha", 1, 0, FREE)), List.of(order("Alpha", "A", 0.1)), List.of(), List.of("Alpha -> A")));
        rows.add(new Row("a review whose site is gone - superseded, cancelled - is dropped", List.of(),
                List.of(squad("Alpha", 1, 0, FREE)), List.of(order("Alpha", "A", 0.1)), List.of(), List.of()));
        rows.add(new Row("a review whose squad is gone is dropped, and dealt again", List.of(site("A", 0, 0, 3.3)),
                List.of(squad("Bravo", 1, 0, FREE)), List.of(order("Alpha", "A", 0.1)), List.of(), List.of("Bravo -> A")));
        rows.add(new Row("a review not reached in a day is given up and dealt to another squad", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE), squad("Bravo", 4, 0, FREE)), List.of(order("Alpha", "A", 1)), List.of(), List.of("Bravo -> A")));
        rows.add(new Row("and not given back to the squad that gave it up", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(order("Alpha", "A", 1)), List.of()));
        rows.add(new Row("a day after it gave it up, the squad may be given it again", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(order("Alpha", "A", 2)), List.of("Alpha -> A")));
        rows.add(new Row("given up twice, the squad isn't ordered to it again: the same try isn't made a third time", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(order("Alpha", "A", 4), order("Alpha", "A", 2)), List.of()));
        rows.add(new Row("given up twice by one squad, another near is still ordered to it", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE), squad("Bravo", 3, 0, FREE)), List.of(), List.of(order("Alpha", "A", 4), order("Alpha", "A", 2)),
                List.of("Bravo -> A")));
        rows.add(new Row("given up once each at two sites: the squad may be given either again", List.of(site("A", 0, 0, 3.9)),
                List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(order("Alpha", "A", 2), order("Alpha", "B", 2)), List.of("Alpha -> A")));
        rows.add(new Row("a squad put on the operator's scouting is taken off the review it held, and it's dealt again",
                List.of(site("A", 0, 0, 3.3)), List.of(squad("Alpha", 0, 0, HELD), squad("Bravo", 2, 0, FREE)), List.of(order("Alpha", "A", 0.1)),
                List.of(), List.of("Bravo -> A")));
        rows.add(new Row("a review past its day is given up though its squad is held now - by night on a step: not dropped uncounted",
                List.of(site("A", 0, 0, 3.9)), List.of(squad("Alpha", 0, 0, HELD)), List.of(order("Alpha", "A", 1)), List.of(order("Alpha", "A", 3)),
                List.of()));
        rows.add(new Row("a squad with nobody in the world reaches nothing", List.of(site("A", 0, 0, 3.9)),
                List.of(new ReviewOrders.Squad("Alpha", List.of(), FREE)), List.of(), List.of(), List.of()));
        for (Row row : rows) {
            assertEquals(row.expected(), said(ReviewOrders.decide(row.sites(), row.squads(), row.open(), row.gaveUp(), NOW)), row.what());
        }
    }

    /** A review past its day is given up, and counted, though its squad is held when the day runs out. */
    @Test
    void aReviewPastItsDayIsGivenUpThoughItsSquadIsHeld() {
        ReviewOrders.Order stale = order("Alpha", "A", 1);
        assertEquals(List.of(stale), ReviewOrders.decide(List.of(site("A", 0, 0, 3.9)), List.of(squad("Alpha", 0, 0, HELD)), List.of(stale), List.of(),
                NOW).givenUp());
    }

    /** What's given up is said, once, as the decision that gave it up. */
    @Test
    void aReviewGivenUpIsSaid() {
        ReviewOrders.Order stale = order("Alpha", "A", 1);
        ReviewOrders.Decision decision = ReviewOrders.decide(List.of(site("A", 0, 0, 3.9)), List.of(squad("Alpha", 0, 0, FREE)),
                List.of(stale), List.of(), NOW);

        assertEquals(List.of(stale), decision.givenUp());
        assertEquals(List.of(), decision.orders(), "the squad that gave it up isn't ordered to it again");
    }

    /** A new review is ordered as of now, which its give-up counts from. */
    @Test
    void aNewReviewIsOrderedNow() {
        ReviewOrders.Decision decision = ReviewOrders.decide(List.of(site("A", 0, 0, 3)), List.of(squad("Alpha", 0, 0, FREE)), List.of(), List.of(), NOW);

        assertEquals(List.of(new ReviewOrders.Order("Alpha", "A", NOW)), decision.orders());
    }
}
