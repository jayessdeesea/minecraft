package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Recovering a stranded soldier (docs/military-doctrine.md §7): one with no route to its place that
 * doesn't cost it a fall, and nothing to build steps with - a replacement respawned on a ledge, say -
 * is fetched by the nearest squadmate carrying blocks. The squadmate builds the stair the engineer
 * lays out from where the stranded one stands down to the ground, and the stranded one walks down
 * it. Where the engineer can lay none, the squadmate is sent to it, to build what steps it finds - or,
 * carrying a pickaxe and no blocks, to cut them on its way there.
 *
 * <p>A recovery, once begun, lasts until the stranded soldier has moved off where it was stuck: its
 * own report flickers as it tries its route again, and a recovery dropped each time would hand the
 * stair from one squadmate to another and never build it. One per squad, like {@link LinkUp}; the
 * squad's own reports are what it reads, before the link-up drill relabels a replacement.
 */
public final class Recovery {

    /** Blocks a squadmate needs carried to be sent: a stair down from a ledge is two a step. */
    public static final int BLOCKS_TO_FETCH = 24;
    /** Moved this far from where it was stuck, a soldier is on its way down: recovered. */
    static final double MOVED_OFF = 2;
    static final double TOLERANCE = 1.5;

    private static final class Episode {
        final Position at;
        final Optional<List<Cell>> stair;
        /** With no stair down: the cells of a stair up to dig, if one can be laid. */
        final Optional<List<Cell>> cut;
        /** Up a pillar of its own: the pillar's cells, for it to dig down from on top. */
        Optional<List<Cell>> pillar = Optional.empty();
        String fetcher;

        Episode(Position at, Optional<List<Cell>> stair, Optional<List<Cell>> cut) {
            this.at = at;
            this.stair = stair;
            this.cut = cut;
        }
    }

    /** Each stranded soldier being recovered, by name, in the order they were found. */
    private final Map<String, Episode> recovering = new LinkedHashMap<>();
    /** How many reports in a row each soldier has been stranded for. */
    private final Map<String, Integer> strandedFor = new HashMap<>();
    /** This cycle's escapes: each stranded soldier nobody can fetch, sent down to its squad itself. */
    private final Map<String, Order.MoveTo> escaping = new HashMap<>();
    /**
     * Reports in a row a soldier must be stranded before it's fetched: a route that fails once, on a
     * hop across the camp, isn't a soldier stuck. Live, with no route outranking its routine's
     * report, soldiers walking about the camp were found stranded and fetched for a moment each.
     */
    private final int reports;

    /** A recovery for a soldier stranded in its first report. */
    public Recovery() {
        this(1);
    }

    /** A recovery for a soldier stranded {@code reports} reports in a row. */
    public Recovery(int reports) {
        if (reports < 1) {
            throw new IllegalArgumentException("a soldier is stranded in one report at the least, not " + reports);
        }
        this.reports = reports;
    }

    /**
     * Whether {@code soldier} is stranded: no route. Blocks it carries don't make it otherwise - its
     * own planner has already tried building its way with them: live, a soldier with 2 dirt stood
     * in a pit by the camp's wall, never taken for stranded, its only way out falls.
     */
    public static boolean stranded(SoldierStatus soldier) {
        return soldier.report() instanceof Report.Cantco(String reason) && reason.startsWith("no route from");
    }

    /**
     * {@code orders} with, for each soldier being recovered, its fetcher - the nearest squadmate
     * that carried {@link #BLOCKS_TO_FETCH} blocks when it was picked - set to build {@code stair},
     * the engineer's stair down from where it was stranded, laid out once when it's found stranded,
     * or else sent to it. A squadmate fetches one at a time, and none is sent while the squad is in
     * contact.
     */
    public List<Order> orders(List<SoldierStatus> reported, List<Order> orders, Function<SoldierStatus, Optional<List<Cell>>> stair) {
        return orders(reported, orders, stair, s -> Optional.empty());
    }

    /**
     * As {@link #orders(List, List, Function)}, and for one stranded below - in a pocket, with no
     * stair down to build - {@code cut}, the engineer's stair up, dug by the nearest squadmate with a
     * pickaxe: an {@code Order.Dig} of just its cells. {@code reported} and {@code orders} go soldier
     * for soldier, in one order.
     */
    public List<Order> orders(List<SoldierStatus> reported, List<Order> orders, Function<SoldierStatus, Optional<List<Cell>>> stair,
                              Function<SoldierStatus, Optional<List<Cell>>> cut) {
        return orders(reported, orders, stair, cut, s -> Optional.empty());
    }

    /**
     * As {@link #orders(List, List, Function, Function)}, and first, for one up on a pillar -
     * {@code pillar}, the column standing free under it - its own way down: it digs the pillar from
     * on top, a block's drop at a time onto known ground (operator, 2026-11-13: if you know the
     * area, it's safe to dig down). Live, a builder sent off its pillar to feed itself stood at
     * y=98 with no route down.
     */
    public List<Order> orders(List<SoldierStatus> reported, List<Order> orders, Function<SoldierStatus, Optional<List<Cell>>> stair,
                              Function<SoldierStatus, Optional<List<Cell>>> cut, Function<SoldierStatus, Optional<List<Cell>>> pillar) {
        if (reported.size() != orders.size()) {
            throw new IllegalArgumentException(reported.size() + " soldiers reported for " + orders.size() + " orders: "
                    + reported.stream().map(SoldierStatus::name).toList());
        }
        Map<String, SoldierStatus> byName = new LinkedHashMap<>();
        reported.forEach(s -> byName.put(s.name(), s));
        // Off where it was stranded, it's out - but for one digging its pillar down, which is out
        // only at the pillar's foot: each block it digs drops it one, and the drill let go of it
        // after the first, in the squad simulation, and it built its pillar back. And one on a stair
        // up is out only at the stair's top: each step dug moves it a step, and in the squad
        // simulation the drill let go of it every two, waited for it to be stranded again, and laid
        // the stair out afresh from where it stood - five steps up in ten minutes.
        recovering.entrySet().removeIf(e -> !byName.containsKey(e.getKey()) || (e.getValue().pillar.isPresent()
                ? byName.get(e.getKey()).position().y() <= e.getValue().pillar.get().getLast().y() + 0.5
                : e.getValue().cut.isPresent()
                ? byName.get(e.getKey()).position().y() >= e.getValue().cut.get().stream().mapToInt(Cell::y).max().orElse(0) - 0.5
                : Cohesion.distance(byName.get(e.getKey()).position(), e.getValue().at) > MOVED_OFF));
        for (SoldierStatus s : reported) {
            int stuck = stranded(s) ? strandedFor.merge(s.name(), 1, Integer::sum) : 0;
            if (stuck == 0) {
                strandedFor.remove(s.name());
            }
            if (stuck >= reports && !recovering.containsKey(s.name())) {
                Optional<List<Cell>> own = pillar.apply(s).filter(c -> !c.isEmpty());
                Optional<List<Cell>> down = own.isPresent() ? Optional.empty() : stair.apply(s).filter(c -> !c.isEmpty());
                Episode episode = new Episode(s.position(), down, down.isPresent() || own.isPresent() ? Optional.empty() : cut.apply(s));
                episode.pillar = own;
                recovering.put(s.name(), episode);
            }
        }
        if (reported.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return orders;
        }
        List<Order> result = new ArrayList<>(orders);
        List<String> names = List.copyOf(byName.keySet());
        escaping.clear();
        for (Map.Entry<String, Episode> e : recovering.entrySet()) {
            Episode episode = e.getValue();
            // Carrying the blocks for its own stair - logs too - it builds it, from the top down:
            // live, a soldier stood a quarter of an hour on a ledge with 8 logs, nobody in its squad
            // carrying blocks enough to come up for it.
            SoldierStatus self = byName.get(e.getKey());
            if (episode.pillar.isPresent()) {
                result.set(names.indexOf(e.getKey()), dig(episode.pillar.get()));
                continue;
            }
            if (episode.stair.isPresent() && self != null && ownBlocks(self) >= episode.stair.get().size()) {
                result.set(names.indexOf(e.getKey()), new Order.Place(fromTop(episode.stair.get())));
                continue;
            }
            // Below, with a pickaxe: it tunnels up itself, along the engineer's cut. Live, a soldier
            // with a pickaxe and 263 cobblestone stood a day in a dead end at the mine's bottom.
            if (episode.cut.isPresent() && self != null && pickaxe(self)) {
                result.set(names.indexOf(e.getKey()), dig(episode.cut.get()));
                continue;
            }
            if (episode.fetcher == null || !fetching(episode.fetcher, byName)) {
                // With a cut laid and no stair - stranded below, not up on a ledge - a pickaxe is as
                // good as blocks: it digs the cut from the top. Live, a soldier boxed in a pocket waited
                // two runs, nobody in its squad carrying blocks enough. With nothing laid out, only
                // one up on a ledge is still gone to, by a carrier from below whose own route builds
                // the steps it finds; sent down to one in a pocket it has nothing to do there but
                // stand, and live two squadmates in turn walked down to one in a dead end at the
                // mine's bottom and were trapped beside it.
                boolean nothingLaid = episode.stair.isEmpty() && episode.cut.isEmpty();
                episode.fetcher = nearestCarrier(e.getKey(), episode.at, reported, episode.cut.isPresent(), nothingLaid);
            }
            if (episode.fetcher == null) {
                // Nobody to fetch it, and nothing to build its own way down with: it goes down to its
                // squad itself, at MEDIUM - a fall that leaves it half its health or more. Live, soldiers
                // stood for an hour on hut roofs, the wall and a canopy over the camp, a 4-block drop
                // from the ground - 1 health of 20 - and the squad's LOW risk took no fall at all.
                Optional<SoldierStatus> mate = reported.stream()
                        .filter(m -> !recovering.containsKey(m.name()) && !(m.report() instanceof Report.LinkingUp))
                        .min(Comparator.comparingDouble(m -> Cohesion.distance(m.position(), episode.at)));
                if (mate.isPresent()) {
                    Position to = mate.get().position();
                    Order.MoveTo down = new Order.MoveTo(to.x(), to.y(), to.z(), ESCAPE_TOLERANCE, ESCAPE_TOLERANCE, Risk.MEDIUM);
                    result.set(names.indexOf(e.getKey()), down);
                    escaping.put(e.getKey(), down);
                }
                continue;
            }
            Position at = episode.at;
            result.set(names.indexOf(episode.fetcher), episode.stair.<Order>map(Order.Place::new)
                    .or(() -> episode.cut.map(Recovery::dig))
                    .orElse(new Order.MoveTo(at.x(), at.y(), at.z(), TOLERANCE, TOLERANCE, Risk.LOW)));
        }
        return List.copyOf(result);
    }

    /** Whether the drill has {@code soldier}: stranded, and not yet off where it was stuck. */
    public boolean recovering(String soldier) {
        return recovering.containsKey(soldier);
    }

    /**
     * The stair {@code digger} was digging is dug, the world says: the recovery it was for ends - its
     * own, or the one it fetched for - and the names of the soldiers released. With the stair cut the
     * stranded one has a route out, or is stranded afresh where it stands and gets another. In the
     * squad simulation one stood at the top but one step of a dug stair, its dig done and its order
     * met, held by the drill till it should climb out.
     */
    public List<String> release(String digger) {
        List<String> released = recovering.entrySet().stream()
                .filter(e -> e.getKey().equals(digger) || digger.equals(e.getValue().fetcher))
                .map(Map.Entry::getKey).toList();
        released.forEach(recovering::remove);
        return released;
    }

    private static boolean pickaxe(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(Kit::pickaxe).orElse(false);
    }

    /**
     * {@code statuses} as the squad leader is shown them: each soldier being recovered reported
     * {@link Report.Recovering} and put at the rear, so it's never the point man - its CANTCO is the
     * drill's to answer, not the squad's.
     */
    public List<SoldierStatus> asRecovering(List<SoldierStatus> statuses) {
        List<SoldierStatus> formed = new ArrayList<>();
        List<SoldierStatus> rear = new ArrayList<>();
        for (SoldierStatus s : statuses) {
            if (recovering.containsKey(s.name())) {
                rear.add(new SoldierStatus(s.name(), s.position(), s.order(), new Report.Recovering(), s.condition()));
            } else {
                formed.add(s);
            }
        }
        if (formed.isEmpty()) {
            return statuses; // all of it stranded: nobody else to lead
        }
        formed.addAll(rear);
        return List.copyOf(formed);
    }

    /** How near its squadmate a stranded soldier going down on its own is sent. */
    static final double ESCAPE_TOLERANCE = 3;

    /**
     * The order this cycle sends {@code soldier} down to its squad on its own, if it's stranded with
     * nobody to fetch it - to outrank the stand its own routine sends it to, the one it can't reach.
     */
    public Optional<Order.MoveTo> escape(String soldier) {
        return Optional.ofNullable(escaping.get(soldier));
    }

    /** An {@code Order.Dig} of just {@code cells}, as a tunnel - cut cell by cell under rock: their box, and every other cell in it kept. */
    static Order.Dig dig(List<Cell> cells) {
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Cell c : cells) {
            lo[0] = Math.min(lo[0], c.x());
            lo[1] = Math.min(lo[1], c.y());
            lo[2] = Math.min(lo[2], c.z());
            hi[0] = Math.max(hi[0], c.x());
            hi[1] = Math.max(hi[1], c.y());
            hi[2] = Math.max(hi[2], c.z());
        }
        Set<Cell> in = Set.copyOf(cells);
        Set<Cell> except = new HashSet<>();
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = lo[1]; y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    Cell c = new Cell(x, y, z);
                    if (!in.contains(c)) {
                        except.add(c);
                    }
                }
            }
        }
        return new Order.Dig(new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]), Set.copyOf(except), true);
    }

    /** Whether {@code fetcher} can go on fetching: still in the squad, and neither stranded nor rejoining itself. */
    private boolean fetching(String fetcher, Map<String, SoldierStatus> byName) {
        SoldierStatus mate = byName.get(fetcher);
        return mate != null && !recovering.containsKey(fetcher) && !(mate.report() instanceof Report.LinkingUp);
    }

    /**
     * The nearest squadmate fit to fetch {@code stuck} at {@code at}: one carrying {@link #BLOCKS_TO_FETCH}
     * blocks, or a pickaxe when {@code diggingWillDo}; and when {@code ledgeOnly}, one standing below
     * the stranded one - up on a ledge, not down in a pocket.
     */
    private String nearestCarrier(String stuck, Position at, List<SoldierStatus> reported, boolean diggingWillDo, boolean ledgeOnly) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SoldierStatus mate : reported) {
            boolean taken = recovering.values().stream().anyMatch(e -> mate.name().equals(e.fetcher));
            double distance = Cohesion.distance(mate.position(), at);
            boolean equipped = blocks(mate) >= BLOCKS_TO_FETCH
                    || diggingWillDo && pickaxe(mate);
            if (ledgeOnly && mate.position().y() >= at.y() - 1) {
                continue;
            }
            if (!mate.name().equals(stuck) && !taken && equipped && !recovering.containsKey(mate.name())
                    && !(mate.report() instanceof Report.LinkingUp) && distance < bestDistance) {
                best = mate.name();
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * {@code stair} - laid out to be built from the ground up - in the order to build it from its
     * top: first an anchor under the ledge's edge, against the ledge's underside, since the top
     * step only touches the ledge at a corner; then the top step against the anchor, each block
     * under a step against the step, and the next step against that.
     */
    static List<Cell> fromTop(List<Cell> stair) {
        List<Cell> down = stair.reversed();
        Cell top = down.getFirst();
        Optional<Cell> next = down.stream().filter(c -> c.x() != top.x() || c.z() != top.z()).findFirst();
        if (next.isEmpty()) {
            return down;
        }
        List<Cell> built = new ArrayList<>();
        built.add(new Cell(top.x() - (next.get().x() - top.x()), top.y(), top.z() - (next.get().z() - top.z())));
        built.addAll(down);
        return List.copyOf(built);
    }

    /** The blocks {@code soldier} could build with itself: stone and dirt, and logs. */
    private static int ownBlocks(SoldierStatus soldier) {
        return blocks(soldier) + soldier.condition().flatMap(Condition::kit).map(Kit::logs).orElse(0);
    }

    private static int blocks(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(k -> k.stone() + k.dirt()).orElse(0);
    }
}
