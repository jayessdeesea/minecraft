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

    /** The clock now, in the millis a row's {@code changedAt} is kept in. */
    long now();
}
