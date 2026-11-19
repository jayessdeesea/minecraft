package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * The woods found cut out (ChainOfCommand.onWoodSpent), kept across restarts: live (field479), the garrison restarted
 * and the platoon leader sent Bravo to the wood Charlie had found cut out minutes before. Thread-safe.
 */
final class WoodsBook {

    private static final String KEY = "woods-spent";

    private final WallStore store;
    private final List<long[]> rows = new ArrayList<>();

    WoodsBook(WallStore store) {
        this.store = store;
    }

    /** The woods kept from before a restart put back into {@code chain}, each with when it was found cut out. */
    synchronized void restoreInto(ChainOfCommand chain) {
        store.loadRows(KEY).ifPresent(kept -> kept.stream().filter(r -> r.length == 3).forEach(r -> {
            rows.add(r);
            chain.onWoodSpent(new Position(r[0], 64, r[1]), r[2]);
        }));
    }

    /** The wood at {@code at} found cut out at {@code millis}: told {@code chain}, and kept - the last day's only. */
    synchronized void spent(ChainOfCommand chain, Position at, long millis) {
        chain.onWoodSpent(at, millis);
        rows.removeIf(r -> millis - r[2] > WoodSightings.KEPT_MILLIS);
        rows.add(new long[]{Math.round(at.x()), Math.round(at.z()), millis});
        store.saveRows(KEY, List.copyOf(rows));
    }
}
