package io.github.jayessdeesea.minecraft.landing;

/** Why a request was refused after an attempt: the author's commit stays where it is. */
public enum FailReason {
    /** The verify said BUILD FAILURE. */
    BUILD_FAILED,
    /** The verify was ended twice, the later time at its cap. */
    TIMED_OUT,
    /** The verify was ended twice, the later time for writing nothing for the stall limit. */
    STALLED,
    /** Main moved under its push three times. */
    MAIN_MOVED,
    /** Five attempts and no outcome. */
    ATTEMPTS
}
