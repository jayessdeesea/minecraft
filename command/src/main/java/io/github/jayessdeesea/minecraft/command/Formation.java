package io.github.jayessdeesea.minecraft.command;

/**
 * A squad's movement formation - ATP 3-21.8 (2024) ch. 3 §2; see docs/military-doctrine.md §2 for
 * the quoted doctrine and why each exists. Offsets are in a leader-relative frame: forward is the
 * direction of travel, right is to its right, both in multiples of the squad's {@link Interval}.
 */
public enum Formation {

    /** ¶3-46/3-47: the basic formation - point man leads, others echeloned right and left behind. */
    WEDGE,

    /** ¶3-48: single file behind the point man - restrictive terrain, buildings, dense vegetation. */
    FILE,

    /** ¶3-54: abreast of the point man - maximum firepower to the front, for an assault. */
    LINE;

    /**
     * Slot {@code index}'s offset from the point man (index 0, always at the origin), as
     * {@code {forward, right}} in blocks. Alternates right, left, right... outward.
     */
    double[] offset(int index, double interval) {
        if (index == 0) {
            return new double[]{0, 0};
        }
        int rank = (index + 1) / 2;
        int side = index % 2 == 1 ? 1 : -1;
        return switch (this) {
            // Diagonal echelon: each rank one interval back-and-out from the one before it.
            case WEDGE -> new double[]{-rank * interval * DIAGONAL, side * rank * interval * DIAGONAL};
            case FILE -> new double[]{-index * interval, 0};
            case LINE -> new double[]{0, side * rank * interval};
        };
    }

    private static final double DIAGONAL = Math.sqrt(0.5);
}
