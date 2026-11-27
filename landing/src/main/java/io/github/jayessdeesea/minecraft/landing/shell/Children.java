package io.github.jayessdeesea.minecraft.landing.shell;

/**
 * Ends what a {@code landing} process started: a child and all it started in turn. In the tool this is the job object
 * ({@link Job}); a kill that cannot prove them gone says so, and the caller ends its run.
 */
public interface Children {

    /** Ends {@code root} and everything it started; true only when they are known gone. */
    boolean kill(ProcessHandle root);

    /** A kill not proven: what is left may still be running until this process's exit ends it. */
    final class NotGone extends RuntimeException {
        public NotGone(String message) {
            super(message);
        }
    }
}
