package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Phase;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static io.github.jayessdeesea.minecraft.app.DayWorkRules.*;

/**
 * The platoon leader's day guardrails (operator, 2026-11-17: the platoon leader says who farms, who works the grove
 * and who flattens): the keep-by-day rule over each squad's objective, and the guardrails over all of them at once.
 * Each move said, loudly: the platoon leader's to get right. Pure over the {@link Day} it's handed, but for the log.
 */
final class DayGuardrails {

    private static final Logger LOG = LogManager.getLogger(DayGuardrails.class);

    /**
     * The day as the chain of command has it: whether the keep-by-day rule is in force now (by day, out of the night's
     * shelter, or the night shift surfacing), the farm squad, the camp, the ground to flatten, the phase, the builders
     * its level wants, the stores, the squads in the order of battle, each squad's own point round a shared one, the
     * keep's next mine and its mine as it stands.
     */
    record Day(boolean inForce, Optional<String> farmSquad, Optional<Foothold> foothold, Flatten flatten, Phase phase, int flattenBuilders,
               Stockpile stores, List<String> squads, BiFunction<Position, String, Position> spread, Supplier<Optional<Position>> nextMine,
               Position keepMine) {
    }

    private DayGuardrails() {
    }

    /**
     * As {@code ChainOfCommand.byDay}, the released squads dealt across every
     * shortfall in {@code alarms} in turn, food first (PlatoonSergeant#shortfalls): the first released
     * squad to the first, the next to the next, round again - with three squads and food and wood in
     * alarm, two farm and one cuts (operator, 2026-11-17: more people means more throughput).
     */
    static Objective byDay(Day d, Objective objective, SquadStatus squad, List<PlatoonSergeant.Shortfall> alarms, List<ResourceSite> woods) {
        if (!d.inForce()) {
            return objective;
        }
        // The farm squad keeps the camp's farm, not the ground - whatever it's given: its standing job by day.
        if (d.farmSquad().filter(squad.squadName()::equals).isPresent()) {
            Foothold base = d.foothold().orElse(null);
            Position at = base != null ? base.camp() : objective.location();
            if (!(objective.task() instanceof Objective.Task.None) || !objective.location().equals(at)) {
                LOG.info("[PL -> {}] by day, the farm squad: keeps the camp's farm, not '{}'", squad.squadName(), objective.mission());
            }
            return new Objective("Keep the camp's farm", at, "Food for the force: the farm squad's standing job", new Objective.Task.None());
        }
        // The platoon leader's day's work stands - who farms, who works a grove, who cuts, mines and flattens
        // (operator, 2026-11-17); the guardrails over all of it come after (guarded).
        if (dayWork(objective.task())) {
            return objective.task() instanceof Objective.Task.Keep
                    ? new Objective("Flatten " + d.flatten().describe(), d.spread().apply(d.flatten().centre(), squad.squadName()),
                    "The ground the operator ordered flattened", new Objective.Task.Keep())
                    : objective;
        }
        // Only as many squads on the ground as its level wants (backlog/flatten-campaign.md): the rest
        // released - to the platoon leader's own objective for them, or the camp's work where that's
        // the ground too - and called back as the levels widen. Live, every squad stood on a level
        // of a few blocks, a third of the crew's time spent with nothing to dig.
        List<String> builders = d.squads().stream().filter(n -> d.farmSquad().filter(n::equals).isEmpty()).toList();
        int wanted = d.flattenBuilders();
        int perSquad = Math.max(1, squad.soldiers().size());
        int squadsWanted = wanted == Integer.MAX_VALUE ? builders.size() : Math.max(1, (wanted + perSquad - 1) / perSquad);
        // Short of anything, one squad on the ground and the rest to what's short (operator, 2026-11-17: one
        // squad on it during the day, two or three collecting resources if necessary). Live (field416), a level
        // wanting 40 builders kept every squad on it with food and wood both short.
        if (!alarms.isEmpty() || d.phase() == Phase.CONSOLIDATION) {
            squadsWanted = Math.min(squadsWanted, 1);
        }
        if (d.phase() == Phase.SEIZURE) {
            squadsWanted = 0; // the camp secured and every soldier armed before the mission (Phase)
        }
        if (builders.indexOf(squad.squadName()) >= squadsWanted) {
            int released = builders.indexOf(squad.squadName()) - squadsWanted;
            Optional<PlatoonSergeant.Shortfall> shortfall = alarms.isEmpty() ? Optional.empty() : Optional.of(alarms.get(released % alarms.size()));
            Optional<Objective> needed = shortfall.flatMap(s -> toShortfall(d, s, squad, woods, wanted));
            // Not sent again to what it's just said it can't do there: live (field296), a squad sent
            // for wood to trees already cut said "nothing left to gather" and was sent back each decision.
            boolean stalledOnIt = needed.isPresent() && squad.standing().filter(o -> o.task().equals(needed.get().task())).isPresent()
                    && squad.progress().filter(p -> p.state() == Progress.State.STALLED).isPresent();
            if (stalledOnIt) {
                LOG.info("[PL -> {}] released from the flatten: its {} shortfall work stalled ({}) - the platoon leader's objective instead",
                        squad.squadName(), shortfall.get(), squad.progress().get().detail());
            }
            if (needed.isPresent() && !stalledOnIt) {
                if (!needed.get().equals(squad.standing().orElse(null))) {
                    LOG.info("[PL -> {}] released from the flatten ({}): to the force's {} shortfall - '{}'",
                            squad.squadName(), buildersWanted(wanted), shortfall.get(), needed.get().mission());
                }
                return needed.get();
            }
            if (!(objective.task() instanceof Objective.Task.Keep)) {
                return objective;
            }
            // The next mine cut ahead of time, by day, so the night shift moves to stairs already cut.
            Optional<Position> nextMine = d.nextMine().get();
            if (nextMine.isPresent()) {
                return new Objective("Cut the next mine while the ground's level is small", nextMine.get(),
                        "Released from the flatten: the keep's next mine cut before its first is spent", new Objective.Task.Mine());
            }
            // Nothing else to do: the stockpile's logs toward their most, then its bread toward its most -
            // the lowest priorities there are (operator, 2026-11-17: low priority, but if the workers are
            // idle, do it).
            Stockpile held = d.stores();
            if (held != null && held.logs() < Keep.LOGS_MOST) {
                Optional<Objective> wood = toShortfall(d, PlatoonSergeant.Shortfall.WOOD, squad, woods, wanted);
                if (wood.isPresent()) {
                    return wood.get();
                }
            }
            if (held != null && held.rations() < Subsistence.STOCKPILE_MOST) {
                Optional<Objective> farm = toShortfall(d, PlatoonSergeant.Shortfall.FOOD, squad, woods, wanted);
                if (farm.isPresent()) {
                    return farm.get();
                }
            }
            Foothold base = d.foothold().orElse(null);
            return new Objective("Work the camp while the ground's level is small", base != null ? base.camp() : objective.location(),
                    "Released from the flatten: " + buildersWanted(wanted), new Objective.Task.None());
        }
        Flatten site = d.flatten();
        Objective keep = new Objective("Flatten " + site.describe(),
                d.spread().apply(site.centre(), squad.squadName()), "The ground the operator ordered flattened", new Objective.Task.Keep());
        LOG.warn("[PL -> {}] by day, ordered '{}' ({}) - the operator's rule puts it on the keep instead",
                squad.squadName(), objective.mission(), objective.task());
        return keep;
    }

    /**
     * The guardrails over the day's work, all squads at once (operator, 2026-11-17: one squad on the flatten,
     * two or three collecting resources if necessary): if no squad flattens, one is put on it; if something
     * the force is short of has nobody on it, a squad doubling up on another shortfall - or on the flatten
     * while another is too - is moved to it. Each move is said, loudly: the platoon leader's to get right.
     */
    static List<Objective> guarded(Day d, List<Objective> day, List<SquadStatus> statuses, List<PlatoonSergeant.Shortfall> alarms,
                                   List<ResourceSite> woods) {
        if (!d.inForce()) {
            return day;
        }
        List<Objective> out = new ArrayList<>(day);
        List<Integer> movable = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            // Nor a squad on the platoon leader's FORAGE for its own Class I RED - its answer to that squad's hunger, today's
            // food; a hungry squad on FARM is still dealt across the shortfalls (operator, 2026-11-17). Live (field513), hungry
            // Charlie, sent to forage 573 ripe wheat, was taken by the wall's guardrail and then the wood's.
            boolean feedingItself = statuses.get(i).classOne().filter(c -> c.rating() == io.github.jayessdeesea.minecraft.command.Subsistence.Rating.RED)
                    .isPresent() && day.get(i).task() instanceof Objective.Task.Forage;
            if (d.farmSquad().filter(statuses.get(i).squadName()::equals).isEmpty() && !feedingItself) {
                movable.add(i);
            }
        }
        if (d.phase() != Phase.SEIZURE && !movable.isEmpty() && out.stream().noneMatch(o -> o.task() instanceof Objective.Task.Keep)) {
            // The squad already flattening, if one is, so the flatten keeps its crew: live (field418), the platoon
            // leader put nobody on it twice running and the guardrail moved Charlie, then Alpha, two minutes apart.
            // But not one the platoon leader has just put on a shortfall: live (field431-439), it sent Charlie for wood,
            // the guardrail took Charlie back onto the flatten as its standing keeper, and then sent Bravo off its
            // own order to the wood - two of its decisions undone where one squad not on a shortfall would do.
            java.util.function.IntPredicate onAShortfall = i -> alarms.stream().anyMatch(s -> covers(out.get(i), s));
            Optional<Integer> flattening = movable.stream()
                    .filter(i -> statuses.get(i).standing().filter(o -> o.task() instanceof Objective.Task.Keep).isPresent())
                    .filter(i -> !onAShortfall.test(i)).findFirst();
            int k = flattening.or(() -> spare(out, movable, alarms))
                    .or(() -> movable.stream().filter(i -> !onAShortfall.test(i)).findFirst())
                    .orElse(movable.getFirst());
            String name = statuses.get(k).squadName();
            LOG.warn("[Guardrail] no squad on the flatten - {} put on it, not '{}'", name, out.get(k).mission());
            out.set(k, new Objective("Flatten " + d.flatten().describe(), d.spread().apply(d.flatten().centre(), name),
                    "The ground the operator ordered flattened", new Objective.Task.Keep()));
        }
        for (PlatoonSergeant.Shortfall s : alarms) {
            boolean coveredByFarmSquad = s == PlatoonSergeant.Shortfall.FOOD && d.farmSquad().isPresent();
            // With no trees seen - the squads up from a night underground - the keep's own wood party is on it,
            // cutting the trees the world model holds near the depot (SoldierRunner.knownTree). Live (field419), at
            // dawn short of wood with no trees reported, the guardrail found nowhere to send anyone.
            boolean coveredByTheKeep = s == PlatoonSergeant.Shortfall.WOOD && woods.stream().noneMatch(w -> w.resource() == Resource.WOOD)
                    && out.stream().anyMatch(o -> o.task() instanceof Objective.Task.Keep);
            if (coveredByFarmSquad || coveredByTheKeep || out.stream().anyMatch(o -> covers(o, s))) {
                continue;
            }
            Optional<Integer> k = spare(out, movable, alarms);
            Optional<Objective> work = k.flatMap(i -> toShortfall(d, s, statuses.get(i), woods, d.flattenBuilders()));
            if (work.isEmpty()) {
                LOG.warn("[Guardrail] short of {} with nobody on it, and no squad to spare or nowhere to do it", s);
                continue;
            }
            LOG.warn("[Guardrail] short of {} with nobody on it - {} to it, not '{}'", s, statuses.get(k.get()).squadName(), out.get(k.get()).mission());
            out.set(k.get(), work.get());
        }
        // A squad producing stays on it to the product's most (operator, 2026-11-18; ProductionLevels.wanted) - not
        // when it's moved to something short, or it's the one squad on the flatten.
        for (int i : movable) {
            Optional<Objective> was = statuses.get(i).standing();
            Optional<PlatoonSergeant.Shortfall> making = was.flatMap(DayWorkRules::producing);
            int j = i;
            boolean onlyFlattener = out.get(i).task() instanceof Objective.Task.Keep
                    && out.stream().filter(o -> o.task() instanceof Objective.Task.Keep).count() == 1;
            if (making.isPresent() && !done(statuses.get(i)) && underItsMost(d.stores(), making.get()) && producing(out.get(i)).isEmpty() && !onlyFlattener
                    && alarms.stream().noneMatch(s -> covers(out.get(j), s))) {
                LOG.warn("[Guardrail] {} kept on its {} work to the stock's most - not '{}'", statuses.get(i).squadName(), making.get(), out.get(i).mission());
                out.set(i, was.get());
            }
        }
        // The airhead's lights, safety first (docs/military-doctrine.md § 17): in the SEIZURE a camp unlit with nobody on
        // SECURE has a squad spare of the shortfalls put on it - the standard the SEIZURE ends on. Live (field517), with
        // the wall no standard, every squad farmed all day and the camp's 52 dark cells stood.
        d.foothold().filter(f -> d.phase() == Phase.SEIZURE && f.standards().stream().anyMatch(s -> !s.met() && "SECURE".equals(s.task()))
                        && out.stream().noneMatch(o -> o.task() instanceof Objective.Task.Secure))
                .ifPresent(f -> spare(out, movable, alarms).ifPresent(k -> {
                    LOG.warn("[Guardrail] the camp unlit with nobody on it - {} to SECURE, not '{}'", statuses.get(k).squadName(), out.get(k).mission());
                    out.set(k, new Objective("Light the camp", f.camp(), "The airhead lit: safety first", new Objective.Task.Secure()));
                }));
        // Wood that grows back, past the SEIZURE (operator, 2026-11-20: grow more trees): unmet with nobody on TREE_FARM, a squad
        // spare of the shortfalls is put on it. Live (field565), 36 of the groves' 48 cells planted, two squads on the farm and
        // nobody planting.
        d.foothold().filter(f -> d.phase() != Phase.SEIZURE && f.standards().stream().anyMatch(s -> !s.met() && "TREE_FARM".equals(s.task()))
                        && out.stream().noneMatch(o -> o.task() instanceof Objective.Task.TreeFarm))
                .ifPresent(f -> spare(out, movable, alarms).ifPresent(k -> {
                    LOG.warn("[Guardrail] the groves short of trees with nobody planting - {} to TREE_FARM, not '{}'", statuses.get(k).squadName(),
                            out.get(k).mission());
                    out.set(k, new Objective("Plant the groves", f.standards().stream().filter(s -> !s.met() && "TREE_FARM".equals(s.task()))
                            .flatMap(s -> s.at().stream()).findFirst().orElse(f.camp()), "Wood that grows back", new Objective.Task.TreeFarm()));
                }));
        // A TREE_FARM goes where the groves are bare - the camp's tree chunk - wherever the platoon leader put it: live
        // (field647), squads were moved to its point 40 blocks off while their foresters walked to the chunk, and timed out.
        d.foothold().flatMap(f -> f.standards().stream().filter(s -> "TREE_FARM".equals(s.task())).flatMap(s -> s.at().stream()).findFirst())
                .ifPresent(bare -> out.replaceAll(o -> o.task() instanceof Objective.Task.TreeFarm
                        && Math.hypot(o.location().x() - bare.x(), o.location().z() - bare.z()) > GROVE_NEAR
                        ? new Objective(o.mission(), bare, o.intent(), o.task()) : o));
        return out;
    }

    /** How near a TREE_FARM's point is left where it is: a tree chunk's breadth, so a squad isn't re-sent each cell planted. */
    static final int GROVE_NEAR = 24;

    /** The work that makes good {@code shortfall} for a squad released from the flatten, if there's somewhere to do it. */
    static Optional<Objective> toShortfall(Day d, PlatoonSergeant.Shortfall shortfall, SquadStatus squad, List<ResourceSite> woods, int wanted) {
        return DayWorkRules.toShortfall(shortfall, squad, woods, "Released from the flatten (" + buildersWanted(wanted) + "): the force is short of ",
                d.foothold(), d.keepMine(), d.flatten());
    }
}
