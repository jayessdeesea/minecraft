package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A block put where a soldier's box reaches - a squadmate's fence in the column beside it - doesn't hold it there: its
 * box no longer fits where it stands, so no step from there fits either, and it steps to the middle of its own cell first.
 */
class WedgedTest {

    private static Report walk(Position from, WorldView world, Position to) {
        DeterministicSoldier carl = new DeterministicSoldier("Carl", from);
        carl.receiveOrder(new Order.MoveTo(to.x(), to.y(), to.z(), 0.5, 0.5, Risk.LOW));
        for (int tick = 0; tick < 400; tick++) {
            carl.tick(world);
        }
        return carl.status().report();
    }

    @Test
    void aFencePutInTheColumnItsBoxReachesIntoDoesNotHoldIt() {
        // Level ground at y=63 and a fence at (32, 64, 2), which bars the cell over it too; its box, 0.3 either side of x=33.09, reaches 0.21 into column 32.
        WorldView ground = DeterministicSoldierTest.blocks((x, y, z) -> y <= 63 || x == 32 && (y == 64 || y == 65) && z == 2);

        assertEquals(new Report.InPosition(), walk(new Position(33.087966499501505, 64, 2.0113555926748377), ground, new Position(36.5, 64, 2.5)));
    }

    @Test
    void overADropItsOwnCellItStaysPut() {
        // No floor under (33, 2): its box reaches onto column 34's, where a block now stands, and the middle of its own cell is over the drop.
        WorldView ground = DeterministicSoldierTest.blocks((x, y, z) -> y <= 63 && !(x == 33 && z == 2) || x == 34 && (y == 64 || y == 65) && z == 2);
        Gait gait = new Gait(new Position(33.75, 64, 2.5));
        gait.onGround = true;

        gait.step(ground, 36.5, 2.5, Gait.STEP);

        assertEquals(new Position(33.75, 64, 2.5), gait.position, "held, not stepped over the drop");
    }

    @Test
    void inABlockItsOwnCellItStaysPut() {
        // Its own cell solid: there is no middle to step to, and nothing here moves it through a block.
        WorldView ground = DeterministicSoldierTest.blocks((x, y, z) -> y <= 63 || x == 33 && (y == 64 || y == 65) && z == 2);
        DeterministicSoldier carl = new DeterministicSoldier("Carl", new Position(33.5, 64, 2.5));
        carl.receiveOrder(new Order.MoveTo(36.5, 64, 2.5, 0.5, 0.5, Risk.LOW));
        for (int tick = 0; tick < 100; tick++) {
            carl.tick(ground);
        }

        assertEquals(new Position(33.5, 64, 2.5), carl.status().position());
    }
}
