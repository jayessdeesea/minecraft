package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The operator's "a farm at chunk (x, z)", at a level - sea level unless one's given (operator, 2026-11-21) - and the
 * steps the platoon leader breaks it into by doctrine, each an objective started when the one before is measured done:
 * the chunk flattened to a block below the level, its floor laid in cobblestone there, then the chunk farm template on
 * the floor at the level - dirt at the level, its water holes on the cobblestone. Computed here, never by a model.
 *
 * <p>A tree farm (operator, 2026-11-22: a command for the templated tree chunk) is the same mission with another crop:
 * the chunk flattened to the level - its ground left, for saplings want soil - then the tree chunk's template on it, its
 * sixteen saplings, its chest and its nine torches.
 *
 * @param chunkX the chunk's x, block x / 16
 * @param chunkZ the chunk's z, block z / 16
 * @param level  the farm's ground: the dirt's level, the crops or saplings a block over it
 * @param crop   what's grown on it
 */
public record FarmMission(int chunkX, int chunkZ, int level, Crop crop) {

    /** What a farm mission grows: wheat on a chunk farm, or trees on a tree chunk. */
    public enum Crop { WHEAT, TREES }

    public FarmMission {
        Objects.requireNonNull(crop, "crop");
    }

    /** A wheat farm: the mission as first given. */
    public FarmMission(int chunkX, int chunkZ, int level) {
        this(chunkX, chunkZ, level, Crop.WHEAT);
    }

    /** The level a farm is made at unless the order gives one: the world's sea level. */
    public static final int SEA_LEVEL = 63;

    /** What a step of the mission is. */
    public enum Kind { FLATTEN, FLOOR, FARM_TEMPLATE, TREE_TEMPLATE }

    /** A step of the mission: its place in it, what it is, the level it works to, as said down the chain. */
    public record Step(int index, Kind kind, int level, String task) {
        public Step {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(task, "task");
        }
    }

    /** A farm at chunk ({@code chunkX}, {@code chunkZ}) at sea level. */
    public static FarmMission atSeaLevel(int chunkX, int chunkZ) {
        return new FarmMission(chunkX, chunkZ, SEA_LEVEL);
    }

    /** The mission as given and kept: "a farm at chunk (1, 3) at y=63", "a tree farm at chunk (1, 4) at y=63". */
    public String name() {
        return String.format(Locale.ROOT, "a %sfarm at chunk (%d, %d) at y=%d", crop == Crop.TREES ? "tree " : "", chunkX, chunkZ, level);
    }

    /** Its steps, in order: flatten to the level below, floor it in cobblestone there, the farm template at the level. */
    public List<Step> steps() {
        String chunk = String.format(Locale.ROOT, "chunk (%d, %d), x %d..%d z %d..%d", chunkX, chunkZ, minX(), minX() + 15, minZ(), minZ() + 15);
        if (crop == Crop.TREES) {
            return List.of(
                    new Step(0, Kind.FLATTEN, level, "flatten " + chunk + " to y=" + level),
                    new Step(1, Kind.TREE_TEMPLATE, level, "the tree farm template on " + chunk + " at y=" + level));
        }
        return List.of(
                new Step(0, Kind.FLATTEN, level - 1, "flatten " + chunk + " to y=" + (level - 1)),
                new Step(1, Kind.FLOOR, level - 1, "floor " + chunk + " in cobblestone at y=" + (level - 1)),
                new Step(2, Kind.FARM_TEMPLATE, level, "the farm template on " + chunk + " at y=" + level));
    }

    /** A step as given down the chain, the mission's name and the step's task: "a farm at chunk (1, 3) at y=63: flatten ...". */
    public String said(Step step) {
        return name() + ": " + step.task();
    }

    private static final Pattern NAME = Pattern.compile("a (tree )?farm at chunk \\((-?\\d+), (-?\\d+)\\) at y=(-?\\d+)");

    /** The mission {@code name} names, as {@link #name} says it: empty for any other. */
    public static Optional<FarmMission> named(String name) {
        Matcher m = NAME.matcher(name);
        return m.matches() ? Optional.of(new FarmMission(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                m.group(1) == null ? Crop.WHEAT : Crop.TREES)) : Optional.empty();
    }

    /**
     * The mission {@code text} gives - "x z" or "x z y", a block's x and z and the level, what the operator types after
     * {@code !farm}: a farm on the chunk the block is in, at sea level unless a level is given. Empty for anything else.
     */
    public static Optional<FarmMission> parse(String text) {
        return parse(text, Crop.WHEAT);
    }

    /** As {@link #parse(String)}, for a farm of {@code crop}. */
    public static Optional<FarmMission> parse(String text, Crop crop) {
        String[] words = text.strip().split("[\\s,]+");
        if (words.length != 2 && words.length != 3) {
            return Optional.empty();
        }
        try {
            int level = words.length == 3 ? Integer.parseInt(words[2]) : SEA_LEVEL;
            if (level <= Flatten.LOWEST + 1 || level >= Flatten.HIGHEST) {
                return Optional.empty();
            }
            return Optional.of(new FarmMission(Math.floorDiv(Integer.parseInt(words[0]), 16), Math.floorDiv(Integer.parseInt(words[1]), 16), level, crop));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The chunk's layer at {@code y}: its 16 by 16 cells. */
    public Box layer(int y) {
        return new Box(minX(), y, minZ(), minX() + 15, y, minZ() + 15);
    }

    /** The ground a step's squad is sent to: the chunk's middle, over the step's level. */
    public Position centre(Step step) {
        return new Position(minX() + 8.5, step.level() + 1, minZ() + 8.5);
    }

    /** The chunk's least x. */
    public int minX() {
        return chunkX * 16;
    }

    /** The chunk's least z. */
    public int minZ() {
        return chunkZ * 16;
    }
}
