package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where the day's work stands, computed for the platoon leader (operator, 2026-11-17: the platoon leader
 * says who farms, who works the grove and who flattens): what the force is short of
 * ({@link PlatoonSergeant#shortfalls}), the stockpile as last seen, and the ground to flatten with its
 * level and the blocks left on it.
 */
public record DayWork(List<PlatoonSergeant.Shortfall> shortfalls, Optional<Stockpile> stores, Flatten flatten,
                      OptionalInt keepLevel, int keepBlocksLeft, long unarmed) {

    public DayWork {
        shortfalls = List.copyOf(shortfalls);
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

    /** Whether the force is in its foothold (see {@link #foothold(Optional)}). */
    public boolean foothold() {
        return foothold(stores);
    }

    /**
     * Whether the force, its stores {@code stores}, is still in its foothold (operator, 2026-11-18): short of the
     * bread or the logs - under their least ({@link ProductionLevels}) - to sustain itself, or not yet seen. In it,
     * nobody mines at night and the day's work is the food and wood pipeline, one squad at most flattening; out of
     * it once both are stocked.
     */
    public static boolean foothold(Optional<Stockpile> stores) {
        return stores.map(st -> st.count(Renewable.BREAD) < ProductionLevels.STANDING.of(Renewable.BREAD).least()
                || st.count(Renewable.LOGS) < ProductionLevels.STANDING.of(Renewable.LOGS).least()).orElse(true);
    }
}
