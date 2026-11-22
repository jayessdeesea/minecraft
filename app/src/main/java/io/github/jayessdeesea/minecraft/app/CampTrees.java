package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The camp's tree chunk, sited close (operator, 2026-11-21: in the airhead and the early phases the renewables are all
 * close by - flat the requirement, sea level the preference, within 64 blocks): one chunk round the camp's, beside a wheat
 * chunk, its 16 trunks the camp's grove - the wood standard's, and a TREE_FARM squad's - in place of a grove wherever a
 * depot's rings found ground: live (field630), foresters walked 200 to 330 blocks to the keep's, at (-34, 68, -113).
 * Sited once, again if its chunk is taken for wheat; a minute between looks while there's none.
 */
final class CampTrees {

    private static final Logger LOG = LogManager.getLogger(CampTrees.class);

    /** How many chunks round the camp's the tree chunk is looked for: 64 blocks. */
    static final int RINGS = 4;
    /** Between looks while none qualifies. */
    static final long RESITE_MILLIS = 60_000;

    private volatile TreeChunk chunk;
    private volatile long lookedAt = Long.MIN_VALUE;

    /** The camp's tree chunk as a grove, sited from {@code camp} beside {@code farm}'s wheat chunks at {@code now}: none if none qualifies. */
    synchronized Optional<TreeFarm> grove(WorldView world, BlockPos camp, FarmPlan farm, long now) {
        Set<Long> wheat = farm == null ? Set.of() : farm.chunks().stream().map(c -> ChunkFarm.key(c.chunkX(), c.chunkZ()))
                .collect(Collectors.toSet());
        if (chunk != null && wheat.contains(ChunkFarm.key(chunk.chunkX(), chunk.chunkZ()))) {
            chunk = null; // taken for wheat: sited again
        }
        if (chunk == null && (lookedAt == Long.MIN_VALUE || now - lookedAt >= RESITE_MILLIS)) {
            lookedAt = now;
            chunk = TreeChunk.site(world, camp, RINGS, c -> true, wheat, wheat).orElse(null);
            if (chunk != null) {
                LOG.info("[Trees] the camp's tree chunk at chunk ({}, {}), its ground at y={}, {} blocks from the camp - {}",
                        chunk.chunkX(), chunk.chunkZ(), chunk.level(), Math.round(Math.hypot(chunk.chunkX() * 16 + 8 - camp.x(),
                                chunk.chunkZ() * 16 + 8 - camp.z())), chunk.stage(world));
            }
        }
        return farm(world);
    }

    /**
     * The tree chunk sited, as the grove a squad growing trees plants - wherever its order points: live (field645), Charlie
     * was sent onto the camp's hill, the ground being flattened, while the camp's tree chunk stood bare.
     */
    Optional<TreeFarm> farm(WorldView world) {
        TreeChunk c = chunk;
        List<BlockPos> plot = c == null ? List.of() : c.plantable(world); // its trunks that take a sapling: none walked at buried
        return plot.isEmpty() ? Optional.empty() : Optional.of(new TreeFarm(c.trunks().getFirst(), plot, c.torches(), Optional.of(c.chest())));
    }

    /** The chunk sited, if any. */
    Optional<TreeChunk> chunk() {
        return Optional.ofNullable(chunk);
    }
}
