package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The keep's mine and the camp's as they stand, and the sites each has left ({@link MineSites}, {@link CampMineSites}),
 * kept across restarts: lost at one, the night shift went back to the old site, dried up, and had to work it out again
 * before it moved. Each write under the store's lock, as the soldier runner's other chest rows are.
 */
final class MineSiteBook {

    /** The keys the keep's mine as it stands, the sites it has left, the camp's and the sites it has left are kept under. */
    private static final String KEEP_MINE = "keepmine", KEEP_TRIED = "minestried", CAMP_MINE = "campmine", CAMP_TRIED = "campminestried";

    private final WallStore store;

    MineSiteBook(WallStore store) {
        this.store = store;
    }

    /**
     * Both mines as they were left put back into {@code chain}. A kept keep's mine whose well is one of {@code deadWells} -
     * found dried up or blocked before a restart - moves on at once: live (field382), the night shift was sent back down
     * the mine field381 had found dried up.
     */
    void restoreInto(ChainOfCommand chain, Set<BlockPos> deadWells) {
        store.load(KEEP_TRIED).ifPresent(tried -> chain.setMinesTried(positions(tried)));
        store.load(KEEP_MINE).filter(l -> l.size() == 1).map(l -> positions(l).getFirst()).ifPresent(chain::setKeepMine);
        Position kept = chain.keepMine();
        if (deadWells.stream().anyMatch(w -> w.x() == (int) Math.floor(kept.x()) && w.z() == (int) Math.floor(kept.z()))) {
            chain.onMineDriedUp((int) Math.floor(kept.x()), (int) Math.floor(kept.z())).ifPresent(site -> keepKeepMine(chain));
        }
        chain.campMines.restore(store.load(CAMP_MINE).filter(l -> l.size() == 1).map(l -> positions(l).getFirst()),
                store.load(CAMP_TRIED).map(MineSiteBook::positions).orElse(List.of()));
    }

    /** The keep's mine moved on, to disk. */
    void keepKeepMine(ChainOfCommand chain) {
        synchronized (store) {
            store.save(KEEP_MINE, List.of(cell(chain.keepMine())));
            store.save(KEEP_TRIED, cells(chain.minesTried()));
        }
    }

    /** The camp's mine, centred at {@code camp}, moved on, to disk. */
    void keepCampMine(ChainOfCommand chain, Position camp) {
        synchronized (store) {
            store.save(CAMP_MINE, List.of(cell(chain.campMines.site(camp))));
            store.save(CAMP_TRIED, cells(chain.campMines.tried()));
        }
    }

    private static List<Position> positions(Collection<BlockPos> cells) {
        return cells.stream().map(c -> new Position(c.x(), c.y(), c.z())).toList();
    }

    private static BlockPos cell(Position p) {
        return BlockPos.containing(p.x(), p.y(), p.z());
    }

    private static List<BlockPos> cells(Collection<Position> sites) {
        return sites.stream().map(MineSiteBook::cell).toList();
    }
}
