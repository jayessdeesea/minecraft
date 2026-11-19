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
 * The chunks its task is worked over round the objective - each a box {@value #SIDE} a side on the server's chunk
 * grid (operator, 2026-11-19: farms aligned on the chunks) - and the supply point. Computed for the platoon leader from its objective, never asked of it. Pure.
 */
public record WorkArea(List<Box> boxes, Optional<Position> dump) {

    /** A chunk's side: each box is a chunk. */
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
     * How far round its point {@code task} is worked, each way, before its chunks are taken: a farm and a grove none -
     * its point's chunk is its ground (operator, 2026-11-19: a farm aligned on the chunk) - a wood
     * {@link WoodDuties#REACH}; empty for work that isn't production done over ground - a wall, a mine, the keep, a
     * patrol.
     */
    public static OptionalInt reach(Objective.Task task) {
        if (task instanceof Objective.Task.Farm || task instanceof Objective.Task.TreeFarm) {
            return OptionalInt.of(0);
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
        // Whole chunks, on the server's grid: every chunk the square round the point touches.
        List<Box> boxes = new ArrayList<>();
        for (int cx = Math.floorDiv(x - r, SIDE); cx <= Math.floorDiv(x + r, SIDE); cx++) {
            for (int cz = Math.floorDiv(z - r, SIDE); cz <= Math.floorDiv(z + r, SIDE); cz++) {
                boxes.add(new Box(cx * SIDE, y - BELOW, cz * SIDE, cx * SIDE + SIDE - 1, y + ABOVE, cz * SIDE + SIDE - 1));
            }
        }
        return Optional.of(new WorkArea(boxes, dump));
    }

    /** {@code objective} with its work area to {@code dump} set, or cleared for a task not worked over ground. */
    public static Objective attach(Objective objective, Optional<Position> dump) {
        Optional<WorkArea> area = of(objective, dump);
        return area.equals(objective.area()) ? objective : objective.withArea(area);
    }

    /** The whole of it, every box in one. */
    public Box whole() {
        return new Box(boxes.stream().mapToInt(Box::minX).min().orElseThrow(), boxes.stream().mapToInt(Box::minY).min().orElseThrow(),
                boxes.stream().mapToInt(Box::minZ).min().orElseThrow(), boxes.stream().mapToInt(Box::maxX).max().orElseThrow(),
                boxes.stream().mapToInt(Box::maxY).max().orElseThrow(), boxes.stream().mapToInt(Box::maxZ).max().orElseThrow());
    }

    /** As said in an order: "in 1 box over x 0..15, z 0..15, the dump at (3, 64, 2)". */
    public String line() {
        Box whole = whole();
        return String.format(Locale.ROOT, "in %d box%s over x %d..%d, z %d..%d, %s", boxes.size(), boxes.size() == 1 ? "" : "es",
                whole.minX(), whole.maxX(), whole.minZ(), whole.maxZ(),
                dump.map(d -> "the dump at (" + Math.round(d.x()) + ", " + Math.round(d.y()) + ", " + Math.round(d.z()) + ")").orElse("no dump yet"));
    }
}
