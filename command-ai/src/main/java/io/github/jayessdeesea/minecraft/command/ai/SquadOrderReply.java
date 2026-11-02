package io.github.jayessdeesea.minecraft.command.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Risk;

import java.util.List;

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
        @JsonPropertyDescription("Blocks above or below the objective's y the squad may end up, at least 1: 3 normally, 1 to keep everyone on one floor of a building, up to 6 on steep hills")
        double verticalTolerance,
        @JsonPropertyDescription("LOW: nobody takes damage to keep moving. MEDIUM: a soldier may take a fall that leaves him at half health or more when there's no safe way. HIGH: falls down to 4 health, only when the mission needs it")
        Risk risk,
        @JsonPropertyDescription("Usually empty. Soldiers who move at a different risk than the squad's - for example one stuck soldier freed at MEDIUM while the rest stay LOW")
        List<SoldierRisk> soldierRisks,
        @JsonPropertyDescription("One sentence: why the squad is doing this") String intent,
        @JsonPropertyDescription("Empty if the mission is achievable; otherwise a short reason the squad cannot accomplish it")
        String cantco) {

    record SoldierRisk(
            @JsonPropertyDescription("The soldier's name, exactly as listed in the squad") String soldier,
            @JsonPropertyDescription("LOW, MEDIUM or HIGH, as for the squad") Risk risk) {
    }
}
