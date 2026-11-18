package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An enderman is fought only in self-defense (operator, 2026-11-16: engage if close, do not provoke). */
class EndermanSelfDefenseTest {

    private static final int GROUND = 64;

    private static WorldView withAnEnderman() {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(7, HostileMobRegistry.typeIdOf("enderman"), new Position(2.5, GROUND, 0.5)));
            }
        };
    }

    private static DeterministicSoldier armed() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, 0, false, false, 0, false));
        alice.reportHealth(20, 20);
        return alice;
    }

    /** One passing at hand isn't swung at: a hit provokes it. Live (field451), Eli swung at three and was killed. */
    @Test
    void anEndermanPassingAtHandIsLeftAlone() {
        DeterministicSoldier alice = armed();
        WorldView world = withAnEnderman();
        boolean struck = false;
        for (int tick = 0; tick < 40; tick++) {
            struck |= alice.tick(world).isPresent();
        }
        assertFalse(struck, "a passing enderman struck");
    }

    /** Hit, with one at hand: it's fought back. */
    @Test
    void anEndermanIsFoughtOnceItsHit() {
        DeterministicSoldier alice = armed();
        WorldView world = withAnEnderman();
        alice.reportHealth(14, 20);
        boolean struck = false;
        for (int tick = 0; tick < 40; tick++) {
            struck |= alice.tick(world).isPresent();
        }
        assertTrue(struck, "hit, and it not fought back");
    }
}
