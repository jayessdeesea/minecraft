package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * The chain-of-command view of the general: decide an {@link Objective} for each squad. Above it
 * is only the human operator, whose mission it carries out ({@link #receiveMission}). Like
 * {@link SquadLeader#decide}, this holds no squad state itself - the imperative shell passes
 * current {@link SquadStatus}es in and applies the returned {@link Objective}s back out to each
 * {@link SquadLeader}.
 */
public interface General {

    /** A new mission from the operator, replacing the current one from the next decision on. */
    void receiveMission(String mission);

    /** What the stockpile held when a soldier last opened it (docs/military-doctrine.md §12). */
    void receiveStockpile(Stockpile stockpile);

    /** The overworld's time of day, as the server last sent it. */
    void receiveTime(TimeOfDay time);

    /**
     * Decides an {@link Objective} for each squad. The returned list has exactly the same length
     * and order as {@code squadStatuses} - element {@code i} of the result is the objective for the
     * squad described by element {@code i} of the input.
     */
    List<Objective> decide(List<SquadStatus> squadStatuses);
}
