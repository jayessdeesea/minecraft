package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Vanilla's break times, staged: the base, the tool, Efficiency, the effects, water, the ground. */
class BreakTimeTest {

    private static final double STONE = 1.5, DIRT = 0.5;

    @Test
    void theBaseCasesAreVanillas() {
        assertEquals(OptionalInt.of(12), BreakTime.of(4, true).ticks(STONE), "stone, a stone pickaxe: 0.6 s");
        assertEquals(OptionalInt.of(150), BreakTime.of(1, false).ticks(STONE), "stone by hand, no drop: 7.5 s");
        assertEquals(OptionalInt.of(15), BreakTime.of(1, true).ticks(DIRT), "dirt by hand: 0.75 s");
        assertEquals(OptionalInt.of(0), BreakTime.of(1, true).ticks(0), "a flower: at once");
        assertEquals(OptionalInt.empty(), BreakTime.of(9, true).ticks(-1), "bedrock: never");
    }

    @Test
    void efficiencyAddsToTheSpeedAndConditionsDivideIt() {
        BreakTime diamond = new BreakTime(8, true, 5, 0, 0, false, false, true);
        assertEquals(OptionalInt.of(2), diamond.ticks(STONE), "Efficiency V adds 26 to 8: 1.5 * 30 / 34");
        assertEquals(OptionalInt.of(57), BreakTime.of(4, true).airborne().ticks(STONE), "off the ground: a fifth of the speed, 56.25 ticks rounded up");
        assertEquals(OptionalInt.of(57), BreakTime.of(4, true).submerged().ticks(STONE), "underwater: the same");
        assertEquals(OptionalInt.of(282), BreakTime.of(4, true).airborne().submerged().ticks(STONE), "both: a twenty-fifth, 281.25 rounded up");
    }

    @Test
    void hardnessGivesTheSameTicksThroughIt() {
        assertEquals(12, new Hardness(STONE, 0).ticks(Hardness.STONE_TIER));
        assertEquals(150, new Hardness(STONE, 0).ticks(Hardness.HAND));
    }
}
