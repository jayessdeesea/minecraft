package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A camp wall's layout, kept on disk so it outlives a restart (docs/military-doctrine.md § 15). A
 * wall is laid out on the ground as it is, and once built the ground reads as the wall's top: laid
 * out again after a restart, a second wall would go up on the first. One JSON file per wall, named
 * by its centre's column. A file that won't read is a fault, not a fresh start.
 *
 * <p>The same store keeps the camp's farm, under another {@code kind}: sited again after a restart,
 * it moved to another pond and the crop growing at the first was left (§ 4).
 */
final class WallStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path directory;
    private final String kind;

    WallStore(Path directory) {
        this(directory, "wall");
    }

    /** A store of {@code kind} layouts - "wall", "farm" - one file each, named by kind and key. */
    WallStore(Path directory, String kind) {
        this.directory = directory;
        this.kind = kind;
    }

    /** The layout kept for the wall round column {@code key} ("x,z"), if one was. */
    Optional<List<BlockPos>> load(String key) {
        Path file = file(key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            List<int[]> cells = JSON.readValue(file.toFile(), new TypeReference<List<int[]>>() {
            });
            return Optional.of(cells.stream().map(c -> new BlockPos(c[0], c[1], c[2])).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("the " + kind + " layout in " + file + " won't read", e);
        }
    }

    /** The key ("x,z") of every layout of this kind kept. */
    List<String> keys() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        String prefix = kind + "_";
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
                    .map(n -> n.substring(prefix.length(), n.length() - ".json".length()))
                    // "x_z", either negative: the separator is the underscore after the first number.
                    .map(k -> k.replaceFirst("^(-?\\d+)_(-?\\d+)$", "$1,$2"))
                    .filter(k -> k.matches("-?\\d+,-?\\d+"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("the " + kind + " layouts in " + directory + " won't list", e);
        }
    }

    /** Keeps {@code layout} for the wall round column {@code key}. */
    void save(String key, List<BlockPos> layout) {
        try {
            Files.createDirectories(directory);
            JSON.writeValue(file(key).toFile(), layout.stream().map(c -> new int[]{c.x(), c.y(), c.z()}).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("the " + kind + " layout for " + key + " won't write to " + directory, e);
        }
    }

    private Path file(String key) {
        return directory.resolve(kind + "_" + key.replace(',', '_') + ".json");
    }
}
