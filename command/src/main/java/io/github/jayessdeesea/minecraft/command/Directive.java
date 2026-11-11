package io.github.jayessdeesea.minecraft.command;

/**
 * One thing a squad leader tells one soldier to do, ahead of its role: done once, then the soldier
 * goes back to its role. Each says when it's done from the soldier's state or the world, never
 * from time, so a directive given again after it's done is done at once - a model repeating its
 * last order changes nothing.
 */
public sealed interface Directive {

    /** How near the point a {@link GoTo} takes the soldier: it picks its footing round it. */
    double GO_TO_TOLERANCE = 1.5;
    /** How far from a {@link Fetch}'s point the soldier looks, across. */
    int FETCH_RADIUS = 16;
    /** The most a {@link Level} box may be across, either way: a soldier's job, not the camp's. */
    int LEVEL_MOST = 32;

    /**
     * Go to {@code at}. A rifleman holds there until told otherwise - a post, an assembly area - so
     * it's never done of itself ({@link #doneBy}). One with a role goes on to its role's work once
     * it's there ({@link Plan#currentDoneBy}): "go there, then mine".
     */
    record GoTo(Position at) implements Directive {

        /**
         * Whether {@code soldier} stands at the point - or, {@code anyHeight}, over or under it: a
         * miner sent to its mine's x and z at its depth gets there on the ground above.
         */
        public boolean reachedBy(SoldierStatus soldier, boolean anyHeight) {
            return Math.hypot(soldier.position().x() - at.x(), soldier.position().z() - at.z()) <= GO_TO_TOLERANCE + 0.5
                    && (anyHeight || Math.abs(soldier.position().y() - at.y()) <= GO_TO_TOLERANCE + 1.5);
        }
    }

    /**
     * Carry {@code amount} of {@code resource}, gathered round {@code near}. Only what a soldier's
     * kit counts: logs, cobblestone and dirt.
     */
    record Fetch(Resource resource, int amount, Position near) implements Directive {
        public Fetch {
            if (resource != Resource.WOOD && resource != Resource.STONE && resource != Resource.SOIL) {
                throw new IllegalArgumentException("a soldier fetches wood, stone or soil, not " + resource);
            }
            if (amount < 1 || amount > 64 * 9) {
                throw new IllegalArgumentException("fetch " + amount);
            }
        }
    }

    /**
     * Make the ground of x {@code minX..maxX}, z {@code minZ..maxZ} level at {@code y}: everything
     * above it dug out, every hole below it filled, so its top is at {@code y - 1} and a soldier
     * stands at {@code y}.
     */
    record Level(int minX, int minZ, int maxX, int maxZ, int y) implements Directive {
        public Level {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("an empty level box: " + minX + ".." + maxX + ", " + minZ + ".." + maxZ);
            }
            if (maxX - minX >= LEVEL_MOST || maxZ - minZ >= LEVEL_MOST) {
                throw new IllegalArgumentException("level at most " + LEVEL_MOST + " across: " + minX + ".." + maxX + ", " + minZ + ".." + maxZ);
            }
        }

        /** Where the soldier works from: the box's middle, at the height it's levelled to. */
        public Position centre() {
            return new Position((minX + maxX + 1) / 2.0, y, (minZ + maxZ + 1) / 2.0);
        }
    }

    /**
     * Whether {@code soldier} has done this, as its own state shows - always false for a
     * {@link GoTo}, which holds, and for a {@link Level}, which the world shows done.
     */
    default boolean doneBy(SoldierStatus soldier) {
        return switch (this) {
            case GoTo _ -> false;
            case Fetch(Resource resource, int amount, Position near) -> carried(soldier, resource) >= amount;
            case Level _ -> false;
        };
    }

    /** How much of {@code resource} - logs, cobblestone, dirt - {@code soldier} carries. */
    static int carried(SoldierStatus soldier, Resource resource) {
        return soldier.condition().flatMap(Condition::kit).map(k -> switch (resource) {
            case WOOD -> k.logs();
            case STONE -> k.stone();
            default -> k.dirt();
        }).orElse(0);
    }

    /**
     * What's left of it for {@code soldier}, as its own state shows: blocks still to go, items still
     * to carry - and 0 for a {@link Level}, which the world measures.
     */
    default int leftFor(SoldierStatus soldier) {
        return switch (this) {
            case GoTo(Position at) -> (int) Math.max(0, Math.ceil(Math.hypot(soldier.position().x() - at.x(),
                    soldier.position().z() - at.z()) - GO_TO_TOLERANCE));
            case Fetch(Resource resource, int amount, Position near) -> Math.max(0, amount - carried(soldier, resource));
            case Level _ -> 0;
        };
    }

    /** Where the soldier goes to do it. */
    default Position where() {
        return switch (this) {
            case GoTo(Position at) -> at;
            case Fetch(Resource resource, int amount, Position near) -> near;
            case Level level -> level.centre();
        };
    }

    /** In words, for the squad leader's view and the log. */
    default String describe() {
        return switch (this) {
            case GoTo(Position at) -> "go to " + at(at);
            case Fetch(Resource resource, int amount, Position near) -> "fetch " + amount + " " + resource + " from round " + at(near);
            case Level(int minX, int minZ, int maxX, int maxZ, int y) ->
                    "level x=" + minX + ".." + maxX + ", z=" + minZ + ".." + maxZ + " at y=" + y;
        };
    }

    private static String at(Position p) {
        return String.format(java.util.Locale.ROOT, "(%.0f, %.0f, %.0f)", p.x(), p.y(), p.z());
    }
}
