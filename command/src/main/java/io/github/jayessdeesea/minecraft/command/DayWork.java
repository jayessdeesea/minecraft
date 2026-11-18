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
                      OptionalInt keepLevel, int keepBlocksLeft) {

    public DayWork {
        shortfalls = List.copyOf(shortfalls);
    }
}
