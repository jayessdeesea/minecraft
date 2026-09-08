package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DeterministicSoldier} as a black box: construct it, feed it an order and a
 * fake {@code WorldView}, tick it, observe {@code status()}. No Netty, no live server, no LLM -
 * see AGENTS.md's "Behavior modeling" section on why this decision core is built to allow exactly
 * this.
 */
class DeterministicSoldierTest {

    @Test
    void trendsTowardTargetWhenUnobstructed() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(10, 64, 10));
        alice.receiveOrder(new Order.TrendToward(0, 0));

        for (int i = 0; i < 10; i++) {
            alice.tick((x, y, z) -> false);
        }

        Position position = alice.status().position();
        assertTrue(position.x() < 10, "expected to have moved toward x=0, was " + position.x());
        assertTrue(position.z() < 10, "expected to have moved toward z=0, was " + position.z());
    }

    @Test
    void stopsWhenTheNextStepIsSolid() {
        DeterministicSoldier bob = new DeterministicSoldier("Bob", new Position(5, 64, 0));
        bob.receiveOrder(new Order.TrendToward(0, 0));

        bob.tick((x, y, z) -> true);

        assertEquals(new Position(5, 64, 0), bob.status().position());
    }

    @Test
    void idleOrderProducesNoMovement() {
        DeterministicSoldier idle = new DeterministicSoldier("Idle", new Position(1, 64, 1));

        idle.tick((x, y, z) -> false);

        assertEquals(new Position(1, 64, 1), idle.status().position());
    }

    @Test
    void stopsAdvancingOnceCloseEnoughToTarget() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.1, 64, 0));
        alice.receiveOrder(new Order.TrendToward(0, 0));

        alice.tick((x, y, z) -> false);

        assertEquals(new Position(0.1, 64, 0), alice.status().position());
    }
}
