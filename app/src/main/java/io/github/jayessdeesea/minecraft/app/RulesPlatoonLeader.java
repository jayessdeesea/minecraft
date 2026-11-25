package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DayWork;
import io.github.jayessdeesea.minecraft.command.DecidesByRules;
import io.github.jayessdeesea.minecraft.command.DrillPlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;

import java.util.List;

/**
 * The platoon leader deciding by drills ({@link DrillPlatoonLeader}; operator, 2026-11-23: no model in the chain of
 * command but the one the operator talks to). That model - {@code told} - is still told what the platoon is told: the
 * mission, the stores, the foothold, the time, since the operator's chat answers from it. It is never asked to decide.
 */
final class RulesPlatoonLeader implements PlatoonLeader, DecidesByRules {

    private final PlatoonLeader drills = new DrillPlatoonLeader();
    private final PlatoonLeader told;

    RulesPlatoonLeader(PlatoonLeader told) {
        this.told = told;
    }

    @Override
    public void receiveMission(String mission) {
        told.receiveMission(mission);
        drills.receiveMission(mission);
    }

    @Override
    public void receiveStockpile(Stockpile stockpile) {
        told.receiveStockpile(stockpile);
        drills.receiveStockpile(stockpile);
    }

    @Override
    public void receiveRemark(String remark) {
        told.receiveRemark(remark);
        drills.receiveRemark(remark);
    }

    @Override
    public void receiveFoothold(Foothold foothold) {
        told.receiveFoothold(foothold);
        drills.receiveFoothold(foothold);
    }

    @Override
    public void receiveDayWork(DayWork work) {
        told.receiveDayWork(work);
        drills.receiveDayWork(work);
    }

    @Override
    public void receiveTime(TimeOfDay time) {
        told.receiveTime(time);
        drills.receiveTime(time);
    }

    @Override
    public List<Objective> decide(List<SquadStatus> squadStatuses) {
        return drills.decide(squadStatuses);
    }
}
