package io.github.jayessdeesea.minecraft.command;

/**
 * The "what I'm doing / what I can't do" line of a soldier's SITREP (docs/military-doctrine.md §4).
 * Closed on purpose, like {@link Order}: a squad leader's reasoning over these must handle every
 * case.
 */
public sealed interface Report {

    /** No task. */
    record Idle() implements Report {
    }

    /** Executing the current order (WILCO, in progress). */
    record Moving() implements Report {
    }

    /** At the ordered position. */
    record InPosition() implements Report {
    }

    /** Hostiles within contact range - the contact reflex has taken over (SPOTREP-worthy, §4). */
    record InContact(int hostiles) implements Report {
    }

    /**
     * CANTCO - "unable to comply" (docs/military-doctrine.md §3): the order can't be carried out,
     * and {@code reason} says why, so the echelon above can re-task instead of waiting on a soldier
     * that will never arrive.
     */
    record Cantco(String reason) implements Report {
    }

    /**
     * Respawned and rejoining (docs/military-doctrine.md §6): holding at the rally point while the
     * squad is in contact, otherwise closing on the squad. Assigned by {@link LinkUp} from the
     * squad's view - a soldier doesn't know where its squad is - never by the soldier itself.
     */
    record LinkingUp() implements Report {
    }

    /**
     * Stranded, and its squad's recovery drill getting it out (docs/military-doctrine.md §7): a
     * squadmate sent with blocks, or steps it builds or digs itself. Assigned from the squad's view,
     * as {@link LinkingUp} is - the soldier itself reports its CANTCO. Live, a squad leader shown
     * that CANTCO took a stranded soldier for the squad's own and reported the mission one.
     */
    record Recovering() implements Report {
    }

    /**
     * Bare-handed and making itself a weapon (docs/military-doctrine.md §11): chopping wood and
     * crafting a wooden sword, with the squad leader's leave. About a minute; it drops the job the
     * moment a hostile comes near.
     */
    record Rearming() implements Report {
    }

    /** Gathering a material around the squad's objective (docs/military-doctrine.md §12). */
    record Gathering(Resource resource) implements Report {
    }

    /** Building the squad's patrol base (docs/military-doctrine.md §14). */
    /** On an {@link Order.Dig} or {@link Order.Fill}: {@code left} of its box's cells still to do. */
    record Working(String command, int left) implements Report {
    }

    record Fortifying() implements Report {
    }

    /** Working the squad's farm: tilling, planting, harvesting, baking bread. */
    record Farming() implements Report {
    }

    /** Hungry with nothing to eat: killing an animal for meat. */
    record Hunting() implements Report {
    }

    /** Planting the squad's tree farm: saplings in, and leaves broken for more. */
    record Planting() implements Report {
    }

    /** Lighting the ground round the objective so no monster spawns on it - making torches, putting them down. */
    record Securing() implements Report {
    }

    /** Walking its patrol round the objective (PATROL): moving, on watch. */
    record Patrolling() implements Report {
    }

    /** Building a structure laid out for it ({@link Order.Place}): {@code placed} of its {@code cells} in. */
    record Building(int placed, int cells) implements Report {
    }

    /** Cutting the squad's staircase down: on step {@code step} of {@code steps}. */
    record Mining(int step, int steps) implements Report {
    }

    /** Handing logs to a squadmate who needs them for a tool (cross-leveling, docs/military-doctrine.md §13). */
    record CrossLeveling(String to, CrossLevel.Item item, int count) implements Report {
    }
}
