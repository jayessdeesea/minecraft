package io.github.jayessdeesea.minecraft.landing;

/** Why a request was refused after an attempt: the author's commit stays where it is. */
public enum FailReason {
    /** The verify said BUILD FAILURE. */
    BUILD_FAILED,
    /** The verify timed out twice. */
    TIMED_OUT,
    /** Main moved under its push three times. */
    MAIN_MOVED,
    /** Five attempts and no outcome. */
    ATTEMPTS
}
