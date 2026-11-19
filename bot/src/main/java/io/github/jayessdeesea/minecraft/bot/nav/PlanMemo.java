package io.github.jayessdeesea.minecraft.bot.nav;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * The last route search a soldier made and what it found: asked the same again - from the same cell, to the same goal,
 * over the same ground (WorldView#stamp), round the same cells, with the same blocks to build with - it's the same
 * answer, not another search (backlog/route-planning-cost.md: commit 832's profile, a third of the garrison's samples a
 * wide search made again from where the last one was made). Not thread-safe: one soldier's tick loop.
 */
public final class PlanMemo {

    private Object[] asked;
    private Pathfinder.Result found;

    /** What a search for {@code question} finds: the last answer if it was asked last, else {@code search}'s. */
    public Pathfinder.Result find(Object[] question, Supplier<Pathfinder.Result> search) {
        if (found == null || !Arrays.equals(asked, question)) {
            found = search.get();
            asked = question.clone();
        }
        return found;
    }
}
