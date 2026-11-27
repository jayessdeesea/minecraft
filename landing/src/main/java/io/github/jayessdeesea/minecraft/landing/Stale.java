package io.github.jayessdeesea.minecraft.landing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The checks on a request before it is merged: that it is a request git can read and that it lands its own id alone,
 * and that it is not built on what main has since moved past (rebase only, operator's decision 7) - a request whose source carries a change that landed
 * after its base would land that change twice, or bring back what was reverted.
 */
public final class Stale {

    private Stale() {
    }

    /**
     * Refused first, since a request git cannot read would make the integrator fault, and three of those halt the
     * queue. {@code squash} is what the ref names, none if not a commit; {@code sourcePresent} whether its
     * {@code Landing-Source} names a commit in the repo (a tree or a blob is none). MALFORMED unless it is a commit with one parent and one
     * {@code Landing-Source}, a full sha, present; LANDING_ID unless its message carries exactly one {@code Landing-Id},
     * its ref's - another id beside it would settle that entry as landed by a commit never verified for it.
     */
    public static Optional<Bounce> request(EntryId ref, Optional<Commit> squash, boolean sourcePresent) {
        if (squash.isEmpty()) {
            return malformed("refs/queue/" + ref + " is not a commit");
        }
        Commit commit = squash.get();
        if (commit.parents().size() != 1) {
            return malformed(commit.sha() + " has " + commit.parents().size() + " parents, not one");
        }
        if (!commit.landingIds().equals(List.of(ref.value()))) {
            return Optional.of(new Bounce(BounceReason.LANDING_ID, commit.landingIds()));
        }
        if (commit.landingSources().size() != 1 || !Sha.valid(commit.landingSources().getFirst())) {
            return malformed(commit.sha() + " has Landing-Source " + commit.landingSources() + ", not one full sha");
        }
        if (!sourcePresent) {
            return malformed("Landing-Source " + commit.landingSources().getFirst() + " is not in the repo");
        }
        return Optional.empty();
    }

    private static Optional<Bounce> malformed(String what) {
        return Optional.of(new Bounce(BounceReason.MALFORMED, List.of(what)));
    }

    /**
     * The Landing-Sources the shell must test against the request's source, {@code git merge-base --is-ancestor}; one
     * that is not a sha asks nothing.
     */
    public static Set<String> questions(List<Commit> landedSinceBase) {
        Set<String> sources = new LinkedHashSet<>();
        landedSinceBase.forEach(c -> c.landingSources().stream().filter(Sha::valid).forEach(sources::add));
        return sources;
    }

    /**
     * A merge commit in {@code base..source} bounces it, naming the merges; else a commit landed since the base whose
     * Landing-Source is in the request's history ({@code inHistory}, the shell's answers to {@link #questions}) bounces
     * it "sync", naming those commits; else it is fresh.
     */
    public static Optional<Bounce> check(List<Commit> landedSinceBase, Set<String> inHistory, List<Commit> branch) {
        List<String> merges = branch.stream().filter(Commit::merges).map(c -> c.sha().value()).toList();
        if (!merges.isEmpty()) {
            return Optional.of(new Bounce(BounceReason.MERGE_COMMIT, merges));
        }
        List<String> carried = landedSinceBase.stream()
                .filter(c -> c.landingSources().stream().anyMatch(inHistory::contains))
                .map(c -> c.sha().value()).toList();
        return carried.isEmpty() ? Optional.empty() : Optional.of(new Bounce(BounceReason.CARRIES_LANDED, carried));
    }
}
