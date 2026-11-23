package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A platoon leader that decides by standing rules, no model (operator, 2026-11-23: the model and the rules gave
 * conflicting orders - soldier, squad leader and platoon leader are deterministic, and the only model at run time is
 * the one the operator talks to). As {@link DrillSquadLeader} is to a squad leader's model.
 *
 * <p>What it gives a squad is the standing work: tend what's built - the squads in the order of battle turn about, one
 * to the wheat, the next to the trees - at the camp, once there is one; before that, hold where it stands. Everything
 * above that is the chain of command's rules over what's given, as they were over a model's draft, and they outrank
 * it: the operator's missions a squad a step, the shortfalls, the night.
 */
public final class DrillPlatoonLeader implements PlatoonLeader {

    private volatile Optional<Foothold> foothold = Optional.empty();

    @Override
    public void receiveMission(String mission) {
        // The operator's missions reach the squads as missions (FarmMission), by rule: its words aren't read here.
    }

    @Override
    public void receiveStockpile(Stockpile stockpile) {
        // The stores bear on the shortfalls, which the rules over this deal out.
    }

    @Override
    public void receiveRemark(String remark) {
        // Said to the model the operator talks to: nothing here weighs words.
    }

    @Override
    public void receiveFoothold(Foothold foothold) {
        this.foothold = Optional.of(foothold);
    }

    @Override
    public void receiveDayWork(DayWork work) {
        // As the stockpile.
    }

    @Override
    public void receiveTime(TimeOfDay time) {
        // The night is the chain of command's rule.
    }

    @Override
    public List<Objective> decide(List<SquadStatus> squadStatuses) {
        List<Objective> objectives = new ArrayList<>();
        for (int i = 0; i < squadStatuses.size(); i++) {
            SquadStatus squad = squadStatuses.get(i);
            Optional<Position> camp = foothold.map(Foothold::camp);
            if (camp.isEmpty()) {
                Position here = squad.soldiers().isEmpty() ? new Position(0, 0, 0) : squad.soldiers().getFirst().position();
                objectives.add(new Objective("Hold where the squad stands", here, "No camp yet: nothing to tend"));
            } else if (i % 2 == 0) {
                objectives.add(new Objective("Keep the wheat farms", camp.get(), "Food for the force", new Objective.Task.Farm()));
            } else {
                objectives.add(new Objective("Keep the tree farms", camp.get(), "Wood that grows back", new Objective.Task.TreeFarm()));
            }
        }
        return objectives;
    }
}
