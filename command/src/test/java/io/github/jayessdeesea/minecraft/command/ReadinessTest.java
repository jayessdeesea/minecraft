package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadinessTest {

    private static final Position CAMP = new Position(0, 64, 0);
    /** A camp sheltered and lit, not yet walled: phase 1 met, phase 2 not. */
    private static final Foothold LIT = new Foothold(CAMP, List.of(
            new Foothold.Standard(1, "shelter", true, "", "PATROL_BASE"),
            new Foothold.Standard(2, "the camp's ground lit", true, "", "SECURE"),
            new Foothold.Standard(2, "the camp walled in", false, "not begun", "FORTIFY")));

    private static SoldierStatus soldier(double x, Optional<String> weapon, int armour, int food, double health) {
        return new SoldierStatus("s", new Position(x, 64, 0), new Order.Idle(), new Report.Idle(),
                Optional.of(new Condition(health, food, 0, Optional.of(new Kit(weapon, 0, armour)))));
    }

    private static SquadStatus squad(boolean sealedIn, SoldierStatus... soldiers) {
        return new SquadStatus("Alpha", new Report.Idle(), List.of(soldiers), List.of(), Optional.empty(), sealedIn);
    }

    @Test
    void safetyGoesWithTheGroundNotTheSquad() {
        SoldierStatus armed = soldier(0, Optional.of("iron_sword"), 4, 20, 20);
        SoldierStatus away = soldier(100, Optional.of("iron_sword"), 4, 20, 20);

        assertEquals(Readiness.Safety.SHELTER, Readiness.of(squad(false, armed), Optional.of(LIT)).safety(),
                "at the camp: its phase met, 1 - the walls not up");
        assertEquals(Readiness.Safety.EXPOSED, Readiness.of(squad(false, away), Optional.of(LIT)).safety(), "away from it");
        assertEquals(Readiness.Safety.SHELTER, Readiness.of(squad(true, away), Optional.of(LIT)).safety(), "sealed in where it is");
        assertEquals(Readiness.Safety.EXPOSED, Readiness.of(squad(false, armed), Optional.empty()).safety(), "no camp at all");
    }

    @Test
    void theRatingIsTheWorstOfArmedArmouredFedAndWhole() {
        SoldierStatus ready = soldier(0, Optional.of("iron_sword"), 4, 20, 20);
        SoldierStatus unarmoured = soldier(0, Optional.of("iron_sword"), 0, 20, 20);

        Readiness full = Readiness.of(squad(false, ready, ready, ready, ready), Optional.empty());
        assertEquals(1, full.rating());
        assertEquals("", full.shortfall());
        Readiness one = Readiness.of(squad(false, ready, ready, ready, unarmoured), Optional.empty());
        assertEquals(3, one.rating(), "three quarters armoured");
        assertEquals("4 of 16 armour slots empty", one.shortfall());
        assertEquals(4, Readiness.of(squad(false, soldier(0, Optional.empty(), 0, 5, 3)), Optional.empty()).rating());
    }
}
