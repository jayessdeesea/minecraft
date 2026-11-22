package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.FarmSite;
import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The platoon leader's chunk register kept as the world shows it (operator, 2026-11-21: the platoon leader tracks what is
 * in each chunk and how mature it is): each of the camp's wheat chunks and its tree chunk, its kind, level and stage as
 * measured - kept when it changes, and said. Its {@link Registers} opened on first use, from the file it's pointed at.
 */
final class ChunkLedger {

    private static final Logger LOG = LogManager.getLogger(ChunkLedger.class);

    private volatile Path file;
    private Registers registers;

    /** The ledger kept in {@code file}, opened on first use. */
    ChunkLedger(Path file) {
        this.file = file;
    }

    /** Points it at {@code file} instead - before its first use: the garrison's own, outside a cloud-sync folder. */
    void keptIn(Path file) {
        if (registers != null) {
            throw new IllegalStateException("the chunk register is open at " + this.file + " already");
        }
        this.file = file;
    }

    /** The registers, opened on first use. */
    synchronized Registers registers() {
        if (registers == null) {
            registers = new Registers(file);
            LOG.info("[Chunks] the platoon leader's registers kept in {}", file);
        }
        return registers;
    }

    /**
     * Measures the camp's {@code farm}'s wheat chunks and its {@code trees} chunk in {@code world} at {@code now}, and keeps
     * each whose kind, level or stage has changed since it was kept.
     */
    synchronized void measure(WorldView world, FarmPlan farm, Optional<TreeChunk> trees, long now) {
        List<ChunkRecord> seen = new java.util.ArrayList<>();
        if (farm != null) {
            for (ChunkFarm c : farm.chunks()) {
                seen.add(new ChunkRecord(c.chunkX(), c.chunkZ(), ChunkRecord.Kind.WHEAT, c.level(), Optional.of(FarmSite.stage(world, c).name()), now));
            }
        }
        trees.ifPresent(t -> seen.add(new ChunkRecord(t.chunkX(), t.chunkZ(), ChunkRecord.Kind.TREES, t.level(), Optional.of(t.stage(world).name()), now)));
        for (ChunkRecord c : seen) {
            Optional<ChunkRecord> kept = registers().chunk(c.chunkX(), c.chunkZ());
            if (kept.filter(k -> k.kind() == c.kind() && k.level() == c.level() && k.stage().equals(c.stage())).isEmpty()) {
                registers().put(c);
                LOG.info("[Chunks] chunk ({}, {}): {} at y={}, {}", c.chunkX(), c.chunkZ(), c.kind(), c.level(), c.stage().orElse("not measured"));
            }
        }
    }
}
