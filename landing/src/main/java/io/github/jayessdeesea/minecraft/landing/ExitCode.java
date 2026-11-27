package io.github.jayessdeesea.minecraft.landing;

/** A waiter's exit - the one table; the docs quote it. */
public final class ExitCode {

    public static final int LANDED = 0;
    public static final int FAILED = 1;
    /** A usage error, or nothing to land. */
    public static final int USAGE = 2;
    public static final int BOUNCED = 3;
    public static final int HALTED = 4;
    /** Still queued at {@code --timeout}. */
    public static final int STILL_QUEUED = 5;

    private ExitCode() {
    }

    /**
     * The entry's own answer once it has one; withdrawn, it comes back as a bounce does. Unsettled, the halt if the
     * integrator is halted, else still queued.
     */
    public static int of(EntryState state, boolean halted) {
        return switch (state) {
            case EntryState.Landed l -> LANDED;
            case EntryState.Failed f -> FAILED;
            case EntryState.Bounced b -> b.reason() == BounceReason.NOTHING_TO_LAND ? USAGE : BOUNCED;
            case EntryState.Withdrawn w -> BOUNCED;
            case EntryState.Queued q -> halted ? HALTED : STILL_QUEUED;
            case EntryState.Verifying v -> halted ? HALTED : STILL_QUEUED;
        };
    }
}
