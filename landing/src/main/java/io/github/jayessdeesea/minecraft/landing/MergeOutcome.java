package io.github.jayessdeesea.minecraft.landing;

import java.util.Arrays;
import java.util.List;

/**
 * What {@code git merge-tree --write-tree -z --name-only --no-messages --merge-base=<squash^> <main> <squash>} said
 * (git 2.45.1): exit 0 clean, 1 a conflict, 128 a bad ref; stdout the tree, then each conflicted path, each ended by
 * a NUL - the paths raw, unquoted, as UTF-8.
 */
public sealed interface MergeOutcome permits MergeOutcome.Candidate, Bounce, Faulted {

    /** Clean, with a tree that differs from main's: the candidate's tree. */
    record Candidate(Sha tree) implements MergeOutcome {
    }

    /**
     * Exit 0 with main's own tree is "nothing to land", never "landed"; exit 1 is a conflict, naming the paths git
     * named. Anything else - another exit, no tree first, an empty path, output not ended by a NUL - is the
     * integrator's fault: git said what this does not understand.
     */
    static MergeOutcome of(int exit, String stdout, Sha mainTree) {
        if (exit != 0 && exit != 1) {
            return fault("exited " + exit, stdout);
        }
        if (!stdout.endsWith("\0")) {
            return fault("exited " + exit + " with output not ended by a NUL", stdout);
        }
        List<String> fields = Arrays.asList(stdout.substring(0, stdout.length() - 1).split("\0", -1));
        Sha tree;
        try {
            tree = Sha.parse(fields.getFirst());
        } catch (IllegalArgumentException e) {
            return fault("exited " + exit + " with no tree first", stdout);
        }
        List<String> paths = fields.subList(1, fields.size());
        if (paths.stream().anyMatch(String::isEmpty)) {
            return fault("exited " + exit + " naming an empty path", stdout);
        }
        if (exit == 1) {
            return new Bounce(BounceReason.CONFLICT, paths);
        }
        return tree.equals(mainTree) ? new Bounce(BounceReason.NOTHING_TO_LAND, List.of()) : new Candidate(tree);
    }

    private static Faulted fault(String what, String stdout) {
        return new Faulted(FaultKind.MERGE_TREE, "git merge-tree " + what + ": '" + stdout.replace('\0', '|') + "'");
    }
}
