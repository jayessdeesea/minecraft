package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A goal no search reaches is searched wide less and less often, not every five seconds for good: commit 944's profile,
 * Eli at a cave's edge with no route to his gallery's next stand, a thirteenth of the garrison's time.
 */
class WideSearchBackoffTest {

    @Test
    void aGoalNoWideSearchReachesIsSearchedLessAndLessOften() {
        AtomicLong looks = new AtomicLong();
        // Open ground all round the soldier - plenty to search - and the goal 80 blocks off sealed in a box of stone.
        WorldView pocket = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                looks.incrementAndGet();
                boolean inside = Math.abs(x - 80) <= 1 && Math.abs(z) <= 1;
                return y < 64 || !inside && y <= 67 && Math.abs(x - 80) <= 2 && Math.abs(z) <= 2 || inside && y == 67;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        alice.receiveOrder(new Order.MoveTo(80.5, 64, 0.5, 1, 1, Risk.LOW));
        long[] perMinute = new long[5];
        for (int minute = 0; minute < perMinute.length; minute++) {
            long before = looks.get();
            for (int tick = 0; tick < 1200; tick++) {
                alice.tick(pocket);
            }
            perMinute[minute] = looks.get() - before;
        }
        System.out.println("[Backoff] world looks a minute: " + java.util.Arrays.toString(perMinute));
        // Searched wide every five seconds, a minute of it was 38 million looks at the world; backed off, 12 million.
        assertTrue(perMinute[4] < 20_000_000, "the fifth minute's planning, backed off: " + java.util.Arrays.toString(perMinute));
    }
}
