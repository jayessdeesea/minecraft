package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Set;

/**
 * A goal handed down to a {@link Soldier} - what to achieve, not how. "How" (which direction to
 * step this tick, whether the next block is solid) is re-derived every tick by the soldier's own
 * decision core against current {@code Position} and {@code WorldView} state - see docs/architecture.md's
 * "Behavior modeling" section. A closed set on purpose: exhaustive {@code switch} over variants
 * should be a compile error, not a silent no-op, whenever a new order kind is added.
 */
public sealed interface Order {

    /** No goal - a soldier with this order takes no movement action on tick. */
    record Idle() implements Order {
    }

    /**
     * Get to any standing spot within {@code horizontalTolerance} blocks (x/z plane) and
     * {@code verticalTolerance} blocks (feet height) of (x, y, z). The tolerances are the soldier's
     * own freedom: it picks the route, around trees and up steps, and where inside the zone to
     * stop. A small vertical tolerance keeps it on one floor of a building. {@code risk} is how
     * much health the soldier may spend on the way ({@link Risk}). {@code urgent} says the time
     * saved is worth food: catching a principal, going to a squadmate's aid - the soldier sprints
     * whether or not it carries rations to make the food up.
     */
    record MoveTo(double x, double y, double z, double horizontalTolerance, double verticalTolerance, Risk risk, boolean urgent)
            implements Order {

        /** A move that isn't urgent. */
        public MoveTo(double x, double y, double z, double horizontalTolerance, double verticalTolerance, Risk risk) {
            this(x, y, z, horizontalTolerance, verticalTolerance, risk, false);
        }
    }

    /**
     * Put a block in each of {@code cells}, in order, from the blocks the soldier carries: a
     * structure the engineer laid out, cell by cell, so the soldier needn't know what it's building.
     * The soldier chooses where to stand for each; a cell it can't reach or that won't take a block
     * is skipped and reported.
     */
    record Place(List<Cell> cells) implements Order {
        public Place {
            cells = List.copyOf(cells);
        }
    }

    /**
     * Dig out every block in {@code box} - logs, leaves, soil, stone - but the cells in
     * {@code except}, keeping what it cuts: a tree from its foot up, the ground from its top down.
     * Clears the sky over ground, takes a rise down. A block it can't reach or can't dig is left
     * and reported. One call for a whole region, as a squad leader gives it: "dig x=0..31,
     * y=64..88, z=0..31" - the soldier works out the cells. A {@code tunnel} is dug cell by cell
     * from where the digger stands instead - a stair cut up out of a pocket, under rock, where no
     * cell has an open top (docs/military-doctrine.md § 7).
     */
    record Dig(Box box, Set<Cell> except, boolean tunnel) implements Order {
        public Dig {
            except = Set.copyOf(except);
        }

        /** A dig of ground: from an open top, level by level. */
        public Dig(Box box, Set<Cell> except) {
            this(box, except, false);
        }
    }

    /**
     * Put a block of cobblestone or dirt it carries into every open cell of {@code box} over ground
     * - air or water - but the cells in {@code except}, lowest first: fill a pit, cap a flooded
     * shaft, make a floor. A cell it can't reach is left and reported. {@code stoneOnly}: cobblestone
     * and never dirt - a floor endermen can't take apart.
     */
    record Fill(Box box, Set<Cell> except, boolean stoneOnly) implements Order {
        public Fill {
            except = Set.copyOf(except);
        }

        public Fill(Box box, Set<Cell> except) {
            this(box, except, false);
        }
    }

    /**
     * Get {@code amount} of {@code resource} from {@code box} and carry it: a supply run - "dig 64
     * dirt from x=-40..-30, y=60..64, z=30..40" - off base, before a fill or a wall. Done once it
     * carries that much, or reported when the box has no more.
     */
    record Gather(Resource resource, Box box, int amount) implements Order {
        public Gather {
            if (amount < 1) {
                throw new IllegalArgumentException("gather " + amount);
            }
        }
    }
}
