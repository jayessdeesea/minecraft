package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * What a mission or a squad keeps from the work beside it, built for each call from its arguments: the camp's
 * structures, the ground no ground work touches, what a keep's dig leaves, the columns of other missions' templates.
 * It holds nothing, so two missions' keeps never share an answer and each is asked as the world stands now.
 */
final class KeptGround {

    private KeptGround() {
    }

    /**
     * A hut going up.
     *
     * @param stand where its builder stands: the floor its ground is judged by
     * @param cells the blocks it's built of
     */
    record Hut(Position stand, Collection<BlockPos> cells) {
    }

    /**
     * A camp's structures, as they stand.
     *
     * @param walls        each wall line's cells
     * @param camp         the camp's centre, on its level; empty with no camp
     * @param radius       how far out the camp's ground work reaches
     * @param goingUp      the huts going up
     * @param built        the huts built
     * @param shops        the squads' workshops
     * @param depot        the camp's own workshop, whose row of chests is the depot
     * @param chestClaims  the cells a chest row leaves ({@link Workshop#chestCells})
     * @param depotChests  how many chests the depot's row runs to
     * @param fullChests   the chests found full
     * @param released     whether a full chest is given up to a dig: on ground to flatten, and seen empty
     */
    record Camp(Collection<? extends Collection<BlockPos>> walls, Optional<Position> camp, int radius, Collection<Hut> goingUp,
                Collection<PatrolBase.Plan> built, Collection<Workshop> shops, Optional<Workshop> depot, Set<BlockPos> chestClaims, int depotChests,
                Collection<BlockPos> fullChests, Predicate<BlockPos> released) {
    }

    /** The cells of a camp's structures: its walls, its huts, its workshops and its chests, with the cell under each fixture. */
    static Set<BlockPos> camp(WorldView world, Camp c) {
        Set<BlockPos> keep = new HashSet<>();
        // The wall's own blocks: ground work levelling round the camp would dig them out as the masons put them back.
        c.walls().forEach(keep::addAll);
        // A hut going up is kept as well as one built: cobblestone over the floor is dug by day. Only one on the camp's
        // level, or none of its ground's: one on ground still to come down is the night's, dug with it (CampGround.hutKept).
        c.goingUp().stream().filter(h -> CampGround.hutKept(h.stand(), c.camp(), c.radius())).forEach(h -> keep.addAll(h.cells()));
        for (PatrolBase.Plan hut : c.built()) {
            if (CampGround.hutKept(hut.floor().getFirst(), c.camp(), c.radius())) {
                hut.cells().forEach(p -> keep.add(BlockPos.containing(p.x(), p.y(), p.z())));
                hut.floor().forEach(p -> keep.add(BlockPos.containing(p.x(), p.y() - 1, p.z())));
            }
        }
        // Each workshop's stand, table, furnace and chest, each chest found full that stands with its stores
        // (SiteGeometry.fullKept), and every chest standing in the depot's row: no ground work digs stores out.
        Stream<BlockPos> fixtures = Stream.concat(c.shops().stream(), c.depot().stream())
                .flatMap(w -> Stream.concat(Stream.of(w.stand(), w.table(), w.furnace()), w.chest().stream()));
        Stream<BlockPos> full = SiteGeometry.fullKept(c.fullChests(), b -> world.isChest(b.x(), b.y(), b.z()), c.released()).stream();
        Stream<BlockPos> row = c.depot().stream().flatMap(w -> w.chestCells(world, c.chestClaims(), c.depotChests()).stream())
                .filter(b -> world.isChest(b.x(), b.y(), b.z()));
        Stream.of(fixtures, full, row).flatMap(s -> s).forEach(b -> {
            keep.add(b);
            keep.add(b.offset(0, -1, 0));
        });
        return keep;
    }

    /**
     * What no ground work digs or fills: the camp's kept structures ({@link #camp}), its mine's cells, and every squad's
     * stairs - walkway and landing. The set is the caller's own, to add its mission's cells to.
     */
    static Set<Cell> ground(Collection<BlockPos> campKeep, Collection<BlockPos> campMine, Collection<Staircase> stairs) {
        Set<Cell> except = new HashSet<>();
        Stream.concat(Stream.concat(campKeep.stream(), campMine.stream()),
                        stairs.stream().flatMap(s -> Stream.concat(s.walkway().stream(), s.landing().stream())))
                .forEach(c -> except.add(new Cell(c.x(), c.y(), c.z())));
        return except;
    }

    /**
     * What a keep's dig of {@code ground} leaves: {@code kept}, what its ground work leaves; its slab's own rock, or a
     * soil site's soil; and a pillar under each chest of {@code standing}. The set is the caller's own, to add the ore
     * it sets aside to.
     *
     * @param standing the chests on the ground to flatten not yet seen empty: dug, a chest spills what it holds, so each
     *                 stands on the ground under it and round it, where its salvager can still reach it
     */
    static Set<Cell> keep(WorldView world, Flatten ground, Set<Cell> kept, Collection<BlockPos> standing) {
        Set<Cell> except = new HashSet<>(kept);
        Box slab = ground.slab();
        for (int x = slab.minX(); x <= slab.maxX(); x++) {
            for (int z = slab.minZ(); z <= slab.maxZ(); z++) {
                for (int y = slab.minY(); y <= slab.maxY(); y++) {
                    // The slab's own cobblestone, and rock, which is as good: dug, a block laid would be the dig's again,
                    // dug and laid in turn. A soil site's top keeps its soil instead, and its rock is dug for dirt to go in.
                    if (ground.soil() ? world.isSoil(x, y, z) : world.isStone(x, y, z) || world.isCobblestone(x, y, z)) {
                        except.add(new Cell(x, y, z));
                    }
                }
            }
        }
        for (BlockPos c : standing) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = ground.bottom(); y <= c.y(); y++) {
                        except.add(new Cell(c.x() + dx, y, c.z() + dz));
                    }
                }
            }
        }
        return except;
    }

    /**
     * The columns a template's ground stands on, but {@code own}'s chunk's: each of {@code templates}' boxes, fence lines
     * and all, never dug or paved by another mission.
     *
     * @param templates the template chunks, by {@link ChunkFarm#key}
     */
    static BiPredicate<Integer, Integer> columns(Set<Long> templates, FarmMission own) {
        long ownKey = ChunkFarm.key(own.chunkX(), own.chunkZ());
        return (x, z) -> {
            // A template's box is its chunk and the fence lines past it: x0..x0+16, so a column on a line is its west neighbour's too.
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int cx = Math.floorDiv(x, 16) - (dx == 1 && Math.floorMod(x, 16) == 0 ? 1 : 0);
                    int cz = Math.floorDiv(z, 16) - (dz == 1 && Math.floorMod(z, 16) == 0 ? 1 : 0);
                    long key = ChunkFarm.key(cx, cz);
                    if (key != ownKey && templates.contains(key)) {
                        return true;
                    }
                }
            }
            return false;
        };
    }
}
