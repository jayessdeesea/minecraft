package io.github.jayessdeesea.minecraft.command.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.jayessdeesea.minecraft.command.Formation;
import io.github.jayessdeesea.minecraft.command.Interval;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.Role;
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
        @JsonPropertyDescription("true lets soldiers stop to make themselves better kit while no fight is near - a bare-handed one a sword (about a minute), and away from a patrol base iron armour and weapons from wood and ore they dig nearby (several minutes); false keeps everyone moving")
        boolean rearm,
        @JsonPropertyDescription("One sentence: why the squad is doing this") String intent,
        @JsonPropertyDescription("Empty if the mission is achievable; otherwise a short reason the squad cannot accomplish it")
        String cantco,
        @JsonPropertyDescription("Empty unless you give a soldier a different role from the one his line shows, with the site he works: never repeat a role he already has")
        List<RoleChange> roleChanges,
        @JsonPropertyDescription("Empty unless you send one soldier away from the squad on a job of his own. The squad's own move is your x, y, z, and every soldier follows it: never list soldiers here to move them with the squad")
        List<SoldierOrder> detached) {

    record RoleChange(
            @JsonPropertyDescription("The soldier's name, exactly as listed in the squad") String soldier,
            @JsonPropertyDescription("RIFLEMAN to put him back with the squad; otherwise the job he does at the site") Role role,
            @JsonPropertyDescription("The site's x (ignored for RIFLEMAN)") double x,
            @JsonPropertyDescription("The site's y: the ground height there, or for a MINER the depth to mine down to") double y,
            @JsonPropertyDescription("The site's z (ignored for RIFLEMAN)") double z) {
    }

    record SoldierOrder(
            @JsonPropertyDescription("The soldier's name, exactly as listed in the squad") String soldier,
            @JsonPropertyDescription("GO_TO (x, y, z), FETCH (amount of resource from round x, y, z), LEVEL (the ground from x, z to x2, z2 made level at y), or CANCEL (drop what he had to do first)")
            Kind order,
            @JsonPropertyDescription("x of the point, or the LEVEL box's first corner") double x,
            @JsonPropertyDescription("y of the point, or the height LEVEL makes the ground's top a soldier stands at") double y,
            @JsonPropertyDescription("z of the point, or the LEVEL box's first corner") double z,
            @JsonPropertyDescription("For FETCH: WOOD, STONE or SOIL") Resource resource,
            @JsonPropertyDescription("For FETCH: how many to carry") int amount,
            @JsonPropertyDescription("For LEVEL: the box's other corner's x (at most 31 blocks from x)") double x2,
            @JsonPropertyDescription("For LEVEL: the box's other corner's z (at most 31 blocks from z)") double z2) {
    }

    enum Kind {
        GO_TO, FETCH, LEVEL, CANCEL
    }

    record SoldierRisk(
            @JsonPropertyDescription("The soldier's name, exactly as listed in the squad") String soldier,
            @JsonPropertyDescription("LOW, MEDIUM or HIGH, as for the squad") Risk risk) {
    }
}
