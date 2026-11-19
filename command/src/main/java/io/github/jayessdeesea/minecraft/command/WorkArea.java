package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The ground a squad is sent to work and where its yield goes (operator, 2026-11-18: "alpha go do farming in these
 * x,z 1-16 areas and dump the supplies in this supply dump"): the OPORD's paragraph 3 and 4 at the squad's level.
 * Boxes of at most {@value #SIDE} a side tiling the square its task is worked over round the objective, and the
 * supply point. Computed for the platoon leader from its objective, never asked of it. Pure.
 */
public record WorkArea(List<Box> boxes, Optional<Position> dump) {

    /** The longest side of a box: a soldier's share is a box or a run of them. */
    public static final int SIDE = 16;
    /** How far under and over the objective's height the ground is worked. */
    static final int BELOW = 8;
    static final int ABOVE = 24;

    public WorkArea {
        boxes = List.copyOf(boxes);
        Objects.requireNonNull(dump, "dump");
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("a work area of no ground");
        }
    }

    /**
     * How far round its point {@code task} is worked, each way: a farm and a grove 16, a wood {@link WoodDuties#REACH};
     * none for work that isn't production done over ground - a wall, a mine, the keep, a patrol.
     */
    public static OptionalInt reach(Objective.Task task) {
        if (task instanceof Objective.Task.Farm || task instanceof Objective.Task.TreeFarm) {
            return OptionalInt.of(SIDE);
        }
        return task.equals(new Objective.Task.Gather(Resource.WOOD)) ? OptionalInt.of(WoodDuties.REACH) : OptionalInt.empty();
    }

    /** {@code objective}'s work area, its yield to {@code dump}; none for a task not worked over ground. */
    public static Optional<WorkArea> of(Objective objective, Optional<Position> dump) {
        OptionalInt reach = reach(objective.task());
        if (reach.isEmpty()) {
            return Optional.empty();
        }
        int x = (int) Math.floor(objective.location().x());
        int y = (int) Math.floor(objective.location().y());
        int z = (int) Math.floor(objective.location().z());
        int r = reach.getAsInt();
        List<Box> boxes = new ArrayList<>();
        for (int[] xs : runs(x - r, x + r)) {
            for (int[] zs : runs(z - r, z + r)) {
                boxes.add(new Box(xs[0], y - BELOW, zs[0], xs[1], y + ABOVE, zs[1]));
            }
        }
        return Optional.of(new WorkArea(boxes, dump));
    }

    /** {@code objective} with its work area to {@code dump} set, or cleared for a task not worked over ground. */
    public static Objective attach(Objective objective, Optional<Position> dump) {
        Optional<WorkArea> area = of(objective, dump);
        return area.equals(objective.area()) ? objective : objective.withArea(area);
    }

    /** {@code from..to} cut into runs of at most {@link #SIDE}, as near equal as they come. */
    static List<int[]> runs(int from, int to) {
        int length = to - from + 1;
        int parts = (length + SIDE - 1) / SIDE;
        List<int[]> runs = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            runs.add(new int[]{from + length * i / parts, from + length * (i + 1) / parts - 1});
        }
        return runs;
    }

    /** The whole of it, every box in one. */
    public Box whole() {
        return new Box(boxes.stream().mapToInt(Box::minX).min().orElseThrow(), boxes.stream().mapToInt(Box::minY).min().orElseThrow(),
                boxes.stream().mapToInt(Box::minZ).min().orElseThrow(), boxes.stream().mapToInt(Box::maxX).max().orElseThrow(),
                boxes.stream().mapToInt(Box::maxY).max().orElseThrow(), boxes.stream().mapToInt(Box::maxZ).max().orElseThrow());
    }

    /** As said in an order: "in 9 boxes over x -16..16, z -16..16, the dump at (3, 64, 2)". */
    public String line() {
        Box whole = whole();
        return String.format(Locale.ROOT, "in %d box%s over x %d..%d, z %d..%d, %s", boxes.size(), boxes.size() == 1 ? "" : "es",
                whole.minX(), whole.maxX(), whole.minZ(), whole.maxZ(),
                dump.map(d -> "the dump at (" + Math.round(d.x()) + ", " + Math.round(d.y()) + ", " + Math.round(d.z()) + ")").orElse("no dump yet"));
    }
}
