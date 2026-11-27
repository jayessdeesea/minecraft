package io.github.jayessdeesea.minecraft.landing;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** What a verify of the candidate in the landing worktree came to. */
public sealed interface VerifyOutcome permits VerifyOutcome.Verified, VerifyOutcome.BuildFailed, Faulted {

    /** Maven's own result lines, whole, once colour codes and the line's surrounding space are stripped. */
    Set<String> SUCCESS = Set.of("[INFO] BUILD SUCCESS");
    Set<String> FAILURE = Set.of("[INFO] BUILD FAILURE", "[ERROR] BUILD FAILURE");
    Pattern COLOUR = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

    record Verified() implements VerifyOutcome {
    }

    record BuildFailed() implements VerifyOutcome {
    }

    /**
     * HEAD not the candidate before or after, or the tracked tree dirty before or after: a fault and nothing pushed, whatever the
     * build said - what was verified may not be the candidate. Then: timed out, a fault against the entry; a BUILD
     * FAILURE line, failed; a BUILD SUCCESS line and exit 0, verified. A BUILD SUCCESS line with another exit, or no
     * result line at all, is the integrator's fault. Only Maven's whole line counts, so a test printing the words does
     * not decide it.
     */
    static VerifyOutcome of(List<String> output, int exit, boolean timedOut, Sha candidate, Sha headBefore, Sha headAfter,
                            boolean cleanBefore, boolean clean) {
        if (!headBefore.equals(candidate)) {
            return new Faulted(FaultKind.VERIFY_TAMPERED, "HEAD was " + headBefore + " before the verify, not " + candidate);
        }
        if (!headAfter.equals(candidate)) {
            return new Faulted(FaultKind.VERIFY_TAMPERED, "HEAD moved to " + headAfter + " during the verify of " + candidate);
        }
        if (!cleanBefore) {
            return new Faulted(FaultKind.VERIFY_TAMPERED, "the tracked tree was dirty before the verify of " + candidate);
        }
        if (!clean) {
            return new Faulted(FaultKind.VERIFY_TAMPERED, "the tracked tree was dirty after the verify of " + candidate);
        }
        if (timedOut) {
            return new Faulted(FaultKind.VERIFY_TIMEOUT, "the verify of " + candidate + " timed out");
        }
        List<String> lines = output.stream().map(line -> COLOUR.matcher(line).replaceAll("").strip()).toList();
        if (lines.stream().anyMatch(FAILURE::contains)) {
            return new BuildFailed();
        }
        if (lines.stream().anyMatch(SUCCESS::contains)) {
            return exit == 0 ? new Verified() : new Faulted(FaultKind.VERIFY_NO_RESULT,
                    "the verify of " + candidate + " said BUILD SUCCESS and exited " + exit);
        }
        return new Faulted(FaultKind.VERIFY_NO_RESULT, "the verify of " + candidate + " exited " + exit
                + " with no BUILD line");
    }
}
