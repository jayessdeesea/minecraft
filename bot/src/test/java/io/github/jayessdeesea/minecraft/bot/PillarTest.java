package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A pillar is put from the middle of its cell: a 0.6-wide body standing off-centre reaches under the column beside
 * it, and a block two over its feet there stops the jump short of the height a block goes in under it.
 */
class PillarTest {

    /** What a climb out of a shaft came to: the blocks asked for in order, the tick of the first, and the last report. */
    record Climb(List<BlockPos> asked, int firstAskedAt, Report report, Position end) {
    }

    /** Ticks {@code soldier} for {@code ticks}, the server taking each block it asks for into {@code placed}. */
    static Climb climb(DeterministicSoldier soldier, WorldView world, Set<BlockPos> placed, int ticks) {
        List<BlockPos> asked = new ArrayList<>();
        int firstAskedAt = -1;
        for (int tick = 0; tick < ticks; tick++) {
            soldier.tick(world);
            Optional<BlockPos> placement = soldier.takePlacement();
            if (placement.isPresent()) {
                if (asked.isEmpty()) {
                    firstAskedAt = tick;
                }
                asked.add(placement.get());
                placed.add(placement.get());
            }
        }
        return new Climb(asked, firstAskedAt, soldier.status().report(), soldier.status().position());
    }

    static DeterministicSoldier withCobblestone(Position at) {
        DeterministicSoldier soldier = new DeterministicSoldier("Alice", at);
        soldier.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 16));
        return soldier;
    }

    /**
     * A one-cell shaft (10, 61..63, 25) through rock whose top is the surface at y=64, on a floor at y=60, with a
     * pocket (10, 61..62, 24) beside its foot under the block (10, 63, 24): the pillar is the only way up.
     */
    static WorldView shaftWithAPocketNorth(Set<BlockPos> placed) {
        return DeterministicSoldierTest.blocks((x, y, z) -> placed.contains(new BlockPos(x, y, z)) || y <= 60
                || y <= 63 && !(x == 10 && z == 25) && !(x == 10 && z == 24 && y <= 62));
    }

    @Test
    void offCentreUnderABlockOverTheColumnBesideItCentresThenPillars() {
        Set<BlockPos> placed = new HashSet<>();
        WorldView shaft = shaftWithAPocketNorth(placed);
        // Its box reaches z=24.868, into the pocket's column, under the block at (10, 63, 24).
        DeterministicSoldier alice = withCobblestone(new Position(10.5, 61, 25.168));
        alice.receiveOrder(new Order.MoveTo(11.5, 64, 24.5, 0.5, 0.5, Risk.LOW));

        Climb climb = climb(alice, shaft, placed, 400);

        assertFalse(climb.asked().isEmpty(), "asked for no block; " + climb.report() + " at " + climb.end());
        assertEquals(new BlockPos(10, 61, 25), climb.asked().getFirst());
        assertEquals(new Report.InPosition(), climb.report(), "on the surface: " + climb.end());
    }

    @Test
    void aHairInsideItsCellsLowFaceUnderABlockOverTheColumnBesideItCentresThenPillars() {
        // A shaft (33, 41..44, 34) to the surface at y=45, a pocket (32, 41..42, 34) at its foot under (32, 43, 34).
        Set<BlockPos> placed = new HashSet<>();
        WorldView shaft = DeterministicSoldierTest.blocks((x, y, z) -> placed.contains(new BlockPos(x, y, z)) || y <= 40
                || y <= 44 && !(x == 33 && z == 34) && !(x == 32 && z == 34 && y <= 42));
        // Against the pocket's far wall: x - 0.3 falls a hair under 33, so the box counts as in column 32.
        DeterministicSoldier pete = withCobblestone(new Position(33.29999999999999, 41, 34.5));
        pete.receiveOrder(new Order.MoveTo(34.5, 45, 34.5, 0.5, 0.5, Risk.LOW));

        Climb climb = climb(pete, shaft, placed, 400);

        assertFalse(climb.asked().isEmpty(), "asked for no block; " + climb.report() + " at " + climb.end());
        assertEquals(new BlockPos(33, 41, 34), climb.asked().getFirst());
        assertEquals(new Report.InPosition(), climb.report(), "on the surface: " + climb.end());
    }

    @Test
    void alreadyCentredItPillarsWithNoStepFirst() {
        Set<BlockPos> placed = new HashSet<>();
        WorldView shaft = shaftWithAPocketNorth(placed);
        DeterministicSoldier alice = withCobblestone(new Position(10.5, 61, 25.5));
        alice.receiveOrder(new Order.MoveTo(11.5, 64, 24.5, 0.5, 0.5, Risk.LOW));

        Climb climb = climb(alice, shaft, placed, 400);

        assertEquals(List.of(new BlockPos(10, 61, 25), new BlockPos(10, 62, 25)), climb.asked());
        assertEquals(3, climb.firstAskedAt(), "the tick the first block is asked for: no step to the middle first");
        assertEquals(new Report.InPosition(), climb.report(), "on the surface: " + climb.end());
    }
}
