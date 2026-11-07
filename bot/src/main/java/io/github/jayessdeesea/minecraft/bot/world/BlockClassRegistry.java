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
        /** Tilled for crops: solid like {@link #SOLID}, and never dug for a step - a farm isn't a slope. */
        FARMLAND,
        /** A crafting table or furnace: solid like {@link #SOLID}, and opened to craft or smelt. */
        WORKSTATION,
        /** Collision 1.5 blocks high (fences, walls, closed gates): blocks the cell above too. */
        TALL,
        /** Mined with a pickaxe, solid like {@link #SOLID}: deepslate, andesite, tuff, the ores - its {@link Hardness} rides along. */
        ROCK,
        /** Dug by hand, solid like {@link #SOLID}, and falls into what's dug under it: sand, gravel. */
        GRIT,
        /** Never stand in or walk through: liquids, waterlogged or underwater blocks, fire, powder snow. */
        HAZARD
    }

    private static final String RESOURCE_NAME = "block-classes-777.txt";

    /** Block states that are torches - the light a patrol base is lit by. Filled while loading. */
    private static final java.util.BitSet TORCHES = new java.util.BitSet();
    /** Water source blocks (level 0): what a farm is tilled beside. Filled while loading. */
    private static final java.util.BitSet WATER_SOURCES = new java.util.BitSet();
    /**
     * Water, source or flowing, and the plants that grow only in it - seagrass and kelp, which stand
     * in water a swimmer passes through: swum through at the surface. Filled while loading.
     */
    private static final java.util.BitSet WATER = new java.util.BitSet();
    private static final java.util.Set<String> SWUM = java.util.Set.of("water", "seagrass", "tall_seagrass", "kelp", "kelp_plant");
    /** What a hoe tills into farmland: dirt, grass and dirt paths (coarse and rooted dirt till to dirt first). */
    private static final java.util.BitSet TILLABLE = new java.util.BitSet();
    /** Short and tall grass: broken for wheat seeds. Filled while loading. */
    private static final java.util.BitSet GRASS_PLANTS = new java.util.BitSet();
    /** Each wheat state's growth stage, 0 to {@link #RIPE}. Filled while loading. */
    private static final java.util.Map<Integer, Integer> WHEAT_AGES = new java.util.HashMap<>();
    /** Wheat's last growth stage: ripe, and broken for wheat and seeds. */
    static final int RIPE = 7;
    /** What digging each {@link BlockClass#ROCK} or {@link BlockClass#GRIT} state takes. Filled while loading. */
    private static final java.util.Map<Integer, Hardness> HARDNESS = new java.util.HashMap<>();
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

    static boolean isWaterSource(int stateId) {
        return stateId >= 0 && WATER_SOURCES.get(stateId);
    }

    static boolean isWater(int stateId) {
        return stateId >= 0 && WATER.get(stateId);
    }

    static boolean isTillable(int stateId) {
        return stateId >= 0 && TILLABLE.get(stateId);
    }

    static boolean isGrassPlant(int stateId) {
        return stateId >= 0 && GRASS_PLANTS.get(stateId);
    }

    /**
     * What digging {@code stateId} takes, for the blocks a soldier digs: rock and grit as generated,
     * plain stone, iron ore and soil as vanilla has them. Empty for anything else.
     */
    static java.util.Optional<Hardness> hardness(int stateId) {
        return switch (classOf(stateId)) {
            case STONE -> java.util.Optional.of(new Hardness(1.5, 0));
            case IRON_ORE -> java.util.Optional.of(new Hardness(3.0, 1));
            case SOIL -> java.util.Optional.of(new Hardness(0.65, Hardness.HAND));
            case ROCK, GRIT -> java.util.Optional.of(HARDNESS.get(stateId));
            default -> java.util.Optional.empty();
        };
    }

    /** {@code stateId}'s growth stage if it's wheat. */
    static java.util.OptionalInt wheatAge(int stateId) {
        Integer age = WHEAT_AGES.get(stateId);
        return age == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(age);
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
                    if (fields.length > 2 && SWUM.contains(fields[2])) {
                        WATER.set(stateId);
                    }
                    if (fields.length > 3 && fields[2].equals("water") && fields[3].equals("level=0")) {
                        WATER_SOURCES.set(stateId);
                    }
                    if (fields.length > 2 && java.util.Set.of("dirt", "grass_block", "dirt_path").contains(fields[2])) {
                        TILLABLE.set(stateId);
                    }
                    if (fields.length > 2 && (fields[2].equals("short_grass") || fields[2].equals("tall_grass"))) {
                        GRASS_PLANTS.set(stateId);
                    }
                    if (fields.length > 3 && fields[2].equals("wheat")) {
                        WHEAT_AGES.put(stateId, Integer.parseInt(fields[3].substring("age=".length())));
                    }
                    classes[stateId] = switch (fields[1]) {
                        case "P" -> BlockClass.PASSABLE;
                        case "T" -> BlockClass.TALL;
                        case "H" -> BlockClass.HAZARD;
                        case "L" -> BlockClass.LOG;
                        case "Q" -> BlockClass.STONE;
                        case "D" -> BlockClass.SOIL;
                        case "O" -> BlockClass.IRON_ORE;
                        case "F" -> BlockClass.FARMLAND;
                        case "R" -> {
                            HARDNESS.put(stateId, new Hardness(Double.parseDouble(fields[3]), Integer.parseInt(fields[4])));
                            yield BlockClass.ROCK;
                        }
                        case "G" -> {
                            HARDNESS.put(stateId, new Hardness(Double.parseDouble(fields[3]), Hardness.HAND));
                            yield BlockClass.GRIT;
                        }
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
