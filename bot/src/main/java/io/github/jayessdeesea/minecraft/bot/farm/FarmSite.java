package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.List;

/**
 * A chunk farm as a site built to its template (backlog/renewables.md, Chunk farms as sites; operator, 2026-11-20): the
 * work left between the world as it stands and the template ({@link ChunkFarm}'s layout), by stage, and the stage it's
 * at. The same count costs a site on land - its ground flattened, its holes dug and poured - and one over shallow water
 * - its ground built up round water already in its hole cells, which then wants no bucket. Pure over a {@link WorldView}.
 */
public final class FarmSite {

    /** A site's stages, each standing on the last's: its ground, its water, its crop and lights, its fence. */
    public enum Stage {
        /** Its ground not yet at one level: cells to dig or fill, a hole with no floor. */
        SURVEYED,
        /** Its ground at its level, its holes open over a floor: water wanted. */
        GROUND,
        /** Its holes full: to till, sow and light. */
        WATERED,
        /** Tilled, sown and lit: feeding the force. */
        PRODUCING,
        /** Fenced and gated as well. */
        FENCED
    }

    /**
     * The work left to the template, each a move: blocks to dig over its ground or in its holes; cells to fill - its
     * ground missing, water included - and holes to floor; holes to pour; ground to till and sow; torches, posts and
     * gates to put up.
     */
    public record Work(int dig, int fill, int floors, int pours, int till, int sow, int torches, int posts, int gates) {

        /** Every move left. */
        public int moves() {
            return dig + fill + floors + pours + till + sow + torches + posts + gates;
        }

        /** The stage these leave it at. */
        public Stage stage() {
            return dig + fill + floors > 0 ? Stage.SURVEYED : pours > 0 ? Stage.GROUND
                    : till + sow + torches > 0 ? Stage.WATERED : posts + gates > 0 ? Stage.PRODUCING : Stage.FENCED;
        }

        /** What it wants to Producing: the blocks its fills and floors take, the seeds it sows, the torches it puts up. */
        public Supply toProducing() {
            return new Supply(fill + floors, sow, torches, pours > 0);
        }
    }

    /** The supply a site wants: blocks (cobblestone or dirt), seeds, torches, and a bucket - iron - for holes to pour. */
    public record Supply(int blocks, int seeds, int torches, boolean bucket) {
    }

    private FarmSite() {
    }

    /**
     * The blocks {@link #work} counts to dig, the cells of its ground it counts to fill, and its footings to pave in
     * cobblestone, on {@code farm}: what its SURVEYED stage
     * waits on, block by block - live (field577-581) the camp's chunk farm stood at dig=103, fill=32 run after run, and
     * the count alone said nothing of what or where.
     */
    public static List<List<BlockPos>> surveyed(WorldView world, ChunkFarm farm) {
        List<BlockPos> dig = new java.util.ArrayList<>(), fill = new java.util.ArrayList<>();
        for (BlockPos c : farm.ground()) {
            if (!world.isSolid(c.x(), c.y(), c.z()) || world.isWater(c.x(), c.y(), c.z())) {
                fill.add(c);
            }
            for (int up = 1; up <= 2; up++) { // from the third up it's the farm's sky (ChunkFarm.obstructions)
                if (world.isSolid(c.x(), farm.level() + up, c.z())) {
                    dig.add(new BlockPos(c.x(), farm.level() + up, c.z()));
                }
            }
        }
        for (BlockPos h : farm.holes()) {
            if (!world.isWaterSource(h.x(), h.y(), h.z()) && world.isSolid(h.x(), h.y(), h.z())) {
                dig.add(h);
            }
        }
        // Its fixtures' footings aren't ground to fill: they're paved, the third of these; what stands in a fixture's place is dug.
        dig.addAll(farm.obstructions(world));
        return List.of(List.copyOf(dig), List.copyOf(fill), farm.footingsToPave(world));
    }

    /** The work left on {@code farm} as {@code world} has it. */
    public static Work work(WorldView world, ChunkFarm farm) {
        int dig = 0, fill = 0, floors = 0, pours = 0, till = 0, sow = 0, torches = 0, posts = 0, gates = 0;
        int level = farm.level();
        for (BlockPos c : farm.ground()) {
            // Its ground: the cell at its level solid ground, not water; open over it for the crop.
            if (!world.isSolid(c.x(), c.y(), c.z()) || world.isWater(c.x(), c.y(), c.z())) {
                fill++;
            } else if (!world.isFarmland(c.x(), c.y(), c.z())) {
                till++;
            }
            for (int up = 1; up <= 2; up++) {
                if (world.isSolid(c.x(), level + up, c.z())) {
                    dig++;
                }
            }
            if (world.wheatAge(c.x(), level + 1, c.z()).isEmpty()) {
                sow++;
            }
        }
        fill += farm.footingsToPave(world).size(); // what its fence, torches and chest stand on: cobblestone
        dig += farm.obstructions(world).size();
        for (BlockPos h : farm.holes()) {
            if (world.isWaterSource(h.x(), h.y(), h.z())) {
                continue; // water already there: over a pond, no bucket wanted
            }
            if (world.isSolid(h.x(), h.y(), h.z())) {
                dig++;
            }
            if (!world.isSolid(h.x(), h.y() - 1, h.z())) {
                floors++;
            }
            pours++;
        }
        // A torch on a post's top is the fence's work, not the field's: it goes up with the post it stands on.
        java.util.Set<BlockPos> postTops = new java.util.HashSet<>();
        farm.fence().forEach(p -> postTops.add(p.offset(0, 1, 0)));
        for (BlockPos t : farm.torches()) {
            if (!world.isTorch(t.x(), t.y(), t.z())) {
                if (postTops.contains(t)) {
                    posts++;
                } else {
                    torches++;
                }
            }
        }
        for (BlockPos p : farm.fence()) {
            if (!world.isSolid(p.x(), p.y(), p.z())) {
                posts++;
            }
        }
        for (BlockPos g : farm.gates()) {
            if (!world.isGate(g.x(), g.y(), g.z())) {
                gates++;
            }
        }
        return new Work(dig, fill, floors, pours, till, sow, torches, posts, gates);
    }

    /** The stage {@code farm} is at, as {@code world} has it. */
    public static Stage stage(WorldView world, ChunkFarm farm) {
        return work(world, farm).stage();
    }

    /** The holes still to pour, for whoever carries the bucket. */
    public static List<BlockPos> dryHoles(WorldView world, ChunkFarm farm) {
        return farm.holes().stream().filter(h -> !world.isWaterSource(h.x(), h.y(), h.z())).toList();
    }
}
