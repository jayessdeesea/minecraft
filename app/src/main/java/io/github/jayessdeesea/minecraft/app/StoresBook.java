package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Stockpile;

import java.util.List;
import java.util.Optional;

/**
 * The force's stores as last seen, kept on disk so a restart doesn't leave the platoon leader blind until a
 * chest is opened: live (field469), a minute after a restart the stockpile at 0 logs read as unknown, wood wasn't
 * short, and both released squads were sent for iron. Thread-safe.
 */
final class StoresBook {

    private static final String KEY = "stores";

    private final WallStore store;

    StoresBook(WallStore store) {
        this.store = store;
    }

    /** The stores kept from before the restart, if any were. */
    synchronized Optional<Stockpile> kept() {
        return store.loadRows(KEY).filter(rows -> !rows.isEmpty() && rows.getFirst().length == 11).map(rows -> {
            long[] r = rows.getFirst();
            return new Stockpile((int) r[0], (int) r[1], (int) r[2], (int) r[3], (int) r[4], (int) r[5], (int) r[6], (int) r[7],
                    (int) r[8], (int) r[9], (int) r[10]);
        });
    }

    /** Keeps {@code stores}, and hands them on. */
    synchronized Stockpile keep(Stockpile s) {
        store.saveRows(KEY, List.of(new long[]{s.logs(), s.stone(), s.rations(), s.rawIron(), s.rawCopper(), s.dirt(), s.ironIngots(),
                s.copperIngots(), s.wheat(), s.seeds(), s.saplings()}));
        return s;
    }
}
