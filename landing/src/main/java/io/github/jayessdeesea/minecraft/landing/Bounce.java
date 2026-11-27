package io.github.jayessdeesea.minecraft.landing;

import java.util.List;

/** A request sent back unverified, and what it names: the conflicting files, or the commits at fault. */
public record Bounce(BounceReason reason, List<String> named) implements MergeOutcome {

    public Bounce {
        if (reason == null) {
            throw new IllegalArgumentException("a bounce with no reason");
        }
        named = List.copyOf(named);
    }
}
