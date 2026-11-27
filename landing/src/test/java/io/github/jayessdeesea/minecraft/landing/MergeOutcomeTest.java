package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What {@code git merge-tree --write-tree -z --name-only --no-messages --merge-base=<squash^> <main> <squash>} said,
 * read (design section 3). Its form, as git 2.45.1 gives it: exit 0 clean, 1 on conflict, 128 on a bad ref (with
 * nothing on stdout); stdout the tree, then each conflicted path, each ended by a NUL, the paths raw UTF-8. Main's tree
 * is tree 9.
 */
class MergeOutcomeTest {

    private static final String T5 = sha(5).value(), T9 = sha(9).value();

    static Stream<Arguments> table() {
        return Stream.of(
                // exit, stdout -> outcome
                Arguments.of(0, T5 + "\0", "Candidate 5"),
                // the same tree as main's: the change is already there - never "landed"
                Arguments.of(0, T9 + "\0", "Bounce NOTHING_TO_LAND []"),
                Arguments.of(1, T5 + "\0app/pom.xml\0docs/a b.md\0", "Bounce CONFLICT [app/pom.xml, docs/a b.md]"),
                // -z: a path comes back as it is, never quoted or escaped
                Arguments.of(1, T5 + "\0ünï π.txt\0plain.txt\0", "Bounce CONFLICT [ünï π.txt, plain.txt]"),
                Arguments.of(1, T5 + "\0\"quoted\".txt\0", "Bounce CONFLICT [\"quoted\".txt]"),
                // a conflict that names no path is still a conflict, as git says
                Arguments.of(1, T5 + "\0", "Bounce CONFLICT []"),
                // exit 1 with no tree first: not git's form
                Arguments.of(1, "", "Fault MERGE_TREE"),
                Arguments.of(1, "app/pom.xml\0", "Fault MERGE_TREE"),
                // an empty path, output not ended by a NUL, the newline form (no -z)
                Arguments.of(1, T5 + "\0a.txt\0\0b.txt\0", "Fault MERGE_TREE"),
                Arguments.of(1, T5 + "\0app/pom.xml", "Fault MERGE_TREE"),
                Arguments.of(0, T5 + "\n", "Fault MERGE_TREE"),
                Arguments.of(128, "", "Fault MERGE_TREE"),
                Arguments.of(2, T5 + "\0", "Fault MERGE_TREE"),
                Arguments.of(-1, "", "Fault MERGE_TREE"),
                // exit 0 with no tree, or not a tree: git said something this does not understand
                Arguments.of(0, "", "Fault MERGE_TREE"),
                Arguments.of(0, "fatal: not a tree\0", "Fault MERGE_TREE"));
    }

    @ParameterizedTest
    @MethodSource("table")
    void whatMergeTreeSaid(int exit, String stdout, String expected) {
        assertEquals(expected, line(MergeOutcome.of(exit, stdout, sha(9))));
    }

    private static String line(MergeOutcome outcome) {
        return switch (outcome) {
            case MergeOutcome.Candidate c -> "Candidate " + Integer.parseInt(c.tree().value(), 16);
            case Bounce b -> "Bounce " + b.reason() + " " + b.named();
            case Faulted f -> "Fault " + f.kind();
        };
    }
}
