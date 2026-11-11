package io.github.jayessdeesea.minecraft.command;

import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link General} hands a {@link SquadLeader} - OPORD-shaped but small
 * (docs/military-doctrine.md §5): the mission (what), where, and the commander's intent (why,
 * §1). The intent is what lets the squad leader adapt - pick a different spot near the location,
 * report CANTCO with a useful reason - without another round trip to the general. The
 * {@link Task} is the one thing the squad does there, besides being there.
 */
public record Objective(String mission, Position location, String intent, Task task) {

    /** What the squad does at the objective - one task at a time. */
    public sealed interface Task {
        /** Nothing but be there: move, hold, observe, support. */
        record None() implements Task {
        }

        /** Collect {@code resource} around the location (§12). */
        record Gather(Resource resource) implements Task {
            public Gather {
                if (!resource.gathered()) {
                    throw new IllegalArgumentException(resource + " isn't gathered");
                }
            }
        }

        /** Build a shelter there and spend the night in it (§14). */
        record PatrolBase() implements Task {
        }

        /** Till and plant a wheat farm by the water there, and keep it - harvest, replant, bake bread. */
        record Farm() implements Task {
        }

        /** Plant saplings round the location, spruce first, so the camp has trees to cut (§12). */
        record TreeFarm() implements Task {
        }

        /** Light the ground round the location so no monster spawns on it (§15). */
        record Secure() implements Task {
        }

        /** Wall the ground round the location in, two blocks high, so nothing walks in from the dark (§15). */
        record Fortify() implements Task {
        }

        /** Cut a staircase down at the location's x and z, to its y: stone, and the ore it opens (§16). */
        record Mine() implements Task {
        }

        /** Walk the ground round the location, round and round inside its wall line: security, not work (§15). */
        record Patrol() implements Task {
        }
    }

    public Objective {
        Objects.requireNonNull(task, "task");
    }

    /** An objective with nothing to gather or build. */
    public Objective(String mission, Position location, String intent) {
        this(mission, location, intent, new Task.None());
    }

    /** An objective with nothing to build. */
    public Objective(String mission, Position location, String intent, Optional<Resource> gather) {
        this(mission, location, intent, gather, false, false);
    }

    /** An objective that isn't a farm. */
    public Objective(String mission, Position location, String intent, Optional<Resource> gather, boolean patrolBase) {
        this(mission, location, intent, gather, patrolBase, false);
    }

    /** An objective from the flags of its task: at most one set. */
    public Objective(String mission, Position location, String intent, Optional<Resource> gather,
                     boolean patrolBase, boolean farm) {
        this(mission, location, intent, task(gather, patrolBase, farm));
    }

    private static Task task(Optional<Resource> gather, boolean patrolBase, boolean farm) {
        if ((patrolBase ? 1 : 0) + (farm ? 1 : 0) + (gather.isPresent() ? 1 : 0) > 1) {
            throw new IllegalArgumentException("a squad has one task at a time: gather, patrol base or farm");
        }
        return gather.<Task>map(Task.Gather::new).orElse(patrolBase ? new Task.PatrolBase() : farm ? new Task.Farm() : new Task.None());
    }

    /** What the squad gathers there, if that's its task. */
    public Optional<Resource> gather() {
        return task instanceof Task.Gather(Resource resource) ? Optional.of(resource) : Optional.empty();
    }

    public boolean patrolBase() {
        return task instanceof Task.PatrolBase;
    }

    public boolean farm() {
        return task instanceof Task.Farm;
    }

    public boolean treeFarm() {
        return task instanceof Task.TreeFarm;
    }

    public boolean secure() {
        return task instanceof Task.Secure;
    }

    public boolean patrol() {
        return task instanceof Task.Patrol;
    }

    public boolean fortify() {
        return task instanceof Task.Fortify;
    }

    public boolean mine() {
        return task instanceof Task.Mine;
    }
}
