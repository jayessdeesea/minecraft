package io.github.jayessdeesea.minecraft.landing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where each entry landed: a trailer scan of all of main's history, not just {@code K..tip}, so an entry landed at or
 * before the cutover still reads as landed. A {@code Landing-Id} that is not an id is skipped here (I2 names it); an
 * id on two commits (I3) lands at the older.
 */
public record Landings(Map<EntryId, Sha> commits) {

    public Landings {
        commits = Map.copyOf(commits);
    }

    /** From main's commits, oldest first. */
    public static Landings of(List<Commit> oldestFirst) {
        Map<EntryId, Sha> commits = new LinkedHashMap<>();
        for (Commit commit : oldestFirst) {
            for (String id : commit.landingIds()) {
                try {
                    commits.putIfAbsent(EntryId.parse(id), commit.sha());
                } catch (IllegalArgumentException malformed) {
                    // not an id: nothing landed by it; I2 names the commit
                }
            }
        }
        return new Landings(commits);
    }

    public Optional<Sha> of(EntryId id) {
        return Optional.ofNullable(commits.get(id));
    }
}
