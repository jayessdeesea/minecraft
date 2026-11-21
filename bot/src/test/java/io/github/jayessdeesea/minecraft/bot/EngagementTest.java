package io.github.jayessdeesea.minecraft.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Closing on a hostile by the exchange, not a health line (operator, 2026-11-21). */
class EngagementTest {

    private static final Engagement.Hostile SKELETON_7_OFF = new Engagement.Hostile(20, 4, 0.5, 15, 6.4);

    private static Engagement.Soldier stoneSword(double health, double armour) {
        return new Engagement.Soldier(health, armour, 0, 5, 1.6, 3);
    }

    @Test
    void aSoldierAtOneHealthTakesCoverFromASkeletonItWouldHaveToCross() {
        assertEquals(Engagement.Kind.COVER, Engagement.choose(stoneSword(1, 0), SKELETON_7_OFF, false, 9),
                "field611: Eli at 0.6 health closed on one 6.4 off and was shot dead");
    }

    @Test
    void aHealthySoldierClosesAndOneHurtInAFightBegunFinishesIt() {
        assertEquals(Engagement.Kind.ENGAGE, Engagement.choose(stoneSword(20, 0), SKELETON_7_OFF, false, 9));
        assertEquals(Engagement.Kind.COVER, Engagement.choose(stoneSword(6, 0), SKELETON_7_OFF, false, 9), "met at 6, unarmoured");
        assertEquals(Engagement.Kind.ENGAGE, Engagement.choose(stoneSword(8, 0), SKELETON_7_OFF, true, 9),
                "begun at 20 and down to 8: it still outlasts the kill (4 s to 3.3) - finished");
        assertEquals(Engagement.Kind.WITHDRAW, Engagement.choose(stoneSword(6, 0), SKELETON_7_OFF, true, 9),
                "down to 6 it dies first (3 s to 3.3), and 9 blocks clear costs a shot: it gets clear");
    }

    @Test
    void armourBuysTheMargin() {
        assertEquals(Engagement.Kind.ENGAGE, Engagement.choose(stoneSword(6, 15), SKELETON_7_OFF, false, 9), "iron: met at 6, it closes");
        assertTrue(stoneSword(20, 15).taken(4) < 4 * 0.75, "iron takes a quarter or more off an arrow");
    }

    @Test
    void begunAndDyingFirstItWithdrawsIfItCanGetClear() {
        Engagement.Hostile close = new Engagement.Hostile(20, 4, 0.5, 15, 3);
        assertEquals(Engagement.Kind.WITHDRAW, Engagement.choose(new Engagement.Soldier(3, 0, 0, 1, 1.6, 3), close, true, 4),
                "bare hands at 3 health: it can't kill it first, and 4 blocks clear costs one shot at most");
    }

    /**
     * One that follows is fought unless the soldier would lose outright and can get away: a zombie keeps pace with a walk,
     * so a walk doesn't get away; a sprint does, from one not yet on it.
     */
    @Test
    void aZombieThatKeepsPaceWithAWalkIsFoughtButASprintGetsClear() {
        Engagement.Hostile zombie = new Engagement.Hostile(20, 3, 1, 2, 5, 4);
        assertEquals(Engagement.Kind.ENGAGE, Engagement.choose(new Engagement.Soldier(20, 0, 0, 1, 4, 3), zombie, false, 4),
                "whole, bare hands outlast a zombie's kill (6.7 s to 5): fought - the matrix's zombie+zombie, bare hands, hills");
        assertEquals(Engagement.Kind.ENGAGE, Engagement.choose(new Engagement.Soldier(4, 0, 0, 1, 4, 3), zombie, false, 4),
                "at 4 it loses, but at a walk it can't get away: a fight forced on it");
        assertEquals(Engagement.Kind.COVER, Engagement.choose(new Engagement.Soldier(4, 0, 0, 1, 4, 3, Engagement.SPRINT), zombie, false, 4),
                "at a sprint it gets clear before the zombie's on it");
    }
}
