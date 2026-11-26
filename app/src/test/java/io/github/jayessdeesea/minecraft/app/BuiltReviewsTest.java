package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.ReviewOrders;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How app.BuiltReviews holds the reviews (docs/echelons/platoon-leader/decisions.md, "What's built is reviewed, by order"). */
class BuiltReviewsTest {

    private static final long NOW = 100 * ReviewOrders.DAY_MILLIS;

    /** A chunk built before a restart comes due in the first day after, spread by its name: not every one at once. */
    @Test
    void theChunksBuiltBeforeARestartComeDueSpreadOverTheFirstDay() {
        BuiltReviews reviews = new BuiltReviews();
        FarmMission a = FarmMission.atSeaLevel(1, 3), b = FarmMission.atSeaLevel(2, 3);
        for (FarmMission m : List.of(a, b)) {
            long due = reviews.reviewedAt(m, NOW) + ReviewOrders.DUE;
            assertTrue(due >= NOW && due < NOW + ReviewOrders.DAY_MILLIS, m.name() + " due in the first day: " + (due - NOW));
        }
        assertNotEquals(reviews.reviewedAt(a, NOW), reviews.reviewedAt(b, NOW), "two chunks come due apart");
        assertEquals(reviews.reviewedAt(a, NOW), reviews.reviewedAt(a, NOW + 5_000), "and once seen, it stays as seen");
    }

    /** An urgent review takes the squad near it off its step by day; by night the squad keeps the step. */
    @Test
    void byNightASquadOnAStepIsNotTakenOffItForAReview() {
        FarmMission built = FarmMission.atSeaLevel(1, 3);
        Objective step = new Objective("A flatten", new Position(40, 64, 40), "");
        for (boolean night : List.of(true, false)) {
            BuiltReviews reviews = new BuiltReviews();
            reviews.built(built, NOW - ReviewOrders.URGENT);
            List<Objective> out = new ArrayList<>(List.of(step));
            Set<Integer> held = new HashSet<>();
            reviews.deal(out, List.of(squadAt("Alpha", 30, 60)), held, Set.of(0), Set.of(), night, List.of(built), NOW);
            assertEquals(night ? step : BuiltReviews.objective(built), out.getFirst(), night ? "by night, the step kept" : "by day, the review");
            assertEquals(night ? Set.of() : Set.of(0), held);
        }
    }

    /** A squad of one soldier standing at block ({@code x}, {@code z}). */
    private static SquadStatus squadAt(String name, double x, double z) {
        return new SquadStatus(name, new Report.InPosition(), List.of(new SoldierStatus(name + "-1", new Position(x, 64, z), new Order.Idle(),
                new Report.InPosition(), Optional.empty())), List.of(), Optional.empty(), false);
    }
}
