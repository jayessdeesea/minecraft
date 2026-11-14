package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearerTest {

    private static final int GROUND = 63;
    /** A block of dirt on the ground, beside where the soldier stands, and one floating out of reach. */
    private static final BlockPos MOUND = new BlockPos(2, GROUND + 1, 0), ALOFT = new BlockPos(0, GROUND + 12, 0);

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND || isSoil(x, y, z);
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            BlockPos b = new BlockPos(x, y, z);
            return y <= GROUND || b.equals(MOUND) || b.equals(ALOFT);
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

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> null);

    @Test
    void timeAtOtherWorkCountsTowardTheWaitBeforeItLooksAgain() {
        Clearer clearer = new Clearer("Alice", session, new Inventory(), new HashSet<>());
        Position feet = new Position(0.5, GROUND + 1, 0.5);

        clearer.tick(feet, world, List.of(ALOFT), true);
        assertFalse(clearer.active(), "nothing in reach: it waits before it looks again");
        for (int i = 0; i < Clearer.COOLDOWN_TICKS; i++) {
            clearer.pass(); // its owner at other work
        }

        clearer.tick(feet, world, List.of(MOUND), true);
        assertTrue(clearer.active(), "the wait over, the mound in reach is cut");
    }

    /**
     * A block whose drop needs a pickaxe it hasn't got is left alone (operator, 2026-11-13): dug by
     * hand or with a lesser pickaxe it would break, slowly, for nothing. With the pickaxe, it's cut.
     */
    @Test
    void aBlockBeyondItsToolsIsLeftForABetterPickaxe() {
        WorldView stony = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isStone(int x, int y, int z) {
                return MOUND.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return y <= GROUND;
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
        Position feet = new Position(0.5, GROUND + 1, 0.5);
        Inventory bareHanded = new Inventory();
        Clearer clearer = new Clearer("Alice", session, bareHanded, new HashSet<>());
        clearer.tick(feet, stony, List.of(MOUND), true);
        assertFalse(clearer.active(), "stone with no pickaxe: left");

        Inventory withPickaxe = new Inventory();
        withPickaxe.setSlot(Inventory.MAIN_FIRST, new int[]{DiggerTest.item("wooden_pickaxe"), 1});
        Clearer equipped = new Clearer("Bob", session, withPickaxe, new HashSet<>());
        equipped.tick(feet, stony, List.of(MOUND), true);
        assertTrue(equipped.active(), "with a pickaxe: cut");
    }
}
