package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopTest {

    /** Flat ground at y=63, with {@code stations} standing on it. */
    private static WorldView flat(Map<BlockPos, Workstation> stations) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || stations.containsKey(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public Optional<Workstation> workstation(int x, int y, int z) {
                return Optional.ofNullable(stations.get(new BlockPos(x, y, z)));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void onOpenGroundTheWorkshopStandsAtTheObjectiveWithItsTableAndFurnaceOnAdjacentSides() {
        BlockPos ground = new BlockPos(0, 64, 0);
        Workshop w = Workshop.hasty(flat(Map.of()), ground, Set.of()).orElseThrow();
        assertEquals(ground, w.stand());
        assertEquals(1, Math.abs(w.table().x() - w.stand().x()) + Math.abs(w.table().z() - w.stand().z()));
        assertEquals(1, Math.abs(w.furnace().x() - w.stand().x()) + Math.abs(w.furnace().z() - w.stand().z()));
        assertNotEquals(w.table().x() - w.stand().x(), w.stand().x() - w.furnace().x(), "not opposite sides: the stand stays open");
    }

    @Test
    void aTableAlreadyStandingNearbyIsTheWorkshopsSoARestartFindsTheSameOne() {
        BlockPos table = new BlockPos(3, 64, 2);
        Workshop w = Workshop.hasty(flat(Map.of(table, Workstation.CRAFTING_TABLE)), new BlockPos(0, 64, 0), Set.of()).orElseThrow();
        assertEquals(table, w.table());
        assertTrue(w.stands(flat(Map.of(table, Workstation.CRAFTING_TABLE)), Workstation.CRAFTING_TABLE));
    }

    /**
     * A workshop is sited where its chest has room too: with the cells behind the nearest stand's table
     * and furnace taken, the next stand with a chest's cell is the one. Live, the keep's depot stood
     * on a slope with no level cell for a chest, and never got one.
     */
    @Test
    void theWorkshopIsSitedWhereItsChestHasRoom() {
        BlockPos ground = new BlockPos(0, 64, 0);
        Workshop open = Workshop.hasty(flat(Map.of()), ground, Set.of()).orElseThrow();
        Set<BlockPos> behind = Set.of(open.table().offset(open.table().x() - open.stand().x(), 0, open.table().z() - open.stand().z()),
                open.furnace().offset(open.furnace().x() - open.stand().x(), 0, open.furnace().z() - open.stand().z()));

        Workshop sited = Workshop.hasty(flat(Map.of()), ground, behind).orElseThrow();

        assertTrue(sited.chest().isPresent(), "a chest's cell: " + sited);
        assertTrue(!behind.contains(sited.chest().get()));
    }

    /**
     * A depot's chests: six cells on open ground, the workshop's own chest first, none beside the
     * stand or beside another - two side by side would join into one - and all within reach of it.
     */
    @Test
    void aDepotsChestsStandApartWithinReachOfItsStand() {
        Workshop w = Workshop.hasty(flat(Map.of()), new BlockPos(0, 64, 0), Set.of()).orElseThrow();

        List<BlockPos> cells = w.chestCells(flat(Map.of()), Set.of(), 6);

        assertEquals(6, cells.size(), cells.toString());
        assertEquals(w.chest().orElseThrow(), cells.getFirst());
        for (BlockPos a : cells) {
            assertTrue(Math.abs(a.x() - w.stand().x()) <= 3 && Math.abs(a.z() - w.stand().z()) <= 3, a + " out of reach");
            assertTrue(!a.equals(w.table()) && !a.equals(w.furnace()) && !a.equals(w.stand()));
            for (BlockPos b : cells) {
                assertTrue(a.equals(b) || Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1, a + " beside " + b);
            }
        }
        assertEquals(cells.get(1), w.withChest(cells.get(1)).chest().orElseThrow());
    }

    /**
     * On a hillside a depot's chests go a step up and down the slope too: live, level cells alone
     * gave a depot two chests, both full within minutes of the second going up.
     */
    @Test
    void onASlopeADepotsChestsGoAStepUpOrDown() {
        // The ground climbs a block for every block east.
        WorldView slope = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 + x;
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
        Workshop w = new Workshop(new BlockPos(0, 64, 0), new BlockPos(0, 64, 1), new BlockPos(0, 64, -1));

        List<BlockPos> cells = w.chestCells(slope, Set.of(), 6);

        assertTrue(cells.size() >= 4, "a row of them on the slope: " + cells);
        for (BlockPos c : cells) {
            assertEquals(64 + c.x(), c.y(), c + " on the ground");
            assertTrue(Math.abs(c.y() - 64) <= 1, c + " a step from the stand's height at most");
        }
    }

    @Test
    void claimedCellsAreLeftAlone() {
        BlockPos ground = new BlockPos(0, 64, 0);
        Workshop w = Workshop.hasty(flat(Map.of()), ground, Set.of(ground)).orElseThrow();
        assertNotEquals(ground, w.stand());
    }

    @Test
    void onlyAChestInTheChestsCellIsTheChest() {
        Workshop w = Workshop.hasty(flat(Map.of()), new BlockPos(0, 64, 0), Set.of()).orElseThrow();
        BlockPos cell = w.chest().orElseThrow();

        assertTrue(!w.chestStands(withBlock(cell, false)), "a block of cobblestone there isn't a chest to stock");
        assertTrue(w.chestStands(withBlock(cell, true)));
    }

    /**
     * A chest a block below the cell behind the table is the workshop's chest: the ground at a depot
     * moves, and live a workshop sited a block higher took its own chest for none - the chest put in
     * its place went on top of it, the click opened the old one, and nothing went in all night.
     */
    @Test
    void aChestABlockDownBehindTheTableIsTheWorkshopsChest() {
        BlockPos ground = new BlockPos(0, 64, 0);
        BlockPos planned = Workshop.hasty(flat(Map.of()), ground, Set.of()).orElseThrow().chest().orElseThrow();
        BlockPos below = planned.offset(0, -1, 0);

        Workshop w = Workshop.hasty(withBlock(below, true), ground, Set.of()).orElseThrow();

        assertEquals(Optional.of(below), w.chest(), "the chest standing, not a cell over it");
        assertTrue(w.chestStands(withBlock(below, true)));
    }

    /** Flat ground with one block on it at {@code cell}: a chest, or some other block. */
    private static WorldView withBlock(BlockPos cell, boolean chest) {
        WorldView ground = flat(Map.of());
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return ground.isSolid(x, y, z) || cell.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isChest(int x, int y, int z) {
                return chest && cell.equals(new BlockPos(x, y, z));
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

    @Test
    void theMineBasesTableAndFurnaceGoInTheLandingsRecesses() {
        Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 40);
        Workshop w = Workshop.mine(stairs);
        assertEquals(stairs.landingStand(), w.stand());
        assertEquals(stairs.landing(), List.of(w.table(), w.furnace()));
    }
}
