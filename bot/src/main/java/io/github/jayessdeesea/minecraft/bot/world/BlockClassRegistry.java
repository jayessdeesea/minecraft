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
        /** Dirt, grass, clay and the like: solid like {@link #SOLID}, and dug by hand in about a second. */
        SOIL,
        /** Iron ore: solid like {@link #SOLID}, and mined with a stone pickaxe or better for raw iron. */
        IRON_ORE,
        /** A crafting table or furnace: solid like {@link #SOLID}, and opened to craft or smelt. */
        WORKSTATION,
        /** Collision 1.5 blocks high (fences, walls, closed gates): blocks the cell above too. */
        TALL,
        /** Never stand in or walk through: liquids, waterlogged or underwater blocks, fire, powder snow. */
        HAZARD
    }

    private static final String RESOURCE_NAME = "block-classes-777.txt";

    /** Block states that are torches - the light a patrol base is lit by. Filled while loading. */
    private static final java.util.BitSet TORCHES = new java.util.BitSet();
    /** Which workstation each {@link BlockClass#WORKSTATION} state is. Filled while loading. */
    private static final java.util.Map<Integer, Workstation> WORKSTATIONS = new java.util.HashMap<>();
    private static final BlockClass[] CLASSES = load();

    /** The workstation {@code stateId} is, if it's one. */
    static java.util.Optional<Workstation> workstation(int stateId) {
        return java.util.Optional.ofNullable(WORKSTATIONS.get(stateId));
    }

    /** Whether {@code stateId} is a torch, standing or on a wall. */
    static boolean isTorch(int stateId) {
        return stateId >= 0 && TORCHES.get(stateId);
    }

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
                    if (fields.length > 2 && (fields[2].equals("torch") || fields[2].equals("wall_torch"))) {
                        TORCHES.set(stateId);
                    }
                    classes[stateId] = switch (fields[1]) {
                        case "P" -> BlockClass.PASSABLE;
                        case "T" -> BlockClass.TALL;
                        case "H" -> BlockClass.HAZARD;
                        case "L" -> BlockClass.LOG;
                        case "Q" -> BlockClass.STONE;
                        case "D" -> BlockClass.SOIL;
                        case "O" -> BlockClass.IRON_ORE;
                        case "W" -> {
                            WORKSTATIONS.put(stateId, Workstation.valueOf(fields[2].toUpperCase(java.util.Locale.ROOT)));
                            yield BlockClass.WORKSTATION;
                        }
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
