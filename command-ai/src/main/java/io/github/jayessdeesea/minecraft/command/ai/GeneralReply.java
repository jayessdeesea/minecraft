package io.github.jayessdeesea.minecraft.command.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The general model's structured reply. A wrapper object rather than a bare list: provider-native
 * structured output generally rejects a top-level JSON array as the response schema.
 */
record GeneralReply(@JsonPropertyDescription("Exactly one objective per squad") List<ObjectiveReply> objectives) {

    record ObjectiveReply(
            @JsonPropertyDescription("The squad's name, exactly as listed") String squad,
            @JsonPropertyDescription("true to keep the squad's standing order exactly as it is - the fields below are then "
                    + "ignored; false to give it a new objective") boolean keep,
            @JsonPropertyDescription("Why the objective changes, when keep is false and the squad has a standing order; "
                    + "empty when keeping it") String reason,
            @JsonPropertyDescription("The squad's task at the objective - this field, not the mission's wording, sets it: "
                    + "NONE; WOOD to chop logs around the objective; STONE to quarry it; PATROL_BASE to build a small "
                    + "roofed shelter at the objective and spend the night in it; FARM to till, plant and keep a wheat farm "
                    + "by the water at the objective; TREE_FARM to plant saplings round the objective; SECURE to light the ground round the objective with torches so no "
                    + "monster spawns on it; FORTIFY to wall the ground round the objective in; MINE to cut a staircase down at the objective's x and z to its y; "
                    + "PATROL to walk the ground round the objective, on watch")
            Task task,
            @JsonPropertyDescription("What the squad must do, in plain words for the squad leader") String mission,
            @JsonPropertyDescription("Objective x coordinate (east is positive)") double x,
            @JsonPropertyDescription("Objective y coordinate: the ground height there") double y,
            @JsonPropertyDescription("Objective z coordinate (north is negative)") double z,
            @JsonPropertyDescription("One sentence: the purpose and the end state you want") String intent) {
    }

    /**
     * The squad's task at its objective, at most one; NONE rather than null, so the schema always asks.
     * One field rather than a gather and a separate patrol-base flag: a model sets one choice reliably
     * and can't ask for both.
     */
    enum Task { NONE, WOOD, STONE, PATROL_BASE, FARM, TREE_FARM, SECURE, FORTIFY, MINE, PATROL }
}
