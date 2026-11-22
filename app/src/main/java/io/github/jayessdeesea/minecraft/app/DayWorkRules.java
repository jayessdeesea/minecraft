package io.github.jayessdeesea.minecraft.app;

import org.springframework.ai.chat.client.ChatClient;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiSquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import java.util.Locale;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.ProductionLevels;
import io.github.jayessdeesea.minecraft.command.Renewable;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.Foothold;
import java.util.List;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.app.ChainOfCommand.*;

/**
 * The pure rules the platoon leader's day's work is guarded by (ChainOfCommand.guarded): which tasks are
 * day work, what covers a shortfall, which squad is spare for one, and whether a site cuts into the
 * flatten - no state.
 */
final class DayWorkRules {

    private DayWorkRules() {
    }

    /** Whether {@code task} is the day's work the platoon leader assigns: farm, grove, wood, mine, flatten. */
    static boolean dayWork(Objective.Task task) {
        return task instanceof Objective.Task.Farm || task instanceof Objective.Task.TreeFarm || task instanceof Objective.Task.Mine
                || task instanceof Objective.Task.Keep || task instanceof Objective.Task.Gather g && g.resource() == Resource.WOOD;
    }

    /** Whether {@code objective} answers {@code shortfall}. */
    static boolean covers(Objective objective, PlatoonSergeant.Shortfall shortfall) {
        Objective.Task t = objective.task();
        return switch (shortfall) {
            case FOOD -> t instanceof Objective.Task.Farm || t instanceof Objective.Task.Forage; // forage is food today
            // Only felling: a grove planted is wood in a day or two, not logs today - live (field449-450), the
            // stockpile at 0 logs all day, its WOOD shortfall read as covered by Charlie's TREE_FARM.
            case WOOD -> t instanceof Objective.Task.Gather g && g.resource() == Resource.WOOD;
            case IRON -> t instanceof Objective.Task.Mine;
        };
    }

    /**
     * A squad that can be moved without leaving anything bare: one on a shortfall another squad covers too,
     * one on the flatten while another is, or one on neither; the last such, so the first keep theirs.
     */
    static Optional<Integer> spare(List<Objective> out, List<Integer> movable, List<PlatoonSergeant.Shortfall> alarms) {
        long flattening = out.stream().filter(o -> o.task() instanceof Objective.Task.Keep).count();
        for (int j = movable.size() - 1; j >= 0; j--) {
            int i = movable.get(j);
            Objective o = out.get(i);
            boolean keep = o.task() instanceof Objective.Task.Keep;
            Optional<PlatoonSergeant.Shortfall> mine = alarms.stream().filter(s -> covers(o, s)).findFirst();
            boolean doubled = mine.isPresent() && out.stream().filter(x -> covers(x, mine.get())).count() > 1;
            if (keep ? flattening > 1 : mine.isEmpty() || doubled) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /** Whether a mine headed at ({@code x}, {@code z}) would cut into {@code site} or its bench. */
    static boolean cutsInto(Flatten site, int x, int z) {
        for (int dx = -MineSites.MINE_SITE_CLEAR; dx <= MineSites.MINE_SITE_CLEAR; dx++) {
            for (int dz = -MineSites.MINE_SITE_CLEAR; dz <= MineSites.MINE_SITE_CLEAR; dz++) {
                if (site.benchedContains(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The shortfall {@code objective}'s work makes good, if it's production: the farm's food, the wood's logs. */
    static Optional<PlatoonSergeant.Shortfall> producing(Objective objective) {
        return switch (objective.task()) {
            // Forage too: a platoon leader's FORAGE isn't put back on the farm as "kept on its food work".
            case Objective.Task.Farm f -> Optional.of(PlatoonSergeant.Shortfall.FOOD);
            case Objective.Task.Forage f -> Optional.of(PlatoonSergeant.Shortfall.FOOD);
            case Objective.Task.Gather(var r) when r == Resource.WOOD -> Optional.of(PlatoonSergeant.Shortfall.WOOD);
            default -> Optional.empty();
        };
    }

    /**
     * Whether {@code squad}'s task is done - a squad producing is kept on it to the stock's most, but not once it's done:
     * live (field478), Charlie held a wood every strip of which was cut out.
     */
    static boolean done(SquadStatus squad) {
        return squad.progress().filter(p -> p.state() == io.github.jayessdeesea.minecraft.command.Progress.State.DONE).isPresent();
    }

    /** Whether {@code stores} - as last seen; unseen, not - hold less than the most of the product {@code shortfall}'s work makes. */
    static boolean underItsMost(Stockpile stores, PlatoonSergeant.Shortfall shortfall) {
        return stores != null && java.util.Arrays.stream(Renewable.values()).filter(k -> k.product() && k.shortfall() == shortfall)
                .anyMatch(k -> stores.count(k) < ProductionLevels.STANDING.of(k).most());
    }

    /** The builders the ground's level wants, said: "not planned yet" before its foreman's first plan. */
    static String buildersWanted(int wanted) {
        return wanted == Integer.MAX_VALUE ? "its level not planned yet" : "its level wants " + wanted + " builder(s)";
    }

    /**
     * Whether {@code now} answers a CANTCO raised under {@code held}: another kind of order, or a
     * place more than {@link #ANSWER_MOVE} away - not the cycle-to-cycle shuffle of a formation.
     */
    static boolean answers(Order held, Order now) {
        if (held instanceof Order.MoveTo a && now instanceof Order.MoveTo b) {
            return Math.hypot(a.x() - b.x(), a.z() - b.z()) > ANSWER_MOVE || Math.abs(a.y() - b.y()) > ANSWER_MOVE;
        }
        return held.getClass() != now.getClass();
    }

    /** The objective's task, as the OPORD says it - nothing when there's none. */
    static String task(Objective objective) {
        return (objective.patrolBase() ? ", patrol base"
                : objective.gather().map(r -> ", gather " + r.name().toLowerCase(Locale.ROOT)).orElse(""))
                + objective.area().map(a -> ", " + a.line()).orElse("");
    }

    /**
     * The squads as the platoon leader is shown them: at dusk or dawn with no standing order to keep - the
     * night's or the day's work lapses with it, and each squad is given the next. Live (field423-425), at
     * every dawn the platoon leader kept 'Cut the keep's mine down to the iron through the night' for every
     * squad, and the guardrails made the day's work.
     */
    static List<SquadStatus> forThePlatoonLeader(List<SquadStatus> statuses, boolean edge) {
        return edge ? statuses.stream().map(s -> new SquadStatus(s.squadName(), s.report(), s.soldiers(), s.resources(), Optional.empty(),
                s.holdingBase(), s.baseBlocksLeft(), s.progress(), s.classOne(), s.classFour())).toList() : statuses;
    }

    /**
     * A radio line as a chat command: {@code /me} (any player may use it, and it reads as the radio
     * operator speaking), stripped of characters the server kicks for and cut to chat length.
     * Model-written intents can contain anything.
     */
    static String chatLine(String line) {
        StringBuilder clean = new StringBuilder("me ");
        for (int i = 0; i < line.length() && clean.length() < CHAT_LINE_LIMIT; i++) {
            char c = line.charAt(i);
            clean.append(c == '§' || c < ' ' || c == 127 ? ' ' : c);
        }
        return clean.toString();
    }

    static String at(Position p) {
        return "(" + Math.round(p.x()) + ", " + Math.round(p.y()) + ", " + Math.round(p.z()) + ")";
    }

    /** What each squad leader is made as, by {@code decidesWith}: "drills" or "model". */
    static java.util.function.Function<String, SquadLeader> squadLeaders(String decidesWith, ChatClient client, SquadLeaderPrompts prompts) {
        return switch (decidesWith) {
            case "drills" -> DrillSquadLeader::new;
            case "model" -> name -> new SpringAiSquadLeader(name, client, prompts);
            default -> throw new IllegalArgumentException("minecraft.squad-leader.decides-with is \"" + decidesWith
                    + "\": \"drills\" or \"model\"");
        };
    }

    /**
     * Where a squad goes for {@code shortfall}, {@code why} it's sent: food to the camp's farm, iron to the keep's mine,
     * wood to the nearest of {@code woods} to it - the squad's first soldier, or the flatten's middle.
     */
    static Optional<Objective> toShortfall(PlatoonSergeant.Shortfall shortfall, SquadStatus squad, List<ResourceSite> woods, String why,
                                           Optional<Foothold> base, Position keepMine, Flatten flatten) {
        return switch (shortfall) {
            case FOOD -> base.map(b -> new Objective("Work the camp's farm", b.camp(), why + "food", new Objective.Task.Farm()));
            case IRON -> Optional.of(new Objective("Mine the keep's mine for iron", keepMine, why + "iron", new Objective.Task.Mine()));
            case WOOD -> {
                Position from = squad.soldiers().isEmpty() ? flatten.centre() : squad.soldiers().getFirst().position();
                yield woods.stream().filter(w -> w.resource() == Resource.WOOD)
                        .min(java.util.Comparator.comparingDouble(w -> Math.hypot(w.location().x() - from.x(), w.location().z() - from.z())))
                        .map(w -> new Objective("Gather wood", w.location(), why + "logs", new Objective.Task.Gather(Resource.WOOD)));
            }
        };
    }
}
