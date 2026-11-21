package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recorded worlds (backlog/simulator-fidelity.md): the ground round a soldier stuck a minute, as the client decoded it -
 * each cell's block state - kept under the state directory, so the same situation is replayed in the squad simulation
 * rather than rebuilt by hand (its states are the server's own ids, as the simulation's are). One a soldier at most
 * every {@link #EVERY_MILLIS}. Thread-safe.
 */
final class WorldRecords {

    private static final Logger LOG = LogManager.getLogger(WorldRecords.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** How far round the soldier the ground is kept: across, and up and down. */
    static final int ACROSS = 24, UP_DOWN = 16;
    /** How often at most a soldier's ground is kept. */
    static final long EVERY_MILLIS = 30 * 60_000;
    /** A cell the client hasn't had. */
    static final int UNKNOWN = -1;

    /** A box of block states: its low corner, its size, and each cell's state, x fastest, then z, then y. */
    record Snapshot(String soldier, long at, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int[] states) {

        /** The state at (x, y, z), or {@link #UNKNOWN} outside the box or where the client had none. */
        int stateAt(int x, int y, int z) {
            int i = x - minX, j = y - minY, k = z - minZ;
            return i < 0 || j < 0 || k < 0 || i >= sizeX || j >= sizeY || k >= sizeZ ? UNKNOWN : states[(j * sizeZ + k) * sizeX + i];
        }
    }

    /** What a world is read through: each cell's state, if known. */
    interface States {
        OptionalInt at(int x, int y, int z);
    }

    private final Path directory;
    private final Map<String, Long> lastKept = new ConcurrentHashMap<>();

    WorldRecords(Path directory) {
        this.directory = directory;
    }

    /** The box of {@code world} round {@code at}, for {@code soldier} at {@code now}. */
    static Snapshot take(States world, String soldier, Position at, long now) {
        int minX = (int) Math.floor(at.x()) - ACROSS, minY = (int) Math.floor(at.y()) - UP_DOWN, minZ = (int) Math.floor(at.z()) - ACROSS;
        int size = 2 * ACROSS + 1, height = 2 * UP_DOWN + 1;
        int[] states = new int[size * height * size];
        for (int j = 0; j < height; j++) {
            for (int k = 0; k < size; k++) {
                for (int i = 0; i < size; i++) {
                    states[(j * size + k) * size + i] = world.at(minX + i, minY + j, minZ + k).orElse(UNKNOWN);
                }
            }
        }
        return new Snapshot(soldier, now, minX, minY, minZ, size, height, size, states);
    }

    /**
     * Keeps {@code status}'s soldier's ground round where it stands, unless it was kept within {@link #EVERY_MILLIS} -
     * and beside it a draft of its case for the registry of stuck soldiers (StuckEvalsTest; operator, 2026-11-20: any
     * time a soldier is stuck, an eval of what correct is there): where it stood, its kit, the goal of its walk.
     */
    void record(States world, SoldierStatus status, long now) {
        record(world, status, now, false);
    }

    /**
     * As {@link #record(States, SoldierStatus, long)}, for a soldier killed: kept whatever was kept lately - a death is
     * rarer than a soldier stuck, and each one an eval. Live (field602), Ned shot dead on the surface two minutes after
     * his ground was kept stuck below, and the half hour's wait kept none of where he died.
     */
    void recordDeath(States world, SoldierStatus status, long now) {
        record(world, status, now, true);
    }

    private void record(States world, SoldierStatus status, long now, boolean death) {
        String soldier = status.name();
        Position at = status.position();
        Long last = lastKept.get(soldier);
        if (!death && last != null && now - last < EVERY_MILLIS) {
            return;
        }
        lastKept.put(soldier, now);
        Path file = directory.resolve("worlds").resolve(soldier + "-" + now + ".json");
        try {
            Files.createDirectories(file.getParent());
            JSON.writeValue(file.toFile(), take(world, soldier, at, now));
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't keep " + soldier + "'s ground at " + file, e);
        }
        Path draft = directory.resolve("worlds").resolve(soldier + "-" + now + ".case.json");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(draft.toFile(), StuckCase.draft(status, "worlds/" + file.getFileName()));
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't keep " + soldier + "'s case at " + draft, e);
        }
        LOG.info("[World] {}'s ground kept for the simulation: {}, its case drafted: {}", soldier, file, draft);
    }

    /** A snapshot kept before. */
    static Snapshot load(Path file) {
        try {
            return JSON.readValue(file.toFile(), Snapshot.class);
        } catch (IOException e) {
            throw new UncheckedIOException("couldn't read the world kept at " + file, e);
        }
    }
}
