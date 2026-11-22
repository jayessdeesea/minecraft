package io.github.jayessdeesea.minecraft.command;

import java.util.Objects;
import java.util.Optional;

/**
 * One objective in the platoon leader's objective register: a step of a mission, on a chunk, given to a squad, and how
 * it stands (operator, 2026-11-21: the platoon leader tracks the outcomes and the subtasks). A farm at a chunk is three
 * - flatten it to a level below, floor it in cobblestone, the farm template on the floor - each started when the one
 * before is measured done.
 *
 * @param id        the register's own id; 0 before it's kept
 * @param mission   the mission it's a step of, as given: "a farm at chunk (1, 3)"
 * @param step      its place in the mission, from 0
 * @param task      what the step is, said as its task: "flatten to y=62"
 * @param chunkX    the chunk it works, x
 * @param chunkZ    the chunk it works, z
 * @param squad     the squad on it, once one is
 * @param state     how it stands
 * @param changedAt when its state last changed, in the clock's millis
 * @param why       why it stands so, if it's said: a CANTCO's reason, what was measured done
 */
public record ObjectiveRecord(long id, String mission, int step, String task, int chunkX, int chunkZ, Optional<String> squad,
                              State state, long changedAt, Optional<String> why) {

    /** How an objective stands: RELIEVED when the squad's given another before it's done. */
    public enum State { PLANNED, UNDER_WAY, DONE, CANTCO, RELIEVED }

    public ObjectiveRecord {
        Objects.requireNonNull(mission, "mission");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(squad, "squad");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(why, "why");
        if (step < 0) {
            throw new IllegalArgumentException("step " + step);
        }
    }

    /** This objective as it stands at {@code now}: {@code state}, by {@code squad}, {@code why}. */
    public ObjectiveRecord now(State state, Optional<String> squad, Optional<String> why, long now) {
        return new ObjectiveRecord(id, mission, step, task, chunkX, chunkZ, squad, state, now, why);
    }
}
