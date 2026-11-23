package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * The platoon leader's registers as read (docs/echelons/platoon-leader/decisions.md § Its registers): the task register -
 * each row a squad's task, a phase of an operation or one of the platoon leader's own - and the chunk register. Read
 * only: what's written to them is the chain of command's, through its orders ({@link OperatorCommands}), never a row
 * written from here. What {@link RegistersSaid} computes the operator's chat's answers from.
 */
public interface PlatoonRegisters {

    /**
     * Every task kept, by mission then phase - each a row of the code's objective register (its {@code Objective} type
     * holds a squad's task).
     *
     * @throws IllegalStateException if no registers are kept here
     */
    List<ObjectiveRecord> tasks();

    /**
     * Every chunk kept, by x then z.
     *
     * @throws IllegalStateException if no registers are kept here
     */
    List<ChunkRecord> chunks();

    /**
     * What each soldier present carries, "Alice (Alpha)" to its items by class and their counts, by squad then name.
     *
     * @throws IllegalStateException if nobody's pack is kept here
     */
    default java.util.Map<String, java.util.Map<String, Integer>> carried() {
        throw new IllegalStateException("no soldier's pack is kept here");
    }

    /**
     * The force's stores, what every chest known holds in all: "logs" to 1013. Empty with no chest seen yet.
     *
     * @throws IllegalStateException if no stores are kept here
     */
    default java.util.Map<String, Integer> stores() {
        throw new IllegalStateException("no stores are kept here");
    }

    /** The clock now, in the millis a row's {@code changedAt} is kept in. */
    long now();
}
