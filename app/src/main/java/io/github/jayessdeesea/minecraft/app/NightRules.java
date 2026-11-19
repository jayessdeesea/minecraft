package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Phase;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import static io.github.jayessdeesea.minecraft.app.ChainOfCommand.*;
import static io.github.jayessdeesea.minecraft.app.DayWorkRules.at;

/**
 * The operator's night rules over the platoon leader's orders (docs/echelons/platoon-leader/operator-rules.md): the
 * shelter from just before dark - in the camp before the buildup, the night shift down the mine from it, a patrol base
 * with no camp - the climb out before dawn, and every objective put on the ground. Pure over the {@link Night} it's
 * handed, but for the log.
 */
final class NightRules {

    private static final Logger LOG = LogManager.getLogger(NightRules.class);

    /**
     * The night as the chain of command has it: whether the shelter rule is on, the time, the camp, the phase, whether
     * the keep is built by day, the keep's mine, whether there are no squads, each squad's standing patrol base, how
     * the ground is read, and each squad's own point round a shared one.
     */
    record Night(boolean shelterAtNight, TimeOfDay time, Optional<Foothold> foothold, Phase phase, boolean keepByDay, Position keepMine,
                 boolean noSquads, Function<String, Optional<Objective>> standingBase, Ground ground,
                 java.util.function.BiFunction<Position, String, Position> spread) {
    }

    private NightRules() {
    }

    /** Whether the night rule holds now: under the shelter rule, from just before dark until day. */
    static boolean sheltering(Night n) {
        TimeOfDay now = n.time();
        return n.shelterAtNight() && now != null && (now.dark() || now.ticksUntilDark() <= SHELTER_LEAD_TICKS);
    }

    /**
     * {@code objective}, or - at night, under the shelter rule, when the platoon leader's order
     * would leave a squad in the open or take it out of the base it is sealed in - the patrol base it was already
     * ordered to build, or else one where the squad's point man stands. A guardrail, and a loud one: the platoon leader's own order is logged beside it.
     */
    static Objective sheltered(Night n, Objective objective, SquadStatus squad) {
        TimeOfDay now = n.time();
        if (!n.shelterAtNight() || now == null || !(now.dark() || now.ticksUntilDark() <= SHELTER_LEAD_TICKS)
                || squad.soldiers().isEmpty()) {
            return objective;
        }
        // The night isn't wasted: every squad goes down the camp's mine (operator, 2026-11-13) -
        // whatever the platoon leader orders, a patrol base included: seeing the squad out of one, it
        // ordered one two minutes into the shift. Its shift ends in time to climb out at sunrise.
        Optional<Objective> nightShift = nightShift(n);
        if (nightShift.isPresent() && surfacing(n)) {
            return objective;
        }
        if (nightShift.isPresent() && objective.equals(nightShift.get())) {
            return objective;
        }
        // With no camp to go down the mine of, a squad already sealed in is the one most to keep there,
        // and a base the platoon leader orders is the night's.
        Position at = squad.soldiers().getFirst().position();
        // Before the buildup, shelter in place in the camp, not a patrol base wherever it stands (operator, 2026-11-18).
        Foothold base = n.foothold().orElse(null);
        if (n.phase().beforeBuildup() && base != null) {
            // Each squad its own point round the camp's: live (field482), all four on the camp's centre crowded each
            // other 69 times a night, soldiers of three squads digging the one cell.
            Objective inCamp = new Objective("Shelter in place in the camp through the night", n.spread().apply(base.camp(), squad.squadName()),
                    "Safe until dawn", new Objective.Task.None());
            if (!objective.equals(inCamp)) {
                LOG.warn("[PL -> {}] at or near night, in {}, ordered '{}' - shelters in place in the camp", squad.squadName(), n.phase(), objective.mission());
            }
            return inCamp;
        }
        if (nightShift.isEmpty() && objective.patrolBase()) {
            return objective;
        }
        if (nightShift.isPresent()) {
            LOG.warn("[PL -> {}] at or near night, ordered '{}' ({}) - the operator's rule sends it down the camp's mine instead, at {}",
                    squad.squadName(), objective.mission(), objective.task(), at(nightShift.get().location()));
            return nightShift.get();
        }
        // A base already ordered is the one to finish: sited again wherever the point man stands at
        // each decision, a squad starts hut after hut and finishes none before dawn.
        Optional<Objective> standing = n.standingBase().apply(squad.squadName());
        if (standing.isPresent()) {
            LOG.warn("[PL -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule keeps it on the patrol base it has",
                    squad.squadName(), objective.mission(), objective.task());
            return standing.get();
        }
        Objective shelter = grounded(n.ground(), new Objective("Build a patrol base where you stand and spend the night in it",
                new Position(Math.floor(at.x()), at.y(), Math.floor(at.z())), "Squad safe from monsters until dawn",
                new Objective.Task.PatrolBase()), squad.soldiers());
        LOG.warn("[PL -> {}] at or near night, out of a patrol base, ordered '{}' ({}) - the operator's rule shelters it instead, at {}",
                squad.squadName(), objective.mission(), objective.task(), at(shelter.location()));
        return shelter;
    }

    /** Whether the night shift is climbing out: dark, and sunrise within {@link #SURFACE_LEAD_TICKS}. */
    static boolean surfacing(Night n) {
        TimeOfDay now = n.time();
        return now != null && now.dark() && now.ticksUntilLight() <= SURFACE_LEAD_TICKS;
    }

    /**
     * The night's work for every squad at the camp's mine - every squad's, its stairs shared
     * (operator, 2026-11-13: both squads work the mine at night) - or empty with no camp measured yet.
     */
    static Optional<Objective> nightShift(Night n) {
        Foothold camp = n.foothold().orElse(null);
        // Before the buildup, nobody down the mine by night (operator, 2026-11-18: not safe; we don't want to lose the
        // few resources we have). Live (field463-470), most deaths were underground at night.
        if (camp == null || n.noSquads() || n.phase().beforeBuildup()) {
            return Optional.empty();
        }
        // Building the keep by day, the night's mine is the keep's own, beside it: from the camp's,
        // 65 blocks off and 48 down, the climb out and the walk to the keep were two-fifths of the
        // day (field151; operator, 2026-11-13: cutting a second mine is fine).
        if (n.keepByDay()) {
            return Optional.of(new Objective("Cut the keep's mine down to the iron through the night", n.keepMine(),
                    "Stone for the keep's slab, and the night spent working underground beside it", new Objective.Task.Mine()));
        }
        Position c = camp.camp();
        return Optional.of(new Objective("Cut the camp's mine down to the iron through the night",
                new Position(Math.floor(c.x()) + MINE_HEAD_DX, MINE_DEPTH, Math.floor(c.z()) + MINE_HEAD_DZ),
                "Stone and iron for the camp, and the night spent working underground", new Objective.Task.Mine()));
    }

    /**
     * {@code objective} with its height the ground's at its x and z, looked for near the squad's
     * middle height: the platoon leader guesses heights, and a guess copied from elsewhere in its prompt
     * lands an objective in the sky or underground. MINE keeps its own: that y is a depth.
     */
    static Objective grounded(Ground ground, Objective objective, List<SoldierStatus> soldiers) {
        if (objective.mine() || soldiers.isEmpty()) {
            return objective;
        }
        Position at = objective.location();
        // Near the squad's middle height, not its first soldier's: one stranded up a tower or down a
        // hole would take every objective there with it.
        double[] heights = soldiers.stream().mapToDouble(s -> s.position().y()).sorted().toArray();
        int nearY = (int) Math.floor(heights[heights.length / 2]);
        int x = (int) Math.floor(at.x()), z = (int) Math.floor(at.z());
        OptionalInt height = ground.heightAt(x, z, nearY);
        // From a squad down a mine, the ground near its height is the shaft's floor or a cave's: the
        // objective goes on the surface when that's well above. Live, a wood-gathering objective at
        // the keep mine's head went to the shaft's bottom, 40 blocks down, and the squad found no tree.
        OptionalInt surface = ground.surfaceAt(x, z, nearY);
        if (surface.isPresent() && (height.isEmpty() || surface.getAsInt() - height.getAsInt() > UNDERGROUND)) {
            height = surface;
        }
        if (height.isEmpty() || height.getAsInt() == (int) Math.floor(at.y())) {
            return objective;
        }
        LOG.info("[PL] objective at x={} z={} put on the ground at y={}, not the y={} given", (int) Math.floor(at.x()),
                (int) Math.floor(at.z()), height.getAsInt(), (int) Math.floor(at.y()));
        return new Objective(objective.mission(), new Position(at.x(), height.getAsInt(), at.z()), objective.intent(), objective.task());
    }
}
