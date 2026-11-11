package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.List;
import java.util.Locale;

/**
 * How hard a walk is from one site to another, against the walk over level ground: the steps a
 * route on foot takes beside the fewest it could (a straight line, diagonals allowed), and the
 * jumps up and drops down on the way. A camp whose sites a soldier walks between level and nearly
 * straight is laid out well; jumps, drops and detours are what an awkward one costs every trip.
 */
public record RouteEffort(String to, boolean reached, int ideal, int walked, int jumps, int drops) {

    /** How far a route is looked for. */
    static final int BUDGET = 60_000;

    /** The walk from {@code from} to {@code target}, named {@code to}: on foot, no blocks placed, no fall that hurts. */
    public static RouteEffort of(String to, WorldView world, BlockPos from, BlockPos target) {
        int ideal = Math.max(Math.abs(target.x() - from.x()), Math.abs(target.z() - from.z()));
        Pathfinder.Result route = Pathfinder.find(world, from, new Goal(target.x() + 0.5, target.y(), target.z() + 0.5, 1.5, 1),
                BUDGET);
        if (!route.reachesGoal()) {
            return new RouteEffort(to, false, ideal, 0, 0, 0);
        }
        List<BlockPos> path = route.path();
        // Against the straight line to where the walk ends: the goal is reached within a step or so of it.
        BlockPos end = path.isEmpty() ? from : path.getLast();
        ideal = Math.max(Math.abs(end.x() - from.x()), Math.abs(end.z() - from.z()));
        int jumps = 0;
        int drops = 0;
        BlockPos last = from;
        for (BlockPos step : path) {
            if (step.y() > last.y()) {
                jumps += step.y() - last.y();
            } else if (step.y() < last.y()) {
                drops += last.y() - step.y();
            }
            last = step;
        }
        return new RouteEffort(to, true, ideal, path.size(), jumps, drops);
    }

    /** The share of the walk that's the straight line, in percent: 100 for level, straight ground. */
    public int percent() {
        return walked == 0 ? 100 : Math.min(100, 100 * ideal / walked);
    }

    /** In words, for the log and the review. */
    public String line() {
        return reached
                ? String.format(Locale.ROOT, "to the %s: %d%% (%d steps for %d), %d up, %d down", to, percent(), walked, ideal, jumps, drops)
                : "to the " + to + ": no route on foot";
    }
}
