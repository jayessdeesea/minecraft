package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ProductionLevels;
import io.github.jayessdeesea.minecraft.command.Renewable;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Resource;
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
            case FOOD -> t instanceof Objective.Task.Farm;
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
        for (int dx = -MINE_SITE_CLEAR; dx <= MINE_SITE_CLEAR; dx++) {
            for (int dz = -MINE_SITE_CLEAR; dz <= MINE_SITE_CLEAR; dz++) {
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
            case Objective.Task.Farm f -> Optional.of(PlatoonSergeant.Shortfall.FOOD);
            case Objective.Task.Gather(var r) when r == Resource.WOOD -> Optional.of(PlatoonSergeant.Shortfall.WOOD);
            default -> Optional.empty();
        };
    }

    /** Whether {@code stores} - as last seen; unseen, not - hold less than the most of the product {@code shortfall}'s work makes. */
    static boolean underItsMost(Stockpile stores, PlatoonSergeant.Shortfall shortfall) {
        return stores != null && java.util.Arrays.stream(Renewable.values()).filter(k -> k.product() && k.shortfall() == shortfall)
                .anyMatch(k -> stores.count(k) < ProductionLevels.STANDING.of(k).most());
    }
}
