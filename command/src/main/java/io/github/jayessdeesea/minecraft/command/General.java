package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * The chain-of-command view of the general: decide an {@link Objective} for each squad. No
 * {@code receiveX} method - nothing sits above a {@code General} in the hierarchy (see AGENTS.md's
 * Objective section: "one instance"). Like {@link SquadLeader#decide}, this holds no squad state
 * itself - the imperative shell passes current {@link SquadStatus}es in and applies the returned
 * {@link Objective}s back out to each {@link SquadLeader}.
 */
public interface General {

    /**
     * Decides an {@link Objective} for each squad. The returned list has exactly the same length
     * and order as {@code squadStatuses} - element {@code i} of the result is the objective for the
     * squad described by element {@code i} of the input.
     */
    List<Objective> decide(List<SquadStatus> squadStatuses);
}
