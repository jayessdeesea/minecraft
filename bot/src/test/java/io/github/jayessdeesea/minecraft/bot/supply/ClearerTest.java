package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
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

    /** The ground's dirt, with {@code MOUND} a block of stone or of iron ore - which wants a stone pickaxe for its drop. */
    private WorldView stony(boolean ore) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isStone(int x, int y, int z) {
                return !ore && MOUND.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isOre(int x, int y, int z) {
                return ore && MOUND.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isIronOre(int x, int y, int z) {
                return isOre(x, y, z);
            }

            @Override
            public java.util.Optional<Hardness> hardness(int x, int y, int z) {
                return MOUND.equals(new BlockPos(x, y, z))
                        ? java.util.Optional.of(ore ? new Hardness(3, 1, Hardness.Tool.PICKAXE) : new Hardness(1.5, 0, Hardness.Tool.PICKAXE))
                        : java.util.Optional.empty();
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
    }

    /**
     * Ore whose drop needs a pickaxe it hasn't got is left alone (operator, 2026-11-13): dug with
     * less it breaks slowly and the drop is lost. With the pickaxe, it's cut.
     */
    @Test
    void oreBeyondItsToolsIsLeftForABetterPickaxe() {
        Position feet = new Position(0.5, GROUND + 1, 0.5);
        Inventory wooden = new Inventory();
        wooden.setSlot(Inventory.MAIN_FIRST, new int[]{DiggerTest.item("wooden_pickaxe"), 1});
        Clearer clearer = new Clearer("Alice", session, wooden, new HashSet<>());
        clearer.tick(feet, stony(true), List.of(MOUND), true);
        assertFalse(clearer.active(), "iron ore with a wooden pickaxe: left");

        Inventory stone = new Inventory();
        stone.setSlot(Inventory.MAIN_FIRST, new int[]{DiggerTest.item("stone_pickaxe"), 1});
        Clearer equipped = new Clearer("Bob", session, stone, new HashSet<>());
        equipped.tick(feet, stony(true), List.of(MOUND), true);
        assertTrue(equipped.active(), "with a stone pickaxe: cut");
    }

    /**
     * Stone with no pickaxe is dug by hand (operator, 2026-11-14: the best tool available, and sometimes
     * that's fists): a clear is for the ground gone, not the cobblestone, and fists break anything
     * but bedrock. Live, a soldier with no pickaxe stood in a pocket of stone waiting on one.
     */
    @Test
    void stoneWithNoPickaxeIsDugByHand() {
        Position feet = new Position(0.5, GROUND + 1, 0.5);
        Clearer clearer = new Clearer("Alice", session, new Inventory(), new HashSet<>());
        clearer.tick(feet, stony(false), List.of(MOUND), true);
        assertTrue(clearer.active(), "stone with no pickaxe: dug by hand");
    }
}
