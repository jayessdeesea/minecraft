package io.github.jayessdeesea.minecraft.command.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;

/**
 * The squad leader model's structured reply - the JSON schema handed to the model is generated
 * from this record, descriptions included. Kept separate from the domain {@code SquadOrder} so
 * schema wording and Jackson annotations stay out of the framework-free {@code command} module,
 * and so validation (see {@link SpringAiSquadLeader#toSquadOrder}) has a seam to sit in.
 */
record SquadOrderReply(
        @JsonPropertyDescription("Objective x coordinate (east is positive)") double x,
        @JsonPropertyDescription("Objective y coordinate: the ground height there") double y,
        @JsonPropertyDescription("Objective z coordinate (north is negative)") double z,
        @JsonPropertyDescription("WEDGE, FILE or LINE") Formation formation,
        @JsonPropertyDescription("TIGHT, NORMAL or EXTENDED spacing between soldiers") Interval interval,
        @JsonPropertyDescription("How many blocks above or below the objective's y the squad may end up; small keeps everyone on one floor")
        double verticalTolerance,
        @JsonPropertyDescription("One sentence: why the squad is doing this") String intent,
        @JsonPropertyDescription("Empty if the mission is achievable; otherwise a short reason the squad cannot accomplish it")
        String cantco) {
}
