package io.github.jayessdeesea.minecraft.bot;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the sword's sweep is struck, and when a swing that doesn't sweep is (operator, 2026-11-21). */
class StrikeTest {

    private static final Strike.Weapon SWORD = Strike.Weapon.of(Optional.of("stone_sword"));
    private static final Strike.Weapon HAND = Strike.Weapon.of(Optional.empty());

    @Test
    void withNoSquadmateInItsBoxTheSwordSweepsAtFullCharge() {
        assertEquals(Strike.Kind.WAIT, Strike.choose(SWORD, new Strike.Situation(5, false, 1, true)));
        assertEquals(Strike.Kind.FULL, Strike.choose(SWORD, new Strike.Situation(13, false, 2, true)), "two others in the box: the sweep");
        assertTrue(Strike.sweeps(SWORD, Strike.Kind.FULL));
    }

    @Test
    void withASquadmateInItsBoxAndRoomToSprintItsAKnockbackHit() {
        assertEquals(Strike.Kind.WAIT, Strike.choose(SWORD, new Strike.Situation(9, true, 0, true)), "charging still");
        assertEquals(Strike.Kind.KNOCKBACK, Strike.choose(SWORD, new Strike.Situation(13, true, 0, true)));
        assertFalse(Strike.sweeps(SWORD, Strike.Kind.KNOCKBACK), "a knockback hit never sweeps");
    }

    @Test
    void withASquadmateInItsBoxAndNoSprintItsAShortHitOrTheChargeStartedOver() {
        int at = SWORD.shortTicks();
        assertTrue(SWORD.charge(at) <= Strike.STRONG && SWORD.charge(at + 1) > Strike.STRONG, "the short hit's tick: " + at);
        assertEquals(Strike.Kind.WAIT, Strike.choose(SWORD, new Strike.Situation(at - 1, true, 0, false)));
        assertEquals(Strike.Kind.SHORT, Strike.choose(SWORD, new Strike.Situation(at, true, 0, false)));
        assertEquals(Strike.Kind.RESET, Strike.choose(SWORD, new Strike.Situation(at + 1, true, 0, false)), "charged past it: started over");
        assertFalse(Strike.sweeps(SWORD, Strike.Kind.SHORT));
    }

    @Test
    void bareHandsNeverSweep() {
        assertEquals(Strike.Kind.FULL, Strike.choose(HAND, new Strike.Situation(13, true, 2, false)), "a squadmate beside it or not");
        assertFalse(Strike.sweeps(HAND, Strike.Kind.FULL));
    }

    @Test
    void aSweepsDamageIsOneAndItsRatioOfTheBlow() {
        assertEquals(1.0, Strike.sweepDamage(SWORD, 5), 1e-9, "unenchanted");
        assertEquals(1 + 0.5 * 5, Strike.sweepDamage(new Strike.Weapon(true, 0.5, 0, 1.6), 5), 1e-9, "Sweeping Edge I");
    }
}
