package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrillSquadLeaderTest {

    /**
     * A mine's objective is a depth: of two reachable spots over it, the one down the stairs, not the
     * hilltop beside them. Live, nearest by x and z alone sent a squad 66 blocks up a hill over its mine.
     */
    @Test
    void theNearestSpotCountsHeightSoAMineIsGoneDownTo() {
        Objective mine = new Objective("Cut the mine", new Position(0.5, 16, 0.5), "iron", new Objective.Task.Mine());
        TerrainSurvey terrain = new TerrainSurvey(0, 0, 8, 32, List.of(
                new TerrainSurvey.Sample(1, 0, TerrainSurvey.Ground.LAND, 82, true, 0),
                new TerrainSurvey.Sample(0, 2, TerrainSurvey.Ground.LAND, 40, true, 0)));

        assertEquals(Optional.of(new Position(0.5, 40, 2.5)), DrillSquadLeader.point(mine, terrain));
    }

    /** A tree farm's squad goes to its ground, not to the top of the canopy the survey finds over it. */
    @Test
    void aTreeFarmsPointIsItsGroundNotItsCanopy() {
        Objective farm = new Objective("Keep the tree farm at chunk (2, 3)", new Position(40.5, 64, 56.5), "Wood that grows back", new Objective.Task.TreeFarm());
        TerrainSurvey terrain = new TerrainSurvey(40, 56, 8, 32, List.of(
                new TerrainSurvey.Sample(41, 57, TerrainSurvey.Ground.LAND, 69, true, 0)));

        assertEquals(Optional.of(new Position(40.5, 64, 56.5)), DrillSquadLeader.point(farm, terrain));
    }

    /**
     * On a mission's ground work every soldier detached on a role comes back to the squad (operator, 2026-11-22; field696:
     * a tree farm's flatten with two of four, one a miner detached under an older order). On other work a detachment stands.
     */
    @Test
    void onAMissionsGroundWorkEverySoldierDetachedComesBack() {
        DrillSquadLeader bravo = new DrillSquadLeader("Bravo");
        Position chunk = new Position(40.5, 64, 56.5);
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Dana", chunk, new Order.Idle(), new Report.Idle(), Optional.empty()),
                new SoldierStatus("Eli", new Position(-37, 55, 50), new Order.Idle(), new Report.Idle(), Optional.empty()));
        bravo.receivePlans(java.util.Map.of("Eli", new Plan(Role.MINER, Optional.of(new Position(-30, 16, 40)), List.of())), java.util.Map.of());
        TerrainSurvey terrain = new TerrainSurvey(40, 56, 8, 32, List.of(new TerrainSurvey.Sample(40, 56, TerrainSurvey.Ground.LAND, 64, true, 0)));

        bravo.receiveObjective(new Objective("a tree farm at chunk (2, 3) at y=63: flatten", chunk, "the operator's tree farm", new Objective.Task.Keep()));
        assertEquals(List.of(Plan.Change.role("Eli", Role.RIFLEMAN, chunk)), bravo.decide(squad, terrain).orElseThrow().changes(), "the miner called in to the flatten");

        bravo.receiveObjective(new Objective("Secure the camp", chunk, "light", new Objective.Task.Secure()));
        assertEquals(List.of(), bravo.decide(squad, terrain).orElseThrow().changes(), "on other work the detachment stands");
    }

    /**
     * FILE once the point man reports his route blocked, and till he reports anything but moving, the point man is
     * another, or the objective is: his report goes between CANTCO and moving with each try at the route, and the
     * formation doesn't go with it.
     */
    @Test
    void aBlockedRouteIsFileTillThePointManIsInPosition() {
        DrillSquadLeader bravo = new DrillSquadLeader("Bravo");
        TerrainSurvey terrain = new TerrainSurvey(40, 56, 8, 32, List.of(new TerrainSurvey.Sample(40, 56, TerrainSurvey.Ground.LAND, 64, true, 0)));
        Objective camp = new Objective("Secure the camp", new Position(40.5, 64, 56.5), "light", new Objective.Task.Secure());
        java.util.function.Function<Report, Formation> formationOn = report -> bravo.decide(
                List.of(new SoldierStatus("Dana", new Position(10, 64, 10), new Order.Idle(), report, Optional.empty())), terrain).orElseThrow().formation();
        bravo.receiveObjective(camp);

        assertEquals(Formation.WEDGE, formationOn.apply(new Report.Moving()));
        assertEquals(Formation.FILE, formationOn.apply(new Report.Cantco("no route from (10, 64, 10) to within 0 of (40, 64, 56)")));
        assertEquals(Formation.FILE, formationOn.apply(new Report.Moving()), "trying the route again: still blocked");
        assertEquals(Formation.FILE, formationOn.apply(new Report.Cantco("no route from (10, 64, 10) to within 0 of (40, 64, 56)")));
        assertEquals(Formation.WEDGE, formationOn.apply(new Report.InPosition()), "through");
        assertEquals(Formation.WEDGE, formationOn.apply(new Report.Moving()));

        formationOn.apply(new Report.Cantco("no route"));
        assertEquals(Formation.WEDGE, formationOn.apply(new Report.Idle()), "at something else: not on that route");

        formationOn.apply(new Report.Cantco("no route"));
        assertEquals(Formation.WEDGE, bravo.decide(List.of(new SoldierStatus("Eli", new Position(12, 64, 10), new Order.Idle(), new Report.Moving(), Optional.empty())),
                terrain).orElseThrow().formation(), "another point man's route is another route");

        formationOn.apply(new Report.Cantco("no route"));
        bravo.receiveObjective(camp);
        assertEquals(Formation.FILE, formationOn.apply(new Report.Moving()), "the same objective given again changes nothing");
        bravo.receiveObjective(new Objective("Secure the ford", new Position(80.5, 64, 56.5), "light", new Objective.Task.Secure()));
        assertEquals(Formation.WEDGE, formationOn.apply(new Report.Cantco("no route")), "a CANTCO still standing from the last objective's route");
        assertEquals(Formation.WEDGE, formationOn.apply(new Report.Moving()), "another objective is another route");
    }

    /**
     * Down a mine the squad's order is one order: live (field741-742), a squad cutting a shaft was given 83 orders in
     * 44 minutes that differed only in formation or in a point read from the gallery its point man stood in - its
     * report going between mining and CANTCO, each survey from another gallery. From the first soldier that reports
     * mining, the point and FILE stand till the objective is another.
     */
    @Test
    void aSquadDownAMineKeepsOneOrder() {
        DrillSquadLeader alpha = new DrillSquadLeader("Alpha");
        Objective mine = new Objective("Mine the shaft", new Position(8.5, -59, 40.5), "ore", new Objective.Task.Mine());
        alpha.receiveObjective(mine);
        TerrainSurvey atTheHead = new TerrainSurvey(8, 40, 8, 32, List.of(new TerrainSurvey.Sample(8, 40, TerrainSurvey.Ground.LAND, 63, true, 0)));
        TerrainSurvey inAGallery = new TerrainSurvey(8, 40, 8, 32, List.of(new TerrainSurvey.Sample(-30, 44, TerrainSurvey.Ground.LAND, 4, true, 0)));
        TerrainSurvey inAnother = new TerrainSurvey(8, 40, 8, 32, List.of(new TerrainSurvey.Sample(52, 38, TerrainSurvey.Ground.LAND, 0, true, 0)));
        java.util.function.BiFunction<Report, TerrainSurvey, SquadOrder> on = (report, survey) -> alpha.decide(
                List.of(new SoldierStatus("Alice", new Position(8, 63, 40), new Order.Idle(), report, Optional.empty())), survey).orElseThrow();

        SquadOrder approach = on.apply(new Report.Moving(), atTheHead);
        assertEquals(Formation.WEDGE, approach.formation(), "on the way to the mine: as any move");
        SquadOrder down = on.apply(new Report.Mining(1, 100), atTheHead);
        assertEquals(Formation.FILE, down.formation(), "down a shaft: one file");
        assertEquals(new Position(8.5, 63, 40.5), down.objective(), "the point as it stood when the cutting began");
        assertEquals(down, on.apply(new Report.Cantco("can't cut the staircase further: no pickaxe"), inAGallery), "a cutter's CANTCO, a gallery's survey: the same order");
        assertEquals(down, on.apply(new Report.Mining(40, 100), inAnother), "another gallery's survey: the same order");
        assertEquals(down, on.apply(new Report.InPosition(), inAGallery), "nobody cutting this second: the same order");

        alpha.receiveObjective(new Objective("Secure the camp", new Position(8.5, 64, 40.5), "light", new Objective.Task.Secure()));
        assertEquals(Formation.WEDGE, on.apply(new Report.Moving(), atTheHead).formation(), "out of the mine: another objective, another order");
        alpha.receiveObjective(mine);
        assertEquals(Formation.WEDGE, on.apply(new Report.Moving(), atTheHead).formation(), "given the mine again: an approach, till a soldier is cutting");
    }

    /** With no surveyed ground to reach, the squad holds at one place: the same order however its point man moves, till there is ground. */
    @Test
    void aSquadWithNoGroundToReachHoldsAtOnePlace() {
        DrillSquadLeader bravo = new DrillSquadLeader("Bravo");
        TerrainSurvey none = new TerrainSurvey(40, 56, 8, 32, List.of(new TerrainSurvey.Sample(40, 56, TerrainSurvey.Ground.LAND, 64, false, 0)));
        TerrainSurvey some = new TerrainSurvey(40, 56, 8, 32, List.of(new TerrainSurvey.Sample(40, 56, TerrainSurvey.Ground.LAND, 64, true, 0)));
        bravo.receiveObjective(new Objective("Secure the camp", new Position(40.5, 64, 56.5), "light", new Objective.Task.Secure()));

        SquadOrder first = bravo.decide(List.of(new SoldierStatus("Dana", new Position(10, 64, 10), new Order.Idle(), new Report.Idle(), Optional.empty())), none).orElseThrow();
        SquadOrder second = bravo.decide(List.of(new SoldierStatus("Dana", new Position(12, 64, 11), new Order.Idle(), new Report.Idle(), Optional.empty())), none).orElseThrow();

        assertEquals(new Position(10, 64, 10), first.objective(), "where its point man stood when no ground was found");
        assertEquals(first, second, "and the same order a second later, the point man two blocks on");
        assertEquals(new Position(40.5, 64, 56.5), bravo.decide(List.of(new SoldierStatus("Dana", new Position(12, 64, 11), new Order.Idle(), new Report.Idle(), Optional.empty())), some).orElseThrow().objective());
        assertEquals(new Position(12, 64, 11), bravo.decide(List.of(new SoldierStatus("Dana", new Position(12, 64, 11), new Order.Idle(), new Report.Idle(), Optional.empty())), none).orElseThrow().objective(),
                "a hold after ground was found is a new one");
    }
}
