package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlattenTest {

    /** The keep's first ground is a site like any other: its dig, slab and depot as the keep's were. */
    @Test
    void theKeepsFirstGroundIsAFlattenSite() {
        assertEquals(Keep.dig(), Flatten.KEEP.dig());
        assertEquals(Keep.slab(), Flatten.KEEP.slab());
        assertEquals(Keep.fill(), Flatten.KEEP.fill());
        assertEquals(Keep.DEPOT, Flatten.KEEP.depot());
        assertEquals(Keep.BOTTOM, Flatten.KEEP.bottom());
    }

    /** Corners either way round, spaces or commas; anything else isn't a site. */
    @Test
    void aSiteIsReadAsTheOperatorTypesIt() {
        assertEquals(Optional.of(new Flatten(-16, 16, 16, 32, 63)), Flatten.parse("16 32 -16 16 63"));
        assertEquals(Optional.of(new Flatten(-16, 16, 16, 32, 63)), Flatten.parse("-16,16, 16,32 63"));
        assertEquals(Optional.empty(), Flatten.parse("-16 16 16 32"));
        assertEquals(Optional.empty(), Flatten.parse("a b c d e"));
        assertEquals(Optional.empty(), Flatten.parse("0 0 1 1 999"));
    }

    /**
     * The benched edge (clearing rule 6, 1:1): the ring d out from the edge is dug down to d over the
     * floor - row 1 from two over the top up, row 3 from four - its four sides meeting at the corners.
     */
    @Test
    void theBenchStepsUpOneABlockOut() {
        Flatten site = new Flatten(0, 0, 9, 9, 63);
        assertEquals(new Box(-1, 65, -1, 10, Keep.SKY_TOP, -1), site.bench(1).getFirst(), "row 1 north, from the floor plus one");
        assertEquals(new Box(12, 67, -2, 12, Keep.SKY_TOP, 11), site.bench(3).get(3), "row 3 east, from the floor plus three");
        assertTrue(site.benchedContains(-Flatten.BENCH, 9 + Flatten.BENCH), "the bench's far corner");
        assertFalse(site.benchedContains(-Flatten.BENCH - 1, 0), "one past it");
    }

    /** A long site: its middle, its depot off the west edge halfway along, columns in and out. */
    @Test
    void aSitesGeometryIsItsOwn() {
        Flatten site = new Flatten(-16, 16, 16, 32, 63);
        assertEquals(new Position(0.5, 64, 24.5), site.centre());
        assertEquals(new Position(-16 - Flatten.DEPOT_OFF, 64, 24), site.depot());
        assertTrue(site.contains(0, 32));
        assertFalse(site.contains(0, 33));
        assertEquals(new Box(-16, 64, 16, 16, Keep.SKY_TOP, 32), site.dig());
    }

    /** A big site's depots spread along its west edge, its depot the middle one; a keep's is one. */
    @org.junit.jupiter.api.Test
    void aBigSiteHasADepotEveryFortyEightBlocksAndAKeepOne() {
        Flatten big = new Flatten(-16, -64, 16, 64, 62);
        int x = -16 - Flatten.DEPOT_OFF;
        assertEquals(java.util.List.of(new Position(x, big.floor(), -43), new Position(x, big.floor(), 0), new Position(x, big.floor(), 43)),
                big.depots());
        assertEquals(big.depot(), big.depots().get(1));
        assertEquals(java.util.List.of(Flatten.KEEP.depot()), Flatten.KEEP.depots());
    }
}
