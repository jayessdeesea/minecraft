package io.github.jayessdeesea.minecraft.command;

import java.util.Optional;

/**
 * A soldier's standing job, held until its squad leader changes it: one at a time. A rifleman goes
 * with its squad - the formation, and the general's task. Any other role works its own site as
 * the squad's detachment for that role, whatever the general's task (docs/military-doctrine.md
 * § 18): the miner cuts the mine at its site, the farmer keeps the farm there.
 *
 * <p>A role is the work, not survival: a soldier in any role eats, fights back and gets clear of
 * trouble on its own, and goes back to its role after (docs/echelons/soldier/orders.md).
 */
public enum Role {
    /** With the squad: its slot in the formation, and the squad's task. */
    RIFLEMAN(null),
    /** Cuts the staircase down at the site to its depth, and takes the ore it opens. */
    MINER(new Objective.Task.Mine()),
    /** Tills, plants and harvests the wheat farm by the water at the site, and bakes its bread. */
    FARMER(new Objective.Task.Farm()),
    /** Levels the ground round the site and walls it in. */
    MASON(new Objective.Task.Fortify()),
    /** Plants saplings round the site. */
    FORESTER(new Objective.Task.TreeFarm()),
    /** Chops logs round the site. */
    WOODCUTTER(new Objective.Task.Gather(Resource.WOOD)),
    /** Quarries cobblestone round the site. */
    QUARRYMAN(new Objective.Task.Gather(Resource.STONE)),
    /** Walks the ground round the site, on watch. */
    SENTRY(new Objective.Task.Patrol());

    private final Objective.Task task;

    Role(Objective.Task task) {
        this.task = task;
    }

    /** The task its detachment works at its site; empty for a rifleman, who has the squad's. */
    public Optional<Objective.Task> task() {
        return Optional.ofNullable(task);
    }
}
