package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a camp, its ground work, a keep's dig and a farm mission each keep, a part a test. */
class KeptGroundTest {

    private static final Optional<Position> CAMP = Optional.of(new Position(0.5, 64, 0.5));
    private static final int RADIUS = 24;

    private final Set<BlockPos> chests = new HashSet<>();
    private final Set<BlockPos> stone = new HashSet<>();
    private final Set<BlockPos> cobblestone = new HashSet<>();
    private final Set<BlockPos> soil = new HashSet<>();

    /** Flat ground with its top at y=63, of no kind but what a test names. */
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63 || chests.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isStone(int x, int y, int z) {
            return stone.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isCobblestone(int x, int y, int z) {
            return cobblestone.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return soil.contains(new BlockPos(x, y, z));
        }
    };

    /** A camp of nothing but what a test gives it. */
    private static KeptGround.Camp camp(List<List<BlockPos>> walls, Optional<Position> camp, List<KeptGround.Hut> goingUp, List<PatrolBase.Plan> built,
                                        List<Workshop> shops, Optional<Workshop> depot, Set<BlockPos> full, Predicate<BlockPos> released) {
        return new KeptGround.Camp(walls, camp, RADIUS, goingUp, built, shops, depot, Set.of(), 24, full, released);
    }

    private static KeptGround.Camp bare() {
        return camp(List.of(), CAMP, List.of(), List.of(), List.of(), Optional.empty(), Set.of(), c -> false);
    }

    @Test
    void aCampOfNothingKeepsNothing() {
        assertEquals(Set.of(), KeptGround.camp(world, bare()));
    }

    /** A wall's own cells, every line's, and nothing under them. */
    @Test
    void aCampKeepsItsWalls() {
        List<List<BlockPos>> walls = List.of(List.of(new BlockPos(8, 64, 0), new BlockPos(8, 65, 0)), List.of(new BlockPos(-8, 64, 0)));
        assertEquals(Set.of(new BlockPos(8, 64, 0), new BlockPos(8, 65, 0), new BlockPos(-8, 64, 0)),
                KeptGround.camp(world, camp(walls, CAMP, List.of(), List.of(), List.of(), Optional.empty(), Set.of(), c -> false)));
    }

    /** A hut going up is kept on the camp's level, past its radius, or with no camp - never on ground still to come down. */
    @Test
    void aCampKeepsAHutGoingUpOnItsLevelOnly() {
        KeptGround.Hut level = new KeptGround.Hut(new Position(4.5, 64, 4.5), List.of(new BlockPos(3, 64, 4), new BlockPos(3, 65, 4)));
        KeptGround.Hut onTheHill = new KeptGround.Hut(new Position(-4.5, 70, 4.5), List.of(new BlockPos(-3, 70, 4)));
        KeptGround.Hut farOff = new KeptGround.Hut(new Position(40.5, 70, 0.5), List.of(new BlockPos(41, 70, 0)));
        List<KeptGround.Hut> huts = List.of(level, onTheHill, farOff);
        assertEquals(Set.of(new BlockPos(3, 64, 4), new BlockPos(3, 65, 4), new BlockPos(41, 70, 0)),
                KeptGround.camp(world, camp(List.of(), CAMP, huts, List.of(), List.of(), Optional.empty(), Set.of(), c -> false)));
        assertEquals(Set.of(new BlockPos(3, 64, 4), new BlockPos(3, 65, 4), new BlockPos(-3, 70, 4), new BlockPos(41, 70, 0)),
                KeptGround.camp(world, camp(List.of(), Optional.empty(), huts, List.of(), List.of(), Optional.empty(), Set.of(), c -> false)), "no camp: every hut");
    }

    /** A hut built keeps its blocks and the ground under its floor, by where its first floor cell stands. */
    @Test
    void aCampKeepsAHutBuiltAndTheGroundUnderItsFloor() {
        PatrolBase.Plan level = new PatrolBase.Plan(List.of(), List.of(new Position(4.5, 64, 4.5), new Position(5.5, 64, 4.5)),
                List.of(new Position(3.5, 64, 4.5), new Position(3.5, 65, 4.5)));
        PatrolBase.Plan onTheHill = new PatrolBase.Plan(List.of(), List.of(new Position(-4.5, 70, 4.5)), List.of(new Position(-3.5, 70, 4.5)));
        assertEquals(Set.of(new BlockPos(3, 64, 4), new BlockPos(3, 65, 4), new BlockPos(4, 63, 4), new BlockPos(5, 63, 4)),
                KeptGround.camp(world, camp(List.of(), CAMP, List.of(), List.of(level, onTheHill), List.of(), Optional.empty(), Set.of(), c -> false)));
    }

    /** Each workshop's stand, table, furnace and chest, the squads' and the depot's, and the cell under each. */
    @Test
    void aCampKeepsItsWorkshopsAndTheCellUnderEach() {
        Workshop squads = new Workshop(new BlockPos(10, 64, 10), new BlockPos(11, 64, 10), new BlockPos(9, 64, 10), Optional.of(new BlockPos(11, 64, 11)));
        Workshop depot = new Workshop(new BlockPos(-10, 64, 10), new BlockPos(-9, 64, 10), new BlockPos(-11, 64, 10));
        Set<BlockPos> expected = new HashSet<>();
        for (BlockPos c : List.of(new BlockPos(10, 64, 10), new BlockPos(11, 64, 10), new BlockPos(9, 64, 10), new BlockPos(11, 64, 11),
                new BlockPos(-10, 64, 10), new BlockPos(-9, 64, 10), new BlockPos(-11, 64, 10))) {
            expected.add(c);
            expected.add(c.offset(0, -1, 0));
        }
        assertEquals(expected, KeptGround.camp(world, camp(List.of(), CAMP, List.of(), List.of(), List.of(squads), Optional.of(depot), Set.of(), c -> false)));
    }

    /** A chest found full is kept, with the cell under it, while it stands and isn't released to a dig. */
    @Test
    void aCampKeepsAFullChestThatStandsAndIsNotReleased() {
        BlockPos stands = new BlockPos(5, 64, 0), gone = new BlockPos(6, 64, 3), released = new BlockPos(7, 64, 6);
        chests.add(stands);
        chests.add(released);
        assertEquals(Set.of(stands, stands.offset(0, -1, 0)), KeptGround.camp(world,
                camp(List.of(), CAMP, List.of(), List.of(), List.of(), Optional.empty(), Set.of(stands, gone, released), released::equals)));
    }

    /** Every chest standing in the depot's row is kept with the cell under it, and a cell of the row with no chest in it is not. */
    @Test
    void aCampKeepsEveryChestStandingInItsDepotsRow() {
        Workshop depot = new Workshop(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(-1, 64, 0));
        BlockPos inTheRow = new BlockPos(0, 64, 2);
        chests.add(inTheRow);
        Set<BlockPos> expected = new HashSet<>();
        for (BlockPos c : List.of(depot.stand(), depot.table(), depot.furnace(), inTheRow)) {
            expected.add(c);
            expected.add(c.offset(0, -1, 0));
        }
        assertEquals(expected, KeptGround.camp(world, camp(List.of(), CAMP, List.of(), List.of(), List.of(), Optional.of(depot), Set.of(), c -> false)));
    }

    /** No ground work digs or fills the camp's kept cells, its mine, or a squad's stairs: their walkways and landings. */
    @Test
    void groundWorkKeepsTheCampItsMineAndTheSquadsStairs() {
        Staircase stairs = Staircase.well(2, 2, 64, 30);
        Set<Cell> expected = new HashSet<>(Set.of(new Cell(1, 64, 1), new Cell(100, 40, 100)));
        stairs.walkway().forEach(c -> expected.add(new Cell(c.x(), c.y(), c.z())));
        stairs.landing().forEach(c -> expected.add(new Cell(c.x(), c.y(), c.z())));
        assertFalse(stairs.walkway().isEmpty() || stairs.landing().isEmpty(), "a well has a walkway and a landing");
        Set<Cell> kept = KeptGround.ground(Set.of(new BlockPos(1, 64, 1)), List.of(new BlockPos(100, 40, 100)), List.of(stairs));
        assertEquals(expected, kept);
        kept.add(new Cell(0, 0, 0)); // a caller adds its own to it
        assertEquals(Set.of(), KeptGround.ground(Set.of(), List.of(), List.of()));
    }

    /** A keep's dig leaves what its ground work leaves, and its slab's rock - stone or cobblestone, in the slab's layer only. */
    @Test
    void aKeepsDigLeavesItsSlabsRock() {
        Flatten ground = new Flatten(0, 0, 3, 3, 63);
        stone.addAll(List.of(new BlockPos(0, 63, 0), new BlockPos(1, 62, 1), new BlockPos(1, 64, 1), new BlockPos(4, 63, 0)));
        cobblestone.add(new BlockPos(3, 63, 3));
        soil.add(new BlockPos(2, 63, 2));
        Set<Cell> kept = Set.of(new Cell(50, 64, 50));
        Set<Cell> except = KeptGround.keep(world, ground, kept, List.of());
        assertEquals(Set.of(new Cell(50, 64, 50), new Cell(0, 63, 0), new Cell(3, 63, 3)), except);
        except.add(new Cell(0, 0, 0)); // ore set aside is added to it
        assertEquals(Set.of(new Cell(50, 64, 50)), kept, "what the ground work leaves is its caller's still");
    }

    /** A soil site's slab keeps its soil, and its rock is dug. */
    @Test
    void aSoilSitesDigLeavesItsSlabsSoil() {
        Flatten ground = new Flatten(0, 0, 3, 3, 63, Flatten.Side.WEST, true);
        stone.add(new BlockPos(0, 63, 0));
        cobblestone.add(new BlockPos(3, 63, 3));
        soil.addAll(List.of(new BlockPos(2, 63, 2), new BlockPos(2, 64, 2)));
        assertEquals(Set.of(new Cell(2, 63, 2)), KeptGround.keep(world, ground, Set.of(), List.of()));
    }

    /** A chest still to be emptied stands on a pillar: the ground under it and round it, from the dig's bottom up to its own height. */
    @Test
    void aKeepsDigLeavesThePillarUnderAChestStillStanding() {
        Flatten ground = new Flatten(0, 0, 3, 3, 63);
        Set<Cell> expected = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 64; y <= 66; y++) {
                    expected.add(new Cell(2 + dx, y, dz));
                }
            }
        }
        assertEquals(27, expected.size());
        assertEquals(expected, KeptGround.keep(world, ground, Set.of(), List.of(new BlockPos(2, 66, 0))));
        assertEquals(Set.of(), KeptGround.keep(world, ground, Set.of(), List.of(new BlockPos(2, 63, 0))), "under the dig's bottom: no pillar");
    }

    /** A mission keeps every other template's chunk and the fence lines past it, a column on a line its west or north neighbour's too. */
    @Test
    void aMissionKeepsOtherTemplatesChunksAndTheirFenceLines() {
        FarmMission own = new FarmMission(0, 0, 64, FarmMission.Crop.WHEAT);
        BiPredicate<Integer, Integer> kept = KeptGround.columns(Set.of(ChunkFarm.key(1, 0), ChunkFarm.key(0, 0), ChunkFarm.key(-1, -1)), own);
        assertTrue(kept.test(16, 5) && kept.test(31, 0) && kept.test(31, 15), "the chunk east of it");
        assertTrue(kept.test(32, 5) && kept.test(20, 16) && kept.test(32, 16), "and the fence lines past its east and south edges");
        assertFalse(kept.test(33, 5) || kept.test(20, 17) || kept.test(20, -1), "nothing past them");
        assertFalse(kept.test(15, 5) || kept.test(5, 5) || kept.test(1, 1), "its own chunk, a template or not");
        assertTrue(kept.test(0, 0), "its own corner is on the lines of the template north-west of it");
        assertFalse(kept.test(0, 1) || kept.test(1, 0), "and its own west and north lines are no other template's");
        assertTrue(kept.test(-16, -16) && kept.test(-1, -1) && kept.test(0, -5) && kept.test(-5, 0), "the chunk north-west of it, lines and all");
        BiPredicate<Integer, Integer> none = KeptGround.columns(Set.of(), own);
        assertFalse(none.test(16, 5) || none.test(0, 0), "no template: nothing kept");
    }
}
