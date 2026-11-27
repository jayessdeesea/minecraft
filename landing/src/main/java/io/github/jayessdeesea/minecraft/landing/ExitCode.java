package io.github.jayessdeesea.minecraft.landing;

/**
 * A waiter's exit - the one table; the docs quote it. The tool's other commands answer {@link #OK} done or
 * {@link #REFUSED} (a violation found, a push refused), and every command {@link #LANDING_FAILED} when landing itself
 * failed.
 */
public final class ExitCode {

    public static final int LANDED = 0;
    public static final int FAILED = 1;
    /** A usage error, or nothing to land. */
    public static final int USAGE = 2;
    public static final int BOUNCED = 3;
    public static final int HALTED = 4;
    /**
     * Not all landed, and nothing wrong: run {@code land} again - still queued at {@code --timeout}, or landed with more
     * in the worktree since its source.
     */
    public static final int LAND_AGAIN = 5;
    /** Landing itself failed: the entry, if any, may still be queued - ask {@code landing state <id>}. Never 1, FAILED. */
    public static final int LANDING_FAILED = 6;
    /** A command other than a wait did what it was asked. */
    public static final int OK = 0;
    /** A command other than a wait answered no: the audit found a violation, a push may not go. */
    public static final int REFUSED = 1;

    private ExitCode() {
    }

    /** As {@link #of(EntryState, boolean, boolean)}, the worktree holding nothing more. */
    public static int of(EntryState state, boolean halted) {
        return of(state, halted, false);
    }

    /**
     * The entry's own answer once it has one; withdrawn, it comes back as a bounce does; landed while the worktree
     * holds more since its source ({@code more}), land again. Unsettled, the halt if the integrator is halted, else
     * land again.
     */
    public static int of(EntryState state, boolean halted, boolean more) {
        return switch (state) {
            case EntryState.Landed l -> more ? LAND_AGAIN : LANDED;
            case EntryState.Failed f -> FAILED;
            case EntryState.Bounced b -> b.reason() == BounceReason.NOTHING_TO_LAND ? USAGE : BOUNCED;
            case EntryState.Withdrawn w -> BOUNCED;
            case EntryState.Queued q -> halted ? HALTED : LAND_AGAIN;
            case EntryState.Verifying v -> halted ? HALTED : LAND_AGAIN;
        };
    }
}
