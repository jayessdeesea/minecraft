package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Stairway;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.jayessdeesea.minecraft.app.SoldierRunner.UNDERGROUND;

/**
 * The engineer's ways down for the recovery drill (command.Recovery; docs/military-doctrine.md § 7): a stair down built
 * from a ledge, and a stranded soldier's own pillar or a ledge's edge dug down from on top - each the cells it works,
 * laid out from the world ({@code bot.engineer.Stairway}) for the soldier the drill has stranded.
 */
final class Engineer {

    private static final Logger LOG = LogManager.getLogger(Engineer.class);

    private Engineer() {
    }

    /** The engineer's stair down for a stranded soldier, from where it stands. */
    static Function<SoldierStatus, Optional<List<Cell>>> stairDown(WorldView world, String squadName) {
        return stuck -> {
            String n = stuck.name();
            Position at = stuck.position();
            // Under the ground, a stair down leads only further into it: none, and the stair up is laid
            // instead. Live (field341), Jack in a cave at y=-51 had a stair down to its floor laid out,
            // one block he didn't carry, and stood with nobody to fetch it.
            Optional<BlockPos> sky = TerrainSurveyor.surface(world, (int) Math.floor(at.x()), (int) Math.floor(at.z()), Keep.SKY_TOP);
            if (sky.isPresent() && sky.get().y() > at.y() + UNDERGROUND) {
                LOG.warn("[{}] RECOVERY: {} is stranded at {}, {} under the ground at y={} - no stair down, a stair up instead",
                        squadName, n, at, sky.get().y() - (int) Math.floor(at.y()), sky.get().y());
                return Optional.empty();
            }
            Optional<List<Cell>> stair = Stairway.down(world, BlockPos.containing(at.x(), at.y(), at.z()))
                    .map(cells -> cells.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList());
            if (stair.isPresent()) {
                LOG.info("[{}] RECOVERY: {} is stranded at {} - a stair down laid out, {} blocks", squadName, n, at, stair.get().size());
            } else {
                LOG.warn("[{}] RECOVERY: {} is stranded at {} and no stair down can be laid from there", squadName, n, at);
            }
            return stair;
        };
    }

    /** A stranded soldier's own pillar under it, top first, for it to dig down from on top. */
    static Function<SoldierStatus, Optional<List<Cell>>> pillarDown(WorldView world, Predicate<Position> buried, String squadName) {
        return stuck -> {
            Position at = stuck.position();
            // Or on the surface at a ledge's edge, sent lower, the ground below seen down its side: dug down to (Stairway.edgeShaft).
            // Not sent along its own level: a gap in its way is bridged, not dug down into - in the squad simulation a
            // miner with no route across a hole in its gallery's floor cut a shaft down beside it.
            boolean down = !buried.test(at) && stuck.order() instanceof Order.MoveTo m && m.y() < at.y() - 1;
            Optional<List<Cell>> pillar = Stairway.pillarUnder(world, BlockPos.containing(at.x(), at.y(), at.z())).or(() -> down ? Stairway.edgeShaftNear(world, BlockPos.containing(at.x(), at.y(), at.z())) : Optional.empty())
                    .map(cells -> cells.stream().map(c -> new Cell(c.x(), c.y(), c.z())).toList());
            pillar.ifPresent(p -> LOG.info("[{}] RECOVERY: {} is stranded up at {} - digs down from {}, {} blocks", squadName, stuck.name(), at, p.getFirst(), p.size()));
            return pillar;
        };
    }
}
