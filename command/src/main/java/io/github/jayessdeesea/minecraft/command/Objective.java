package io.github.jayessdeesea.minecraft.command;

/**
 * What a {@link General} hands a {@link SquadLeader} - OPORD-shaped but small
 * (docs/military-doctrine.md §5): the mission (what), where, and the commander's intent (why,
 * §1). The intent is what lets the squad leader adapt - pick a different spot near the location,
 * report CANTCO with a useful reason - without another round trip to the general.
 */
public record Objective(String mission, Position location, String intent) {
}
