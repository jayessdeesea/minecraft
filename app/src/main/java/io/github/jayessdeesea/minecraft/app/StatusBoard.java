package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.TaskRegister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The garrison's status as the operator's chat page shows it ({@code GET /status}, {@link OperatorChat}): read
 * afresh from the runner and the chain of command at each asking, on the asker's thread - never a soldier's tick.
 */
@Component
class StatusBoard {

    private final Supplier<GarrisonStatus> source;

    @Autowired
    StatusBoard(SoldierRunner runner, ChainOfCommand chain, Roster roster) {
        this(() -> read(runner, chain, roster));
    }

    StatusBoard(Supplier<GarrisonStatus> source) {
        this.source = source;
    }

    /** The garrison now. */
    GarrisonStatus now() {
        return source.get();
    }

    private static GarrisonStatus read(SoldierRunner runner, ChainOfCommand chain, Roster roster) {
        long at = chain.millis();
        return new GarrisonStatus(at, roster.squads().stream().map(Roster.Squad::name).map(squad -> GarrisonStatus.Squad.of(squad, chain.objective(squad),
                runner.statuses(squad).stream().map(status -> soldier(runner, squad, status, at)).toList())).toList());
    }

    private static GarrisonStatus.Soldier soldier(SoldierRunner runner, String squad, SoldierStatus status, long at) {
        String unit = runner.unitOf(status.name(), squad);
        Optional<TaskRegister.Tasking> tasking = runner.taskings(unit).stream().filter(t -> t.who().equals(status.name())).findFirst();
        return GarrisonStatus.Soldier.of(status, unit, runner.state(status.name()).flatMap(s -> s.activity.now()), tasking, at);
    }
}
