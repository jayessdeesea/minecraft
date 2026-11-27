package io.github.jayessdeesea.minecraft.landing;

/** Why a request went back to its author unverified: each is the author's to put right before submitting again. */
public enum BounceReason {
    /** Its tree merged onto main is main's tree: the change is already there. Never "landed". */
    NOTHING_TO_LAND,
    /** Not a request git can read: not a commit, not one parent, or its Landing-Source not one sha in the repo. */
    MALFORMED,
    /** Its squash message does not carry exactly one {@code Landing-Id}, its own: the ids it carries are named. */
    LANDING_ID,
    /** It conflicts with main: the files are named. */
    CONFLICT,
    /** Its source carries a change that landed since its base: sync, then submit. */
    CARRIES_LANDED,
    /** Its {@code base..source} holds a merge commit: rebase only. */
    MERGE_COMMIT
}
