package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Phase;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Two of the day's guardrails over the platoon leader's orders, each loud when it acts: no mining before the
 * buildup, and a squad kept where it works rather than sent to a new spot for the same task each decision. Pure
 * but for the log.
 */
final class PhaseGuardrails {

    private static final Logger LOG = LogManager.getLogger(PhaseGuardrails.class);

    /** How far a new spot for the same task may lie from where a squad works for it to stay there instead. */
    static final double STEADY_BLOCKS = 48;

    private PhaseGuardrails() {
    }

    /**
     * {@code given}, or - a MINE before the buildup (operator, 2026-11-18: we don't want to lose the few resources
     * we have) - the food work instead. Live (field472), in the seizure five soldiers were down the mine by day.
     */
    static Objective noMineBeforeBuildup(Objective given, Phase phase, String squad, Optional<Objective> food) {
        if (!(given.task() instanceof Objective.Task.Mine) || !phase.beforeBuildup()) {
            return given;
        }
        Optional<Objective> instead = food;
        instead.ifPresent(o -> LOG.warn("[Guardrail] {} ordered to MINE in the {} - to '{}' instead: no mining before the buildup", squad, phase,
                o.mission()));
        return instead.orElse(given);
    }

    /**
     * Production work done anywhere on its ground - a farm, a grove, a stand of trees - where a new spot each
     * decision is churn. Not a wall, a mine or the keep, whose point the squad leader moves on with the work.
     */
    static boolean production(Objective.Task task) {
        return io.github.jayessdeesea.minecraft.command.WorkArea.reach(task).isPresent();
    }

    /**
     * {@code given}, or the squad's standing objective when that's the same task within {@link #STEADY_BLOCKS} and
     * its work hasn't stalled, for {@link #production} work: it stays and works rather than walking to a new spot
     * for the same job each decision.
     * Live (field472), Charlie was sent to farm at (-114, 64) and 20 s later at (-87, 58), Delta 40 blocks the same.
     */
    static Objective steady(Objective given, SquadStatus squad) {
        Optional<Objective> standing = squad.standing();
        if (standing.isEmpty() || standing.get().equals(given) || !standing.get().task().equals(given.task()) || !production(given.task())
                || squad.progress().filter(p -> p.state() == Progress.State.STALLED).isPresent()
                || Math.hypot(standing.get().location().x() - given.location().x(), standing.get().location().z() - given.location().z()) > STEADY_BLOCKS) {
            return given;
        }
        LOG.info("[Guardrail] {} kept on '{}' where it works, not moved {} blocks for the same task", squad.squadName(), standing.get().mission(),
                Math.round(Math.hypot(standing.get().location().x() - given.location().x(), standing.get().location().z() - given.location().z())));
        return standing.get();
    }
}
