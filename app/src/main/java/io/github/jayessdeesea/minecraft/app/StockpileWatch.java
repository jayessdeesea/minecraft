package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether the camp's stockpile can be got at (operator, 2026-11-20: when the soldiers can't get at the stockpile,
 * the change is a different stockpile site, not someone else sent to it). A soldier out of ways of its own to reach
 * the camp's chest (its depositor's CANTCO) is noted; two soldiers noted within {@link #WINDOW_MILLIS}, or one for
 * {@link #ONE_MILLIS} of it, and the stockpile moves: its new ground where they stand - the middle of them - its old
 * chest given up, to be emptied into the new one as an old camp's is. Kept on disk: the ground, and the chests given
 * up. Live (field497-499), Pete reported "can't reach the stockpile" six tries running, and the platoon leader's FARM
 * order sent the farmers back to the hilltop chest all day.
 */
final class StockpileWatch {

    /** How long a soldier's "can't reach it" counts. */
    static final long WINDOW_MILLIS = 10 * 60_000;
    /** One soldier reporting it this long, from first to last, is enough on its own. */
    static final long ONE_MILLIS = 3 * 60_000;
    /** Soldiers that, between them, are enough. */
    static final int SOLDIERS = 2;
    /** After a move, how long before the new site is judged: the walk to it, and its chest made. */
    static final long SETTLE_MILLIS = 15 * 60_000;

    static final String GROUND = "stockpile-ground", GIVEN_UP = "stockpiles-given-up";

    private final WallStore store;
    /** Each reporter's first and last report, and where it stood. */
    private final Map<String, long[]> reported = new HashMap<>();
    private final Map<String, Position> where = new HashMap<>();
    private final Set<BlockPos> givenUp = ConcurrentHashMap.newKeySet();
    private volatile BlockPos ground;
    /** The chest the reports are of. */
    private BlockPos watched;
    private long movedAt = Long.MIN_VALUE / 2;

    StockpileWatch(WallStore store) {
        this.store = store;
        store.load(GROUND).filter(l -> l.size() == 1).ifPresent(l -> ground = l.getFirst());
        store.load(GIVEN_UP).ifPresent(givenUp::addAll);
    }

    /**
     * {@code chest} got at - opened, a load put in or a draw taken: what's said of it can't be got at is cleared. Live
     * (field509), the stockpile in use at (-10, 64, 77) was given up on two soldiers' reports - two in pockets - while
     * the rest filled it and drew from it.
     */
    synchronized void reached(BlockPos chest) {
        if (chest.equals(watched)) {
            reported.clear();
        }
    }

    /** The ground the camp's stockpile is sited at, once moved: else the camp's own. */
    Optional<BlockPos> ground() {
        return Optional.ofNullable(ground);
    }

    /** The chests given up for one that can be got at: emptied into the stockpile, never stocked. */
    Set<BlockPos> givenUp() {
        return Set.copyOf(givenUp);
    }

    /**
     * {@code soldier}, at {@code at}, can't reach the camp's chest at {@code chest}: the stockpile's new ground if that
     * makes it {@link #SOLDIERS} reporting, or one reporting for {@link #ONE_MILLIS} - the old chest given up.
     */
    synchronized Optional<BlockPos> unreached(String soldier, Position at, BlockPos chest, long now) {
        if (now - movedAt < SETTLE_MILLIS) {
            return Optional.empty();
        }
        if (!chest.equals(watched)) {
            reported.clear();
            watched = chest;
        }
        reported.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW_MILLIS);
        reported.computeIfAbsent(soldier, s -> new long[]{now, now})[1] = now;
        where.put(soldier, at);
        boolean enough = reported.size() >= SOLDIERS || reported.values().stream().anyMatch(t -> t[1] - t[0] >= ONE_MILLIS);
        if (!enough) {
            return Optional.empty();
        }
        List<Position> at_ = new ArrayList<>(reported.keySet().stream().map(where::get).toList());
        double x = at_.stream().mapToDouble(Position::x).sorted().toArray()[at_.size() / 2];
        double z = at_.stream().mapToDouble(Position::z).sorted().toArray()[at_.size() / 2];
        Position mid = at_.stream().min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x() - x, p.z() - z))).orElseThrow();
        ground = BlockPos.containing(mid.x(), mid.y(), mid.z());
        givenUp.add(chest);
        store.save(GROUND, List.of(ground));
        store.save(GIVEN_UP, List.copyOf(givenUp));
        reported.clear();
        movedAt = now;
        return Optional.of(ground);
    }
}
