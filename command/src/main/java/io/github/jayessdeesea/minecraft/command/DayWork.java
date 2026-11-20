package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where the day's work stands, computed for the platoon leader (operator, 2026-11-17: the platoon leader
 * says who farms, who works the grove and who flattens): the lodgment's {@link Phase} and what stands between
 * it and the next, what the force is short of ({@link PlatoonSergeant#shortfalls}), the stockpile as last seen,
 * the ground to flatten with its level and the blocks left on it, and the wheat standing ripe round the camp, to forage.
 */
public record DayWork(List<PlatoonSergeant.Shortfall> shortfalls, Optional<Stockpile> stores, Flatten flatten,
                      OptionalInt keepLevel, int keepBlocksLeft, long unarmed, Phase phase, String toNextPhase, int ripeWheat) {

    public DayWork {
        shortfalls = List.copyOf(shortfalls);
    }

    /** In the buildup, nothing said of the next phase. */
    public DayWork(List<PlatoonSergeant.Shortfall> shortfalls, Optional<Stockpile> stores, Flatten flatten,
                   OptionalInt keepLevel, int keepBlocksLeft, long unarmed) {
        this(shortfalls, stores, flatten, keepLevel, keepBlocksLeft, unarmed, Phase.BUILDUP, "", 0);
    }

    /** With every soldier armed. */
    public DayWork(List<PlatoonSergeant.Shortfall> shortfalls, Optional<Stockpile> stores, Flatten flatten,
                   OptionalInt keepLevel, int keepBlocksLeft) {
        this(shortfalls, stores, flatten, keepLevel, keepBlocksLeft, 0);
    }

    /** The levels the force keeps its renewables at - the standing ones until a mission raises them. */
    public ProductionLevels levels() {
        return ProductionLevels.STANDING;
    }
}
