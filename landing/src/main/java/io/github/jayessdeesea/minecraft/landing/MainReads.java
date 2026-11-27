package io.github.jayessdeesea.minecraft.landing;

/**
 * Reads of main's shared ref, as {@code absent} reads in a row. Any {@code git fetch} in any worktree of the repository
 * rewrites that ref - the integrator's after its push among them - so a read can find it absent: no answer, read again
 * after the poll's wait (the wait the change), each one said with its count. Five in a row is the ref gone, and the
 * reader gives up; a present read starts the count again.
 */
public record MainReads(int absent) {

    public static final int MOST_ABSENT = 5;
    public static final MainReads NONE = new MainReads(0);

    /** After one more read, present or not. */
    public MainReads read(boolean present) {
        return present ? NONE : new MainReads(absent + 1);
    }

    public boolean givenUp() {
        return absent >= MOST_ABSENT;
    }

    /** What the reader says of the last absent read: reading again, or given up. */
    public String said() {
        return givenUp() ? "main's ref read absent " + absent + " of " + MOST_ABSENT + " in a row"
                : "main's ref read absent (" + absent + " of " + MOST_ABSENT + "); reading again";
    }
}
