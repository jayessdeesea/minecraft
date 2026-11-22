package io.github.jayessdeesea.minecraft.command;

/**
 * A baker's duty order read for its soldier (operator, 2026-11-21: the soldier is how; where the makings are, how much
 * and when is the squad leader's): when it draws wheat, how much, and when it bakes - each from its order's batch
 * ({@link DutyOrder#batch}), none the soldier's own. Pure.
 */
public final class BakerDuty {

    /** Wheat a soldier carries at most in one stack: what a draw is made up to, in whole batches. */
    static final int STACK = 64;

    private BakerDuty() {
    }

    /** The wheat {@code order}'s batch takes: its loaves, three wheat a loaf. */
    public static int batchWheat(DutyOrder order) {
        return order.batch() * Subsistence.BREAD_WHEAT;
    }

    /**
     * Whether {@code order}'s baker, carrying {@code wheat}, draws: short of a batch's, with a chest named to draw from.
     * Live (field657), drawing only below a loaf's, its baker carried 15 - more than a loaf, less than a batch - and baked nothing.
     */
    public static boolean draws(DutyOrder order, int wheat) {
        return order.duty() == DutyOrder.Duty.BAKER && order.batch() > 0 && order.source().isPresent() && wheat < batchWheat(order);
    }

    /** What {@code order}'s baker draws up to: the most whole batches a stack holds. */
    public static int drawTo(DutyOrder order) {
        if (order.batch() <= 0 || batchWheat(order) > STACK) {
            throw new IllegalArgumentException("no draw for a batch of " + order.batch() + ": " + order.line());
        }
        return STACK / batchWheat(order) * batchWheat(order);
    }

    /**
     * Whether {@code order}'s baker, carrying {@code wheat} and {@code rations}, goes to its table and bakes: a batch's
     * worth, or a loaf's when it's running out itself (Subsistence.bakeTrip).
     */
    public static boolean bakes(DutyOrder order, int wheat, int rations) {
        return Subsistence.bakeTrip(wheat, rations, order.batch());
    }
}
