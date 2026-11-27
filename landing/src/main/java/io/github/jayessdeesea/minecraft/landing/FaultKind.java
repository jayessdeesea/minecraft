package io.github.jayessdeesea.minecraft.landing;

/** What went wrong, and so whose it is: what a fault does to its entry and to the integrator follows from that. */
public enum FaultKind {
    /** A fetch failed. It heals itself and costs no verify: it backs off, and never halts. */
    FETCH_FAILED(Blame.INTEGRATOR),
    /** The push refused with main unmoved: a guard or a credential. */
    PUSH_REFUSED(Blame.INTEGRATOR),
    /** The push refused because main moved under it: attempted again on the new main, FAILED at the third. */
    OUTSIDE_PUSH(Blame.ENTRY),
    /** The verify ran past its time and its process tree was killed: FAILED at the second. */
    VERIFY_TIMEOUT(Blame.ENTRY),
    /** The verify's output held no BUILD line, or said BUILD SUCCESS and exited otherwise than 0. */
    VERIFY_NO_RESULT(Blame.INTEGRATOR),
    /** HEAD was not the candidate before or after the verify, or the tracked tree was dirty after it: nothing pushed. */
    VERIFY_TAMPERED(Blame.INTEGRATOR),
    /** {@code git merge-tree} exited neither clean nor in conflict, or said what is not understood. */
    MERGE_TREE(Blame.INTEGRATOR),
    /** Another git call building the candidate failed: the stale check's, or {@code commit-tree}. */
    CANDIDATE(Blame.INTEGRATOR),
    /**
     * A git call a pass needs outside an attempt failed - the snapshot, a SeenMain, a settle's ref deletion, the audit -
     * or a ref deletion after a landing: the run ends, exit 6.
     */
    REPO(Blame.INTEGRATOR),
    /** An invariant of section 4 broken: named at every audit until put right; the queue carries on. */
    AUDIT(Blame.AUDIT);

    /** Whose a fault is. */
    public enum Blame {
        /**
         * The integrator's: it settles nothing, takes back the try it ended, and backs off (5, 10, 20 minutes); three
         * running, a fetch's aside, halt the queue.
         */
        INTEGRATOR,
        /** The entry's: it counts against the entry's cap. */
        ENTRY,
        /** The record's: it stops nothing. */
        AUDIT
    }

    private final Blame blame;

    FaultKind(Blame blame) {
        this.blame = blame;
    }

    public Blame blame() {
        return blame;
    }
}
