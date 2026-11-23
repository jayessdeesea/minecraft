package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A soldier's habit with gates: opened on the way through, shut behind, never left open (operator, 2026-11-22). */
class GateHabitTest {

    /** A gate at (5, 64, 0) in a fence along x=5; the soldier walks east through it. */
    private boolean shut = true;
    private final List<String> uses = new ArrayList<>();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63;
        }

        @Override
        public boolean isGate(int x, int y, int z) {
            return x == 5 && y == 64 && z == 0;
        }

        @Override
        public boolean isShutGate(int x, int y, int z) {
            return isGate(x, y, z) && shut;
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

    /** A session that answers a use of the gate as the server does: it swings. */
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(), new Class<?>[]{PlaySession.class},
            (proxy, method, args) -> {
                if (method.getName().equals("useBlock")) {
                    uses.add(args[0] + "," + args[1] + "," + args[2] + (shut ? " opened" : " shut"));
                    shut = !shut;
                }
                return null;
            });

    /** The clock the habit reads: a tick is 50 ms. */
    private final long[] clock = {0};

    private void walk(GateHabit habit, double fromX, double toX, List<Position> others) {
        for (double x = fromX; x <= toX + 1e-9; x += 0.2) {
            clock[0] += 50;
            habit.tick(new Position(x, 64, 0.5), world, others);
        }
    }

    /** Walking up to a shut gate it opens it, once; through and clear of it, it shuts it behind. */
    @Test
    void aGateIsOpenedOnTheWayThroughAndShutBehind() {
        GateHabit habit = new GateHabit("Alice", session, () -> clock[0]);

        walk(habit, 1.5, 4.5, List.of());
        assertEquals(List.of("5,64,0 opened"), uses, "opened as it walks up, and only once");
        assertFalse(shut);

        walk(habit, 4.7, 9.0, List.of());
        assertEquals(List.of("5,64,0 opened", "5,64,0 shut"), uses, "and shut behind it");
        assertTrue(shut);
        assertEquals(1, habit.opened());
        assertEquals(1, habit.shut());
    }

    /** A gate somebody is in is left open: the one clear of it doesn't shut it on them. */
    @Test
    void aGateSomebodyIsInIsLeftOpen() {
        GateHabit habit = new GateHabit("Alice", session, () -> clock[0]);
        shut = false;

        walk(habit, 7.0, 8.0, List.of(new Position(5.5, 64, 0.5)));
        assertEquals(List.of(), uses, "Bob in the gate: left open");

        walk(habit, 8.0, 8.4, List.of(new Position(2.0, 64, 0.5)));
        assertEquals(List.of("5,64,0 shut"), uses, "Bob through and gone: shut");
    }

    /** A gate found open, by a soldier with no way through it to walk, is shut in passing; a shut one it walks along is left shut. */
    @Test
    void aGateFoundOpenIsShutInPassingAndAShutOneLeftShut() {
        GateHabit habit = new GateHabit("Alice", session, () -> clock[0]);
        shut = false;
        for (double z = -3; z <= 3; z += 0.2) {
            clock[0] += 50;
            habit.tick(new Position(7.5, 64, z), world, List.of());
        }
        assertEquals(List.of("5,64,0 shut"), uses, "walking past, two blocks off: shut, and then left shut");
    }

    /**
     * Two soldiers walking up to one shut gate open it once between them: a use swings a gate whichever way it stands, so
     * the second's, the same tick, would shut it on the first - who, in the squad simulation, broke it to get out.
     */
    @Test
    void twoSoldiersAtOneGateOpenItOnce() {
        java.util.Map<io.github.jayessdeesea.minecraft.bot.nav.BlockPos, Long> theForces = new java.util.concurrent.ConcurrentHashMap<>();
        GateHabit alice = new GateHabit("Alice", session, theForces, () -> clock[0]), bob = new GateHabit("Bob", session, theForces, () -> clock[0]);
        for (double x = 1.5; x <= 4.5; x += 0.2) {
            clock[0] += 50;
            alice.tick(new Position(x, 64, 0.5), world, List.of(new Position(x, 64, 1.4)));
            bob.tick(new Position(x, 64, 1.4), world, List.of(new Position(x, 64, 0.5)));
        }
        assertEquals(List.of("5,64,0 opened"), uses, "opened once, and left open for both");
        assertFalse(shut);
    }
}
