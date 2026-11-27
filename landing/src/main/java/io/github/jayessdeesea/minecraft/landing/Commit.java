package io.github.jayessdeesea.minecraft.landing;

import java.util.List;

/**
 * A commit as the queue reads it: its parents and its landing trailers, raw as git gave them - {@code Landing-Id}
 * (the entry it lands) and {@code Landing-Source} (its writer's head when submitted). The audit judges whether they
 * are well formed; nothing here assumes it.
 */
public record Commit(Sha sha, List<Sha> parents, List<String> landingIds, List<String> landingSources) {

    public Commit {
        if (sha == null) {
            throw new IllegalArgumentException("a commit with no sha");
        }
        parents = List.copyOf(parents);
        landingIds = List.copyOf(landingIds);
        landingSources = List.copyOf(landingSources);
    }

    /** Whether it is a merge commit: two parents or more. */
    public boolean merges() {
        return parents.size() >= 2;
    }

    public boolean lands(EntryId id) {
        return landingIds.contains(id.value());
    }
}
