package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * How each global block-state ID affects movement - backed by a resource generated (not hand-typed)
 * from the pinned server jar's own {@code --reports} output; see
 * {@code tools/block-class-generator/GenerateBlockClasses.java} for the classification rules.
 * States the resource doesn't list are {@link BlockClass#SOLID}, the same "unknown = blocked"
 * default {@link ChunkCache} uses for unloaded chunks.
 *
 * <p>Pinned to protocol 777 by filename - regenerate and rename deliberately when the pin bumps.
 */
final class BlockClassRegistry {

    enum BlockClass {
        /** No collision: air, plants, torches, carpets, open doors and gates. */
        PASSABLE,
        /** A full collision box - the default. */
        SOLID,
        /** A log: solid like {@link #SOLID}, and wood a soldier can chop. */
        LOG,
        /** Plain stone: solid like {@link #SOLID}, and quarried with a pickaxe for cobblestone. */
        STONE,
        /** Collision 1.5 blocks high (fences, walls, closed gates): blocks the cell above too. */
        TALL,
        /** Never stand in or walk through: liquids, waterlogged or underwater blocks, fire, powder snow. */
        HAZARD
    }

    private static final String RESOURCE_NAME = "block-classes-777.txt";

    private static final BlockClass[] CLASSES = load();

    private BlockClassRegistry() {
    }

    static BlockClass classOf(int stateId) {
        return stateId >= 0 && stateId < CLASSES.length && CLASSES[stateId] != null ? CLASSES[stateId] : BlockClass.SOLID;
    }

    private static BlockClass[] load() {
        BlockClass[] classes = new BlockClass[0];
        try (InputStream in = BlockClassRegistry.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Missing block class resource: " + RESOURCE_NAME);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(" ");
                    int stateId = Integer.parseInt(fields[0]);
                    if (stateId >= classes.length) {
                        classes = Arrays.copyOf(classes, Math.max(stateId + 1, classes.length * 2));
                    }
                    classes[stateId] = switch (fields[1]) {
                        case "P" -> BlockClass.PASSABLE;
                        case "T" -> BlockClass.TALL;
                        case "H" -> BlockClass.HAZARD;
                        case "L" -> BlockClass.LOG;
                        case "Q" -> BlockClass.STONE;
                        default -> throw new IllegalStateException("Unknown block class '" + fields[1] + "' in " + RESOURCE_NAME + ": " + line);
                    };
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load block class resource: " + RESOURCE_NAME, e);
        }
        return classes;
    }
}
