package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DeterministicSoldier} as a black box: construct it, feed it an order and a
 * fake {@code WorldView} ({@code terrain}/{@code withHostileAt} below), tick it, observe {@code
 * status()}. No Netty, no live server, no LLM -
 * see AGENTS.md's "Behavior modeling" section on why this decision core is built to allow exactly
 * this.
 */
class DeterministicSoldierTest {

    @Test
    void trendsTowardTargetWhenUnobstructed() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(10, 64, 10));
        alice.receiveOrder(new Order.TrendToward(0, 0));

        for (int i = 0; i < 10; i++) {
            alice.tick(terrain(false));
        }

        Position position = alice.status().position();
        assertTrue(position.x() < 10, "expected to have moved toward x=0, was " + position.x());
        assertTrue(position.z() < 10, "expected to have moved toward z=0, was " + position.z());
    }

    @Test
    void stopsWhenTheNextStepIsSolid() {
        DeterministicSoldier bob = new DeterministicSoldier("Bob", new Position(5, 64, 0));
        bob.receiveOrder(new Order.TrendToward(0, 0));

        bob.tick(terrain(true));

        assertEquals(new Position(5, 64, 0), bob.status().position());
    }

    @Test
    void idleOrderProducesNoMovement() {
        DeterministicSoldier idle = new DeterministicSoldier("Idle", new Position(1, 64, 1));

        idle.tick(terrain(false));

        assertEquals(new Position(1, 64, 1), idle.status().position());
    }

    @Test
    void retreatsDirectlyAwayFromANearbyHostileEvenWhenIdle() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        alice.tick(withHostileAt(new Position(3, 64, 0), false));

        Position position = alice.status().position();
        assertTrue(position.x() < 0, "expected to back away from x=3, was " + position.x());
        assertEquals(0, position.z(), 1e-9);
    }

    @Test
    void retreatOverridesAnOrderTowardTheThreat() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.TrendToward(10, 0));

        alice.tick(withHostileAt(new Position(3, 64, 0), false));

        assertTrue(alice.status().position().x() < 0, "should retreat, not follow the order toward the threat");
    }

    @Test
    void standsStillWhenTheRetreatStepIsBlocked() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.TrendToward(-10, 0));

        alice.tick(withHostileAt(new Position(3, 64, 0), true));

        assertEquals(new Position(0, 64, 0), alice.status().position());
    }

    @Test
    void ignoresHostilesOutsideContactRadius() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.TrendToward(10, 0));

        alice.tick(withHostileAt(new Position(DeterministicSoldier.CONTACT_RADIUS + 1, 64, 0), false));

        assertTrue(alice.status().position().x() > 0, "a distant hostile shouldn't interrupt the order");
    }

    @Test
    void serverCorrectionReplacesPositionOnTheNextTickInsteadOfMoving() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.TrendToward(10, 0));

        alice.correctPosition(new Position(100, 70, 100));
        alice.tick(terrain(false));
        assertEquals(new Position(100, 70, 100), alice.status().position());

        alice.tick(terrain(false));
        assertTrue(alice.status().position().x() < 100, "should resume following the order from the corrected spot");
    }

    /** A fake {@link WorldView} where every block is {@code solid} or not, with no hostiles. */
    private static WorldView terrain(boolean solid) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return solid;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    /** A fake {@link WorldView} with one hostile, honoring the radius the soldier asks for. */
    private static WorldView withHostileAt(Position hostile, boolean everythingSolid) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return everythingSolid;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                double distance = Math.sqrt(Math.pow(hostile.x() - center.x(), 2)
                        + Math.pow(hostile.y() - center.y(), 2) + Math.pow(hostile.z() - center.z(), 2));
                return distance <= radius ? List.of(new TrackedEntity(1, 151, hostile)) : List.of();
            }
        };
    }

    @Test
    void stopsAdvancingOnceCloseEnoughToTarget() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.1, 64, 0));
        alice.receiveOrder(new Order.TrendToward(0, 0));

        alice.tick(terrain(false));

        assertEquals(new Position(0.1, 64, 0), alice.status().position());
    }
}
