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
            @JsonPropertyDescription("What the squad must do") String mission,
            @JsonPropertyDescription("Objective x coordinate (east is positive)") double x,
            @JsonPropertyDescription("Objective y coordinate: the ground height there") double y,
            @JsonPropertyDescription("Objective z coordinate (north is negative)") double z,
            @JsonPropertyDescription("One sentence: the purpose and the end state you want") String intent,
            @JsonPropertyDescription("NONE, WOOD to have the squad chop logs around the objective, or STONE to quarry it")
            Gather gather) {
    }

    /** What the squad gathers around its objective; NONE rather than null, so the schema always asks. */
    enum Gather { NONE, WOOD, STONE }
}
