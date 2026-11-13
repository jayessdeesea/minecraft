package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

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
        /** Leaves, solid like {@link #SOLID}, broken by hand at a touch - sometimes for the tree's sapling. */
        LEAVES,
        /** Mined with a pickaxe, solid like {@link #SOLID}: deepslate, andesite, tuff, the ores - its {@link Hardness} rides along. */
        ROCK,
        /** Dug by hand, solid like {@link #SOLID}, and falls into what's dug under it: sand, gravel. */
        GRIT,
        /** Never stand in or walk through: liquids, waterlogged or underwater blocks, fire, powder snow. */
        HAZARD
    }

    private static final String RESOURCE_NAME = "block-classes-777.txt";

    /** Block states that are torches - the light a patrol base is lit by. Filled while loading. */
    private static final BitSet TORCHES = new BitSet();
    /** Block states that are chests, dry or waterlogged - a stockpile is opened at one. Filled while loading. */
    private static final BitSet CHESTS = new BitSet();
    /** Water source blocks (level 0): what a farm is tilled beside. Filled while loading. */
    private static final BitSet WATER_SOURCES = new BitSet();
    /**
     * Water, source or flowing, and the plants that grow only in it - seagrass and kelp, which stand
     * in water a swimmer passes through: swum through at the surface. Filled while loading.
     */
    private static final BitSet WATER = new BitSet();
    /** Ore of any kind: coal, iron, copper, gold, redstone, lapis, emerald, diamond, either stone. Filled while loading. */
    private static final BitSet ORES = new BitSet();
    /** Gravel, which drops flint one time in ten. Filled while loading. */
    private static final BitSet GRAVEL = new BitSet();
    /** Each ore state's metal or mineral - "copper", "iron", "diamond". Filled while loading. */
    private static final Map<Integer, String> ORE_KINDS = new HashMap<>();
    /** Each leaves or sapling state's tree - "spruce", "oak". Filled while loading. */
    private static final Map<Integer, String> TREES = new HashMap<>();
    /** Sapling states. Filled while loading. */
    private static final BitSet SAPLINGS = new BitSet();
    private static final Set<String> SWUM = Set.of("water", "seagrass", "tall_seagrass", "kelp", "kelp_plant");
    /** What a hoe tills into farmland: dirt, grass and dirt paths (coarse and rooted dirt till to dirt first). */
    private static final BitSet TILLABLE = new BitSet();
    /** Short and tall grass: broken for wheat seeds. Filled while loading. */
    private static final BitSet GRASS_PLANTS = new BitSet();
    /** Cobblestone, plain or mossy: what soldiers build with, so a block of it over the camp's floor was put there. */
    private static final BitSet COBBLESTONES = new BitSet();
    /** Each wheat state's growth stage, 0 to {@link #RIPE}. Filled while loading. */
    private static final Map<Integer, Integer> WHEAT_AGES = new HashMap<>();
    /** Wheat's last growth stage: ripe, and broken for wheat and seeds. */
    static final int RIPE = 7;
    /** What digging each {@link BlockClass#ROCK} or {@link BlockClass#GRIT} state takes. Filled while loading. */
    private static final Map<Integer, Hardness> HARDNESS = new HashMap<>();
    /** Which workstation each {@link BlockClass#WORKSTATION} state is. Filled while loading. */
    private static final Map<Integer, Workstation> WORKSTATIONS = new HashMap<>();
    private static final BlockClass[] CLASSES = load();

    /** The workstation {@code stateId} is, if it's one. */
    static Optional<Workstation> workstation(int stateId) {
        return Optional.ofNullable(WORKSTATIONS.get(stateId));
    }

    /** Whether {@code stateId} is a torch, standing or on a wall. */
    static boolean isTorch(int stateId) {
        return stateId >= 0 && TORCHES.get(stateId);
    }

    /** Whether {@code stateId} is a chest. */
    static boolean isChest(int stateId) {
        return stateId >= 0 && CHESTS.get(stateId);
    }

    static boolean isWaterSource(int stateId) {
        return stateId >= 0 && WATER_SOURCES.get(stateId);
    }

    static boolean isWater(int stateId) {
        return stateId >= 0 && WATER.get(stateId);
    }

    /** The tree {@code stateId}'s leaves belong to - empty if it isn't leaves. */
    static Optional<String> leaves(int stateId) {
        return classOf(stateId) == BlockClass.LEAVES ? Optional.ofNullable(TREES.get(stateId)) : Optional.empty();
    }

    /** The tree {@code stateId}'s sapling grows into - empty if it isn't a sapling. */
    static Optional<String> sapling(int stateId) {
        return stateId >= 0 && SAPLINGS.get(stateId) ? Optional.ofNullable(TREES.get(stateId)) : Optional.empty();
    }

    /** What ore {@code stateId} is - "copper", "iron" - or empty if it isn't ore. */
    static Optional<String> oreOf(int stateId) {
        return Optional.ofNullable(ORE_KINDS.get(stateId));
    }

    static boolean isGravel(int stateId) {
        return stateId >= 0 && GRAVEL.get(stateId);
    }

    static boolean isOre(int stateId) {
        return stateId >= 0 && ORES.get(stateId);
    }

    static boolean isTillable(int stateId) {
        return stateId >= 0 && TILLABLE.get(stateId);
    }

    static boolean isCobblestone(int stateId) {
        return stateId >= 0 && COBBLESTONES.get(stateId);
    }

    static boolean isGrassPlant(int stateId) {
        return stateId >= 0 && GRASS_PLANTS.get(stateId);
    }

    /**
     * What digging {@code stateId} takes: as generated for every block that breaks - rock, grit,
     * tall and any other solid block - and plain stone, iron ore and soil as vanilla has them. Empty
     * for what never breaks (bedrock), what has no body to break, and a chest or workstation.
     */
    static Optional<Hardness> hardness(int stateId) {
        return switch (classOf(stateId)) {
            case STONE -> Optional.of(new Hardness(1.5, 0));
            case IRON_ORE -> Optional.of(new Hardness(3.0, 1));
            case SOIL -> Optional.of(new Hardness(0.65, Hardness.HAND));
            case ROCK, GRIT, SOLID, TALL -> Optional.ofNullable(HARDNESS.get(stateId));
            default -> Optional.empty();
        };
    }

    /** {@code stateId}'s growth stage if it's wheat. */
    static OptionalInt wheatAge(int stateId) {
        Integer age = WHEAT_AGES.get(stateId);
        return age == null ? OptionalInt.empty() : OptionalInt.of(age);
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
                    if (fields.length > 2 && fields[2].equals("chest")) {
                        CHESTS.set(stateId);
                    }
                    if (fields.length > 2 && fields[2].endsWith("_sapling")) {
                        SAPLINGS.set(stateId);
                        TREES.put(stateId, fields[2].substring(0, fields[2].length() - "_sapling".length()));
                    }
                    if (fields.length > 2 && fields[2].endsWith("_leaves")) {
                        TREES.put(stateId, fields[2].substring(0, fields[2].length() - "_leaves".length()));
                    }
                    if (fields.length > 2 && fields[2].equals("gravel")) {
                        GRAVEL.set(stateId);
                    }
                    if (fields.length > 2 && fields[2].endsWith("_ore")) {
                        ORES.set(stateId);
                        String kind = fields[2].substring(0, fields[2].length() - "_ore".length()).replace("deepslate_", "");
                        ORE_KINDS.put(stateId, kind);
                    }
                    if (fields.length > 2 && SWUM.contains(fields[2])) {
                        WATER.set(stateId);
                    }
                    if (fields.length > 3 && fields[2].equals("water") && fields[3].equals("level=0")) {
                        WATER_SOURCES.set(stateId);
                    }
                    if (fields.length > 2 && Set.of("dirt", "grass_block", "dirt_path").contains(fields[2])) {
                        TILLABLE.set(stateId);
                    }
                    if (fields.length > 2 && (fields[2].equals("short_grass") || fields[2].equals("tall_grass"))) {
                        GRASS_PLANTS.set(stateId);
                    }
                    if (fields.length > 2 && (fields[2].equals("cobblestone") || fields[2].equals("mossy_cobblestone"))) {
                        COBBLESTONES.set(stateId);
                    }
                    if (fields.length > 3 && fields[2].equals("wheat")) {
                        WHEAT_AGES.put(stateId, Integer.parseInt(fields[3].substring("age=".length())));
                    }
                    classes[stateId] = switch (fields[1]) {
                        case "P" -> BlockClass.PASSABLE;
                        case "S" -> {
                            HARDNESS.put(stateId, new Hardness(Double.parseDouble(fields[3]), Integer.parseInt(fields[4])));
                            yield BlockClass.SOLID;
                        }
                        case "T" -> {
                            if (fields.length > 4) {
                                HARDNESS.put(stateId, new Hardness(Double.parseDouble(fields[3]), Integer.parseInt(fields[4])));
                            }
                            yield BlockClass.TALL;
                        }
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
                        case "V" -> BlockClass.LEAVES;
                        case "C" -> BlockClass.SOLID;
                        case "G" -> {
                            HARDNESS.put(stateId, new Hardness(Double.parseDouble(fields[3]), Hardness.HAND));
                            yield BlockClass.GRIT;
                        }
                        case "W" -> {
                            WORKSTATIONS.put(stateId, Workstation.valueOf(fields[2].toUpperCase(Locale.ROOT)));
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
