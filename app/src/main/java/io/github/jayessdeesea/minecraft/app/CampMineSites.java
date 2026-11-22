package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import static io.github.jayessdeesea.minecraft.app.DayWorkRules.cutsInto;

/**
 * The camp's mine and the sites it has been at and left, as the keep's has ({@link MineSites}): it starts at its first
 * head ({@link ChainOfCommand#MINE_HEAD_DX}, {@link ChainOfCommand#MINE_HEAD_DZ} off the camp's centre) and, each one
 * spent - its MINE done, or dried up - moves to the nearest untried site on the camp's ground. Live (field663), the camp's
 * mine cut out with nowhere to go, every squad was sent down it each minute all night, DONE within a second, and the
 * night's mining yielded nothing. Kept on disk by the soldier runner ({@link MineSiteBook}). Thread-safe.
 */
final class CampMineSites {

    private static final Logger LOG = LogManager.getLogger(CampMineSites.class);

    /** How far out from the camp's centre a site may be: its area of influence past its wall line (CampGround#held). */
    static final int REACH = SoldierRunner.WALL_RADIUS + CampGround.INFLUENCE;

    private volatile Position site;
    private final Set<Position> tried = ConcurrentHashMap.newKeySet();

    /** The first head of the mine of a camp centred at {@code camp}: inside its wall, clear of the huts. */
    static Position first(Position camp) {
        return new Position(Math.floor(camp.x()) + ChainOfCommand.MINE_HEAD_DX, ChainOfCommand.MINE_DEPTH,
                Math.floor(camp.z()) + ChainOfCommand.MINE_HEAD_DZ);
    }

    /** The camp's mine as it stands, for a camp centred at {@code camp}: its first head until it has moved, or a site off another camp's ground. */
    Position site(Position camp) {
        Position at = site;
        return at != null && held(camp, at) ? at : first(camp);
    }

    /**
     * The heads of the mine of the camp centred at {@code camp}, x and z: its first, the site it stands at, and its
     * {@code stairs}' head as kept - no ground work in their shafts.
     */
    java.util.List<int[]> heads(Position camp, Optional<BlockPos> stairs) {
        java.util.List<int[]> heads = new java.util.ArrayList<>();
        java.util.stream.Stream.of(first(camp), site(camp)).forEach(p -> heads.add(new int[]{(int) Math.floor(p.x()), (int) Math.floor(p.z())}));
        stairs.ifPresent(h -> heads.add(new int[]{h.x(), h.z()}));
        return heads;
    }

    /** The sites the camp's mine has been at and left. */
    Set<Position> tried() {
        return Set.copyOf(tried);
    }

    /** The camp's mine as it was left and the sites it had tried, read back from disk at start-up. */
    void restore(Optional<Position> at, Collection<Position> sites) {
        tried.addAll(sites);
        at.ifPresent(p -> {
            LOG.info("[PL] the camp's mine, kept on disk: {}", p);
            site = p;
        });
    }

    /**
     * The camp's mine at {@code camp} spent - its MINE done, or dried up: it moves on to the nearest untried site
     * ({@link #next}). Returns the site it moved to, or empty with every site on the camp's ground tried - said loud,
     * and the mine left where it is.
     */
    Optional<Position> spent(Position camp, Flatten flatten, BiPredicate<Integer, Integer> templated) {
        Position was = site(camp);
        tried.add(was);
        Optional<Position> next = next(camp, was, tried, flatten, templated);
        if (next.isEmpty()) {
            LOG.warn("[PL] the camp's mine at {} is spent, and every site on the camp's ground has been tried - SHORT", DayWorkRules.at(was));
            return Optional.empty();
        }
        site = next.get();
        LOG.warn("[PL] the camp's mine at {} is spent - the night shift's mine moves to {}", DayWorkRules.at(was), DayWorkRules.at(next.get()));
        return next;
    }

    /**
     * Where the camp's mine at {@code camp} goes from {@code at}: the nearest the camp's centre of the chunks on its ground
     * ({@link #REACH} out, as {@link CampGround#held} has it) - a chunk's centre, where a well is cut (Staircase#well) -
     * none of {@code tried}, none that crosses the wall line, cuts into {@code flatten} or its bench, or holds a template
     * ({@code templated}, by chunk x and z: the chunk register's wheat, trees, flattened or floored ground, supply point).
     */
    static Optional<Position> next(Position camp, Position at, Set<Position> tried, Flatten flatten, BiPredicate<Integer, Integer> templated) {
        int cx = (int) Math.floor(camp.x()), cz = (int) Math.floor(camp.z());
        Position best = null;
        double nearest = Double.MAX_VALUE;
        for (int chunkX = Math.floorDiv(cx - REACH, 16); chunkX <= Math.floorDiv(cx + REACH, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(cz - REACH, 16); chunkZ <= Math.floorDiv(cz + REACH, 16); chunkZ++) {
                int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
                Position p = new Position(x, ChainOfCommand.MINE_DEPTH, z);
                // Its chunk wholly past the wall line: a well inside it would go down through the huts and the workshop.
                boolean inWall = chunkX * 16 + 15 >= cx - SoldierRunner.WALL_RADIUS && chunkX * 16 <= cx + SoldierRunner.WALL_RADIUS
                        && chunkZ * 16 + 15 >= cz - SoldierRunner.WALL_RADIUS && chunkZ * 16 <= cz + SoldierRunner.WALL_RADIUS;
                double d = Math.hypot(x - camp.x(), z - camp.z());
                if (d < nearest && held(camp, p) && !inWall && !p.equals(at) && !tried.contains(p) && !cutsInto(flatten, x, z)
                        && !templated.test(chunkX, chunkZ)) {
                    nearest = d;
                    best = p;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether {@code p} is on the ground of the camp centred at {@code camp}. */
    private static boolean held(Position camp, Position p) {
        return Math.max(Math.abs(p.x() - camp.x()), Math.abs(p.z() - camp.z())) <= REACH;
    }
}
