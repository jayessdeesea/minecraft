package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Position;
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

    /** Keeps {@code soldier}'s ground round {@code at}, unless it was kept within {@link #EVERY_MILLIS}. */
    void record(States world, String soldier, Position at, long now) {
        Long last = lastKept.get(soldier);
        if (last != null && now - last < EVERY_MILLIS) {
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
        LOG.info("[World] {}'s ground kept for the simulation: {}", soldier, file);
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
