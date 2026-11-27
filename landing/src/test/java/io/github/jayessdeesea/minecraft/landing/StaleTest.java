package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.landing;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.merge;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.plain;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stale check (design section 3; operator's decision 7, rebase only): a request whose source carries a change
 * that has landed since its base bounces "sync"; so does one whose {@code base..source} holds a merge commit. The
 * shell answers, for each Landing-Source landed since the base, whether it is an ancestor of the request's source
 * ({@code git merge-base --is-ancestor}, a commit its own ancestor); entry {@code n} landed from source {@code 500+n}.
 */
class StaleTest {

    private static final String S1 = sha(501).value(), S2 = sha(502).value();

    static Stream<Arguments> table() {
        return Stream.of(
                // landed since the base, those of their sources in the request's history, base..source -> outcome
                Arguments.of(List.of(), Set.of(), List.of(plain(20), plain(21)), "Fresh"),
                Arguments.of(List.of(landing(10, 1)), Set.of(), List.of(plain(20)), "Fresh"),
                Arguments.of(List.of(landing(10, 1)), Set.of(S1), List.of(plain(20)), "Bounce CARRIES_LANDED [10]"),
                // landed, then reverted on main by a commit of its own: the branch still carries the first
                Arguments.of(List.of(landing(10, 1), landing(11, 2)), Set.of(S1), List.of(plain(20)),
                        "Bounce CARRIES_LANDED [10]"),
                Arguments.of(List.of(landing(10, 1), landing(11, 2)), Set.of(S1, S2), List.of(plain(20)),
                        "Bounce CARRIES_LANDED [10, 11]"),
                // an adopted outside commit has no Landing-Source: nothing to carry
                Arguments.of(List.of(plain(10)), Set.of(), List.of(plain(20)), "Fresh"),
                Arguments.of(List.of(), Set.of(), List.of(plain(20), merge(21)), "Bounce MERGE_COMMIT [21]"),
                // both: the merge is named first - rebasing it away is the fix for both
                Arguments.of(List.of(landing(10, 1)), Set.of(S1), List.of(merge(21)), "Bounce MERGE_COMMIT [21]"),
                // nothing between base and source: still fresh (the merge-tree says "nothing to land")
                Arguments.of(List.of(), Set.of(), List.of(), "Fresh"));
    }

    @ParameterizedTest
    @MethodSource("table")
    void theStaleCheck(List<Commit> landedSinceBase, Set<String> inHistory, List<Commit> branch, String expected) {
        assertEquals(expected, Stale.check(landedSinceBase, inHistory, branch)
                .map(b -> "Bounce " + b.reason() + " " + b.named().stream().map(s -> Integer.parseInt(s, 16)).toList())
                .orElse("Fresh"));
    }

    /**
     * The request itself (amendment 1), refused before any git call that could fault on it: a commit, one parent, its
     * own {@code Landing-Id} alone, one {@code Landing-Source} that is a full sha in the repo. Entry 1's request, from
     * source 501, on base 1, unless a row says otherwise.
     */
    static Stream<Arguments> requests() {
        String own = Fixtures.id(1).value(), other = Fixtures.id(2).value(), source = sha(501).value();
        return Stream.of(
                // the commit the ref names (none: not a commit), the source present -> outcome
                Arguments.of(squash(List.of(sha(1)), List.of(own), List.of(source)), true, "Fresh"),
                Arguments.of(squash(List.of(sha(1)), List.of(), List.of(source)), true, "Bounce LANDING_ID"),
                Arguments.of(squash(List.of(sha(1)), List.of(other), List.of(source)), true, "Bounce LANDING_ID"),
                Arguments.of(squash(List.of(sha(1)), List.of(own, other), List.of(source)), true, "Bounce LANDING_ID"),
                Arguments.of(squash(List.of(sha(1)), List.of(own, own), List.of(source)), true, "Bounce LANDING_ID"),
                Arguments.of(Optional.empty(), true, "Bounce MALFORMED"),
                Arguments.of(squash(List.of(), List.of(own), List.of(source)), true, "Bounce MALFORMED"),
                Arguments.of(squash(List.of(sha(1), sha(2)), List.of(own), List.of(source)), true, "Bounce MALFORMED"),
                Arguments.of(squash(List.of(sha(1)), List.of(own), List.of()), true, "Bounce MALFORMED"),
                Arguments.of(squash(List.of(sha(1)), List.of(own), List.of(source, sha(502).value())), true,
                        "Bounce MALFORMED"),
                Arguments.of(squash(List.of(sha(1)), List.of(own), List.of("abc123")), true, "Bounce MALFORMED"),
                Arguments.of(squash(List.of(sha(1)), List.of(own), List.of(source)), false, "Bounce MALFORMED"));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void theRequestItself(Optional<Commit> squash, boolean sourcePresent, String expected) {
        assertEquals(expected, Stale.request(Fixtures.id(1), squash, sourcePresent).map(b -> "Bounce " + b.reason())
                .orElse("Fresh"));
    }

    @Test
    void aBounceForAnotherIdNamesTheIds() {
        String own = Fixtures.id(1).value(), other = Fixtures.id(2).value();
        assertEquals(List.of(own, other), Stale.request(Fixtures.id(1),
                squash(List.of(sha(1)), List.of(own, other), List.of(sha(501).value())), true).orElseThrow().named());
    }

    private static Optional<Commit> squash(List<Sha> parents, List<String> ids, List<String> sources) {
        return Optional.of(new Commit(sha(30), parents, ids, sources));
    }

    @Test
    void theQuestionsAreTheLandedSources() {
        assertEquals(Set.of(S1, S2), Stale.questions(List.of(landing(10, 1), plain(11), landing(12, 2))));
        // a Landing-Source that is not a sha asks nothing
        Commit odd = new Commit(sha(13), List.of(sha(12)), List.of(Fixtures.id(3).value()), List.of("not-a-sha", "abc123"));
        assertEquals(Set.of(), Stale.questions(List.of(odd)));
    }
}
