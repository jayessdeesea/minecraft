package io.github.jayessdeesea.minecraft.landing;

import java.util.List;
import java.util.Optional;

/**
 * Main since the cutover, for main's own invariants (I1 to I3): its tip, the K it was gathered from (none before the
 * cutover), and its commits {@code K..tip}, oldest first - none before the cutover. Where an entry landed is
 * {@link Landings}', read from all of main.
 */
public record Main(Sha tip, Optional<Sha> since, List<Commit> commits) {

    public Main {
        if (tip == null || since == null) {
            throw new IllegalArgumentException("a main with no tip, or no word of its K");
        }
        commits = List.copyOf(commits);
    }
}
