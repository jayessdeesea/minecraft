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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * A stand is somewhere a walk can end now: open, on solid ground - not a cell inside a bank that a step cut on the
     * way would open. Live (field549-550) farmers were sent to stands inside blocks and under a pond, and each walk
     * there had no route.
     */
    @Test
    void aCutsStandIsOpenGroundNow() {
        WorldView bank = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return isSoil(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                // A bank of soil two high from x=1 on, and a mound on its top at x=3.
                return y <= GROUND || x >= 1 && y <= GROUND + 2 || x == 3 && y == GROUND + 3 && z == 0;
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
        Clearer clearer = new Clearer("Carol", session, new Inventory(), new HashSet<>());
        clearer.tick(new Position(0.5, GROUND + 1, 0.5), bank, List.of(new BlockPos(3, GROUND + 3, 0)), true);

        Position stand = clearer.standAt().orElseThrow(() -> new AssertionError("no cut: " + clearer.idle()));
        BlockPos cell = BlockPos.containing(stand.x(), stand.y(), stand.z());
        assertTrue(io.github.jayessdeesea.minecraft.bot.nav.Pathfinder.standable(bank, cell), "a stand inside the bank: " + cell);
    }

    /** A cut whose stand has no route is passed over, and the next tick takes another, not the same one again. */
    @Test
    void aCutWithNoRouteToItsStandIsPassedOver() {
        BlockPos other = new BlockPos(-2, GROUND + 1, 0);
        WorldView twoMounds = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND || isSoil(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND || b.equals(MOUND) || b.equals(other);
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
        Clearer clearer = new Clearer("Carol", session, new Inventory(), new HashSet<>());
        Position feet = new Position(0.5, GROUND + 1, 0.5);
        clearer.tick(feet, twoMounds, List.of(MOUND, other), true);
        Position first = clearer.standAt().orElseThrow();

        clearer.unreachable("no route from here to within 0 of it");
        assertFalse(clearer.active(), "the cut stopped");
        clearer.tick(feet, twoMounds, List.of(MOUND, other), true);

        assertTrue(clearer.standAt().filter(next -> !next.equals(first)).isPresent(), "the other mound's cut, not " + first + " again");
    }

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
     * Iron ore in the dig is mined for its drop and picked up, not broken as a block in the way:
     * live, a day's flattening went through ore after ore and nobody held an ingot.
     */
    /**
     * Ore of any other kind - coal, redstone, lapis - is mined for its drop too, worth more than the
     * dirt and cobblestone round it (operator, 2026-11-15): live, the keep's flattening broke coal
     * ore as any block and left the coal lying.
     */
    @Test
    void oreOfEveryKindInTheDigIsMinedForItsDrop() {
        WorldView redstone = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isOre(int x, int y, int z) {
                return MOUND.equals(new BlockPos(x, y, z));
            }

            @Override
            public java.util.Optional<String> oreOf(int x, int y, int z) {
                return isOre(x, y, z) ? java.util.Optional.of("redstone") : java.util.Optional.empty();
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
        assertEquals(Digger.Target.ORE, Clearer.targetFor(redstone, MOUND, false));
    }

    @Test
    void ironOreInTheDigIsMinedForItsDrop() {
        assertEquals(Digger.Target.IRON_ORE, Clearer.targetFor(stony(true), MOUND, false));
        assertEquals(Digger.Target.STONE, Clearer.targetFor(stony(false), MOUND, false));
        assertEquals(Digger.Target.SOIL, Clearer.targetFor(stony(false), new BlockPos(0, GROUND, 0), false));
    }

    /**
     * A log with a block over it is no gatherer's LOG ({@link Digger.Target#underTheSky}) but is still in the clear's way:
     * dug as any block. Live (field674-675), Carol and Gus were sent at the logs at (17, 65, 91) and (27, 66, 88), each
     * walk given up a tick in as "the LOG is gone", and the same logs taken again after each wait - nine tries in a row.
     */
    @Test
    void aLogUnderABlockIsClearedAsAnyBlock() {
        BlockPos log = new BlockPos(0, GROUND + 1, 0);
        WorldView roofed = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND + 1 && (y <= GROUND || log.equals(new BlockPos(x, y, z))) || x == 0 && z == 0 && y == GROUND + 3;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return log.equals(new BlockPos(x, y, z));
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
        assertFalse(Digger.Target.LOG.at(roofed, log), "no gatherer's log under a block");
        assertEquals(Digger.Target.OTHER, Clearer.targetFor(roofed, log, true));
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
