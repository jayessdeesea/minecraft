package io.github.jayessdeesea.minecraft.command;

/**
 * What a {@link General} hands a {@link SquadLeader} - OPORD-shaped but small
 * (docs/military-doctrine.md §5): the mission (what), where, and the commander's intent (why,
 * §1). The intent is what lets the squad leader adapt - pick a different spot near the location,
 * report CANTCO with a useful reason - without another round trip to the general. {@code gather}
 * tells the squad to collect a material around the location (§12).
 */
public record Objective(String mission, Position location, String intent, java.util.Optional<Resource> gather) {

    /** An objective with nothing to gather. */
    public Objective(String mission, Position location, String intent) {
        this(mission, location, intent, java.util.Optional.empty());
    }
}
