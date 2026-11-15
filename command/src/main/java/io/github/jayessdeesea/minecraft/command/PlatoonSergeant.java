package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The platoon sergeant: the platoon's sustainment, as code (backlog/fleet-command.md; ATP 3-21.8,
 * "the platoon's primary sustainment operator"). Pure decisions over value types: given what the
 * soldiers carry and what the platoon needs, who is detached to produce what. The platoon leader
 * sees it only as state - specialists produce, missions consume (docs/echelons/tenets.md).
 *
 * <p>Its first duty is the keep's wood party: producers detached from the dig for logs and for
 * the grove that grows them (docs/military-doctrine.md § 4, "The keep's wood party").
 */
public final class PlatoonSergeant {

    /** Builders off the dig in the wood party at most, cutting and planting together. */
    public static final int WOOD_PARTY = 2;
    /** The logs a cutter brings back: a chest's two and a tool's handle for each of a few builders. */
    public static final int WOOD_LOAD = 16;

    private PlatoonSergeant() {
    }

    /**
     * The LOGSTAT's Class IV line for the keep's builders, said as the request it is when they're
     * short (FM 4-0: construction materials are requisitioned, or obtained locally; operator,
     * 2026-11-13: with no resources, the workers ask for supplies): fewer than {@link Keep#LOGS_WANTED}
     * logs between {@code logsCarried} by the squad and {@code logsStockpiled} in the stockpile, so
     * with no chest standing at the depot ({@code chestStands}) none can be made, nor a pickaxe
     * nor a torch. Live, the keep's builders worked a day with no tree within 48 blocks, no chest to
     * stow in, and no pickaxe to dig stone for the slab. Empty when they have enough.
     */
    public static Optional<String> classFour(int logsCarried, int logsStockpiled, boolean chestStands) {
        if (logsCarried + logsStockpiled >= Keep.LOGS_WANTED) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "Class IV RED - request: the keep's builders carry %d log%s between them and the"
                + " stockpile holds %d: %sno pickaxe or torch to make. Request logs - send a squad for WOOD where trees are.",
                logsCarried, logsCarried == 1 ? "" : "s", logsStockpiled, chestStands ? "" : "no chest at the keep's depot, "));
    }

    /**
     * A squad's Class I ({@link Subsistence}): who is short of food, and of them who has no means
     * of getting any - {@code foodless}, with no wheat and no game in sight - and isn't being shared
     * some by a squadmate ({@code sharedTo}). Wheat growing in the camp's farm is a means for all:
     * its bread is on the way, and live, squads with a farm growing raised Class I RED to the platoon leader
     * all night, which it can't answer better than the farm already does.
     */
    public static Subsistence.Status classOne(List<SoldierStatus> statuses, Set<String> foodless, Set<String> sharedTo, boolean farmGrowing) {
        Set<String> noMeans = farmGrowing ? Set.of() : statuses.stream().map(SoldierStatus::name)
                .filter(n -> foodless.contains(n) && !sharedTo.contains(n)).collect(Collectors.toSet());
        return Subsistence.of(statuses, noMeans);
    }

    /**
     * Cross-leveling within a squad (docs/military-doctrine.md § 13), the hand-overs it makes from
     * what its soldiers carry: logs to one that wants them for a tool ({@code logsWanted}) and can't
     * chop any; rations to one running low ({@link Subsistence#low}), hungry yet or not - it asks
     * before it goes hungry (the operator's); a spare weapon to one bare-handed, as a replacement
     * respawns with nothing; and a pickaxe or a shovel to one short of the tool load
     * ({@link CrossLevel#TOOLS_LOW}). Never to or from one {@code recovering}: a hand-over walks the
     * giver to the receiver, and live the foreman spent a day on the stairs with rations for one in a
     * dead end, and in the squad simulation one tunnelling out of a pocket left its stair nineteen
     * times. Kit isn't asked for by one {@link Report.Rearming}: mid-craft its weapon can be on a
     * window's cursor, uncounted, and in the squad simulation a sword went round the squad that way.
     * The pairing itself is {@link CrossLevel#pair}.
     */
    public static List<CrossLevel.Transfer> crossLevel(List<SoldierStatus> statuses, Map<String, Integer> logsWanted,
                                                       java.util.function.Predicate<String> recovering) {
        List<SoldierStatus> able = statuses.stream().filter(s -> !recovering.test(s.name())).toList();
        Map<String, Integer> wanted = new java.util.HashMap<>();
        able.forEach(s -> Optional.ofNullable(logsWanted.get(s.name())).ifPresent(n -> wanted.put(s.name(), n)));
        Map<String, Integer> fed = new java.util.HashMap<>();
        able.forEach(s -> s.condition().flatMap(Condition::kit).filter(k -> Subsistence.low(k.rations()))
                .ifPresent(k -> fed.put(s.name(), CrossLevel.RATIONS_GIVEN)));
        Map<String, Integer> disarmed = new java.util.HashMap<>();
        Map<String, Integer> pickaxesShort = new java.util.HashMap<>();
        Map<String, Integer> shovelsShort = new java.util.HashMap<>();
        statuses.stream().filter(s -> !(s.report() instanceof Report.Rearming)).forEach(s -> s.condition().flatMap(Condition::kit).ifPresent(k -> {
            if (k.weapon().isEmpty()) {
                disarmed.put(s.name(), 1);
            }
            if (k.pickaxes() < CrossLevel.TOOLS_LOW) {
                pickaxesShort.put(s.name(), 1);
            }
            if (k.shovels() < CrossLevel.TOOLS_LOW) {
                shovelsShort.put(s.name(), 1);
            }
        }));
        return CrossLevel.pair(able, wanted, fed, disarmed, pickaxesShort, shovelsShort);
    }

    /**
     * Rations across squads (the operator's: across squads if need be), as a squad of its own would
     * share them: to a soldier running low that no squadmate is sharing with, from the nearest of the
     * force with rations to spare - of {@code outOfContact}, none {@code busy} with a hand-over of
     * its own squad's.
     */
    public static List<CrossLevel.Transfer> crossRations(List<SoldierStatus> outOfContact, Set<String> busy) {
        List<SoldierStatus> free = outOfContact.stream().filter(s -> !busy.contains(s.name())).toList();
        List<SoldierStatus> short_ = free.stream().filter(s -> s.condition().flatMap(Condition::kit)
                .filter(k -> Subsistence.low(k.rations())).isPresent()).toList();
        return CrossLevel.across(short_, free);
    }

    /** One builder the party may be drawn from: its name, and the logs and saplings it carries. */
    public record Hand(String name, int logs, int saplings) {
    }

    /**
     * What the party is to work for, as the sergeant's look at the world found it.
     *
     * @param stockpileLogs  the logs in the stockpile
     * @param treesSeen      whether a squad on the work has seen trees to cut
     * @param groveBare      whether the grove has a cell with nothing growing
     * @param groveWaits     whether the grove's planting is paused - its last planting failed
     * @param reorder        the logs' reorder point as measured ({@link Supply#reorderPoint}), 0 with no burn yet
     * @param objective      the logs' stockage objective as measured ({@link Supply#objective})
     */
    public record Wood(int stockpileLogs, boolean treesSeen, boolean groveBare, boolean groveWaits, double reorder, double objective) {

        /** With no supply measured: the floors alone. */
        public Wood(int stockpileLogs, boolean treesSeen, boolean groveBare, boolean groveWaits) {
            this(stockpileLogs, treesSeen, groveBare, groveWaits, 0, 0);
        }

        /** Below this, the party goes out: the reorder point, and never under half the logs a depot wants. */
        public double out() {
            return Math.max(Keep.LOGS_WANTED / 2.0, reorder);
        }

        /** At this, it comes back: the stockage objective, and never under the logs a depot wants. */
        public double back() {
            return Math.max(Keep.LOGS_WANTED, objective);
        }
    }

    /**
     * Who's in the wood party: its cutters, its planter if it has one, and whether it's out for
     * logs - kept between looks, since it goes out below half the logs wanted and comes back at all
     * of them.
     */
    public record WoodParty(Set<String> cutters, Optional<String> planter, boolean cutting) {

        public static final WoodParty NONE = new WoodParty(Set.of(), Optional.empty(), false);

        /** Whether {@code name} is off the dig in the party. */
        public boolean has(String name) {
            return cutters.contains(name) || planter.filter(name::equals).isPresent();
        }
    }

    /**
     * The keep's wood party (operator, 2026-11-14: trees are everywhere - take a break from the dig,
     * harvest some, build crates at the depot, and replant). {@link #WOOD_PARTY} builders at most and
     * a third of the crew, never {@code foreman}, who says the plan: when the crew and the stockpile
     * hold fewer than half {@link Keep#LOGS_WANTED} logs between them, and until they hold all of
     * them, the party cuts trees, where any have been seen - {@link #WOOD_LOAD} logs a builder, back
     * with them on the dig and another sent. A place left over plants the grove while it has a cell
     * bare, with trees seen or a sapling carried, unless its planting waits. The rest dig. Members
     * stay put between looks, the rest drawn from the end of the roster; a planter carrying saplings
     * first, since it plants at once.
     */
    public static WoodParty woodParty(List<Hand> crew, String foreman, WoodParty was, Wood wood) {
        Map<String, Hand> byName = crew.stream().collect(Collectors.toMap(Hand::name, Function.identity(), (a, b) -> a));
        int logs = crew.stream().mapToInt(Hand::logs).sum() + wood.stockpileLogs();
        boolean saplings = crew.stream().anyMatch(h -> h.saplings() > 0);
        // Out below half what's wanted, back at all of it: live and in the squad simulation, a party
        // that went at 15 and stopped at 16 came and went with every chest and handle made.
        // The platoon sergeant tasks producers when the stock crosses its reorder point (backlog/
        // maturity-model.md, the gate), and stands them down at the stockage objective.
        boolean out = wood.treesSeen() && (was.cutting() ? logs < wood.back() : logs < wood.out());
        int size = Math.min(WOOD_PARTY, crew.size() / 3);
        List<String> candidates = crew.stream().map(Hand::name).filter(n -> !n.equals(foreman)).sorted(Comparator.reverseOrder()).toList();
        // Cutting first while the depot is short: wood now before trees later.
        int cutters = out ? size : 0;
        List<String> cutting = new ArrayList<>(was.cutters().stream()
                .filter(n -> byName.containsKey(n) && byName.get(n).logs() < WOOD_LOAD).sorted().toList());
        while (cutting.size() > cutters) {
            cutting.removeLast();
        }
        for (String n : candidates) {
            if (cutting.size() >= cutters) {
                break;
            }
            if (!cutting.contains(n) && byName.get(n).logs() < WOOD_LOAD / 2) {
                cutting.add(n);
            }
        }
        boolean plants = wood.groveBare() && (wood.treesSeen() || saplings) && !wood.groveWaits() && cutting.size() < size;
        Optional<String> planter = !plants ? Optional.empty()
                : was.planter().filter(byName::containsKey).filter(n -> !cutting.contains(n))
                .or(() -> candidates.stream().filter(n -> !cutting.contains(n))
                        .min(Comparator.comparing(n -> byName.get(n).saplings() > 0 ? 0 : 1)));
        return new WoodParty(Set.copyOf(cutting), planter, out);
    }
}
