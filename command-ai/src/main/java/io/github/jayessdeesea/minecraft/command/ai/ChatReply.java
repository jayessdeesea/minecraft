package io.github.jayessdeesea.minecraft.command.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The platoon leader's structured reply to one of the operator's turns: its answer in words, and at most one action
 * from a closed set - each one of the operator's chat orders.
 */
public record ChatReply(
        @JsonPropertyDescription("Your answer to the operator, in plain words") String reply,
        @JsonPropertyDescription("The one action the operator asked for in this turn, NONE if none: MISSION, ESCORT, FLATTEN or FARM")
        Action action,
        @JsonPropertyDescription("The action's argument as the system prompt gives its form; empty for NONE") String argument) {

    /** What the platoon leader may do from a conversation; NONE rather than null, so the schema always asks. */
    public enum Action { NONE, MISSION, ESCORT, FLATTEN, FARM }
}
