package io.github.jayessdeesea.minecraft.command;

/**
 * What each duty's share should yield in a spell ({@link ShareWatch#DUE_MILLIS}), by what there is in it to work
 * (backlog/duty-orders.md; operator, 2026-11-21: the squad leader tracks each soldier's subtask and its outcome): a
 * baker its batch while wheat is stored, a harvester a sweep of its ripe cells, a woodcutter its logs while its strip
 * stands, a planter a sapling while there's ground opened to plant. Nothing to work, nothing expected: its share isn't
 * behind. Measured against what the errand ledger counted it making, not against its product merely changing. Pure.
 */
public final class DutyYield {

    /** Ripe cells a harvester sweeps at once, nearest first, once a field's far ones are this many (bot.supply.Farmer). */
    public static final int SWEEP = 8;
    /** Logs a woodcutter fells a spell while its strip stands: a low bar - a stall shows, a slow axe doesn't. */
    static final int LOGS = 4;

    private DutyYield() {
    }

    /**
     * What {@code order}'s share should yield in a spell with {@code workable} in it to work: a baker's wheat stored and
     * carried, a harvester's ripe cells, a woodcutter's or a planter's ground left to work (more than none, or none).
     */
    public static int expected(DutyOrder order, int workable) {
        return switch (order.duty()) {
            case BAKER -> Math.min(order.batch(), workable / Subsistence.BREAD_WHEAT);
            case HARVESTER -> workable >= SWEEP ? SWEEP : 0;
            case WOODCUTTER -> workable > 0 ? LOGS : 0;
            case PLANTER -> workable > 0 ? 1 : 0;
        };
    }
}
