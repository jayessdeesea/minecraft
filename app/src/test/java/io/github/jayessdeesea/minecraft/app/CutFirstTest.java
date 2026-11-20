package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CutFirstTest {

    /** The nearest of the camp's ground over its level, within reach; none within it, none. */
    @Test
    void dirtComesFromTheNearestGroundThatHasToGo() {
        List<BlockPos> cut = List.of(new BlockPos(30, 66, 90), new BlockPos(12, 65, 88));
        Position hut = new Position(10.5, 64, 88.5);

        assertEquals(Optional.of(new Position(12.5, 66, 88.5)), CutFirst.near(cut, hut));
        assertEquals(Optional.empty(), CutFirst.near(cut, new Position(-40.5, 64, 88.5)), "none within reach");
    }
}
