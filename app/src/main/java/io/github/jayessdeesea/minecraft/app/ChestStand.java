package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/** Where a soldier stands to reach a chest: a pure function of the world, the soldier, the chest and the other soldiers. */
final class ChestStand {

    private ChestStand() {
    }

    /**
     * Where a soldier at {@code from} stands to reach the chest at {@code chest}: the nearest cell
     * within reach of it that the soldier can stand in and get to - on foot, on its blocks or
     * cutting steps. Not the chest's own cell, which nobody stands in: live, a chest walled on
     * three sides left every trip to it short of reach, and the day's loads in the soldiers' packs.
     */
    static Optional<Position> at(WorldView world, Position from, Position chest, List<Position> soldiers) {
        BlockPos feet = BlockPos.containing(from.x(), from.y(), from.z());
        int cx = (int) Math.floor(chest.x()), cy = (int) Math.floor(chest.y()), cz = (int) Math.floor(chest.z());
        int span = Math.max(Math.abs(cx - feet.x()), Math.abs(cz - feet.z())) + CHEST_STAND_ACROSS + 2;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, CHEST_STAND_BUDGET, 0, false).keySet();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -CHEST_STAND_ACROSS; dx <= CHEST_STAND_ACROSS; dx++) {
            for (int dz = -CHEST_STAND_ACROSS; dz <= CHEST_STAND_ACROSS; dz++) {
                // No lower than a step under the chest: a stand two down is a pit reached by a drop with no
                // walk out of it. Live, three soldiers of three squads were sent into one by the camp's
                // chest, taken for shut in, and cut their way out sideways under the camp.
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = new BlockPos(cx + dx, cy + dy, cz + dz);
                    Position centre = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    // Not on a chest: one is less than a block high, so a stand on it is never quite
                    // reached, and a body on it shuts its lid. Live, the farm squad's soldier was sent
                    // onto the chest beside the camp's stockpile, failed five walks to it in a row, and stood with its load.
                    // In reach wherever in the stand the walk stops - 0.3 of its centre - not only from its
                    // centre: live, a walk ended 3.5 blocks off, a hair past reach, and the load wasn't put in.
                    boolean reachedFromAll = Depositor.withinReach(new Position(centre.x() - 0.3, centre.y(), centre.z() - 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() + 0.3, centre.y(), centre.z() - 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() - 0.3, centre.y(), centre.z() + 0.3), chest)
                            && Depositor.withinReach(new Position(centre.x() + 0.3, centre.y(), centre.z() + 0.3), chest);
                    if (!reachable.contains(cell) || !reachedFromAll || world.isChest(cell.x(), cell.y() - 1, cell.z())) {
                        continue;
                    }
                    double distance = Math.hypot(centre.x() - from.x(), centre.z() - from.z()) + Math.abs(centre.y() - from.y()) + (SoldierErrands.standTaken(centre, from, soldiers) ? 1_000 : 0); // another soldier's stand is the last chosen, not ruled out: with every stand taken the walk went to the chest's own cell, and seven stood round one chest, each keeping the others' stands taken (field704)
                    if (distance < bestDistance) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best).map(c -> new Position(c.x() + 0.5, c.y(), c.z() + 0.5));
    }


    /** How far across from a chest a stand to reach it is looked for, and how far the flood for it goes. */
    static final int CHEST_STAND_ACROSS = 3, CHEST_STAND_BUDGET = 20_000;

    /**
     * Where a depositor stands for its chest: its dumping stand as it is, else where {@code search} finds a stand
     * from {@code here}, else {@code here} itself when in reach of the chest, else {@code noStandFor}'s answer. A
     * soldier with no position ({@code here} empty) goes to {@code noStandFor}.
     */
    static Optional<Position> depositorStand(Depositor own, Optional<Position> here,
                                             BiFunction<Position, Position, Optional<Position>> search, UnaryOperator<Position> noStandFor) {
        return own.dumping() ? own.standAt() : own.standAt().map(chest -> here.flatMap(h -> search.apply(h, chest))
                .orElseGet(() -> here.filter(h -> Depositor.withinReach(h, chest)).orElseGet(() -> noStandFor.apply(chest))));
    }
}
