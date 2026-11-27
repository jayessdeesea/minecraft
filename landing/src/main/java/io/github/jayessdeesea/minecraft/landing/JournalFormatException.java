package io.github.jayessdeesea.minecraft.landing;

/** A journal line that is not a record: corrupt, half-written, or from a form this version does not know. */
public final class JournalFormatException extends RuntimeException {

    public JournalFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
