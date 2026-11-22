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

    /** What the force is short of, that a squad sent to it makes good. */
    public enum Shortfall {
        /** A squad's Class I RED: the farm. */
        FOOD,
        /** The keep's Class IV: logs, where trees are. */
        WOOD,
        /** Raw iron in the stockpile under {@link #IRON_RESERVE}: the mine. */
        IRON
    }

    /** Iron - raw or ingots - the stockpile is short below: a pickaxe each for eight builders. */
    public static final int IRON_RESERVE = 24;

    /**
     * The gravest of what the force is short of, for a squad the flatten doesn't need (operator,
     * 2026-11-16: "send people to the mines, help farming, hunting"): food first - a squad's Class I
     * RED - then wood, a Class IV request or the stockpile under the logs' reserve ({@link Keep#LOGS_RESERVE}), then
     * iron, then the stockpile's bread under its reserve. With no stockpile seen, only food and the
     * squads' own requests are known. Empty with none.
     */
    public static Optional<Shortfall> shortfall(List<SquadStatus> squads, Optional<Stockpile> stockpile) {
        return shortfalls(squads, stockpile).stream().findFirst();
    }

    /**
     * Every shortfall in alarm, food first (operator, 2026-11-17): released squads are dealt across them
     * in turn - more people work more in parallel, not one shortfall at a time, since the wheat and the
     * trees take their time to grow whoever waits on them - and with an odd number, or one squad, food
     * gets the extra. Food: a squad's Class I RED or the stockpile's bread under its reserve; wood: a
     * Class IV request or the stockpile's logs under their reserve; iron: under {@link #IRON_RESERVE}.
     * With no stockpile seen, only the squads' own ratings and requests are known.
     */
    public static List<Shortfall> shortfalls(List<SquadStatus> squads, Optional<Stockpile> stockpile) {
        List<Shortfall> alarms = new ArrayList<>();
        if (squads.stream().anyMatch(s -> s.classOne().filter(c -> c.rating() == Subsistence.Rating.RED).isPresent())
                || productShort(stockpile, Shortfall.FOOD)) {
            alarms.add(Shortfall.FOOD);
        }
        // Under the reserve, not under a depot's 16: live (field362), the keep's crew was one builder, its wood
        // party none, and the stockpile at 3 logs - nobody could make a pickaxe or a shovel.
        if (squads.stream().anyMatch(s -> s.classFour().isPresent()) || productShort(stockpile, Shortfall.WOOD)) {
            alarms.add(Shortfall.WOOD);
        }
        if (stockpile.filter(st -> st.rawIron() + st.ironIngots() < IRON_RESERVE).isPresent()) {
            alarms.add(Shortfall.IRON);
        }
        // Every soldier armed (operator, 2026-11-18: all soldiers should have swords and armour): one with no
        // sword is short of the stick it's made on, and one short of armour of the metal.
        if (!alarms.contains(Shortfall.WOOD) && squads.stream().flatMap(s -> s.soldiers().stream()).anyMatch(PlatoonSergeant::swordless)) {
            alarms.add(Shortfall.WOOD);
        }
        if (!alarms.contains(Shortfall.IRON) && squads.stream().flatMap(s -> s.soldiers().stream()).anyMatch(PlatoonSergeant::unarmoured)) {
            alarms.add(Shortfall.IRON);
        }
        return List.copyOf(alarms);
    }

    /** Whether the stockpile holds less than its least of a product - bread, logs - that {@code shortfall}'s work makes. */
    static boolean productShort(Optional<Stockpile> stockpile, Shortfall shortfall) {
        return stockpile.map(st -> ProductionLevels.STANDING.short_(st::count).stream()
                .anyMatch(kind -> kind.product() && kind.shortfall() == shortfall)).orElse(false);
    }

    /** Whether {@code soldier} carries no sword - its kit known. */
    static boolean swordless(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(k -> k.weapon().filter(w -> w.endsWith("_sword")).isEmpty()).orElse(false);
    }

    /** Whether {@code soldier} wears less than a full set of armour - its kit known. */
    static boolean unarmoured(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(k -> k.armour() < Kit.ARMOUR_SLOTS).orElse(false);
    }

    /**
     * How many of {@code squads}' soldiers are short of their arms - a sword, or a full set of armour
     * (operator, 2026-11-18): while any is, or the force is short of food, nobody flattens.
     */
    public static long unarmed(List<SquadStatus> squads) {
        return squads.stream().flatMap(s -> s.soldiers().stream()).filter(s -> swordless(s) || unarmoured(s)).count();
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
     * some by a squadmate ({@code sharedTo}). Wheat growing in the camp's farm ({@code farmGrowing}, its caller's to say
     * by night only) is a means for all: its bread is on the way, and live, squads with a farm growing raised Class I
     * RED to the platoon leader all night, which it can't answer better than the farm already does - by day it answers
     * with FORAGE.
     */
    public static Subsistence.Status classOne(List<SoldierStatus> statuses, Set<String> foodless, Set<String> sharedTo, boolean farmGrowing) {
        // Not helpless with a squadmate carrying rations to spare: its hand-over comes as the soldier runs out
        // (CrossLevel.RUNNING_OUT) - live (field641-643), squads read Class I RED 6 to 14 times a run, a soldier at 4 to 9
        // beside squadmates with 40 and 50, and the platoon leader's every order "doesn't answer" it.
        boolean spare = statuses.stream().anyMatch(s -> s.condition().flatMap(Condition::kit)
                .filter(k -> k.rations() >= SPARE_RATIONS).isPresent());
        Set<String> noMeans = farmGrowing || spare ? Set.of() : statuses.stream().map(SoldierStatus::name)
                .filter(n -> foodless.contains(n) && !sharedTo.contains(n)).collect(Collectors.toSet());
        return Subsistence.of(statuses, noMeans);
    }

    /** A squadmate carrying this many rations has some to spare for one running out: twice a soldier's short. */
    static final int SPARE_RATIONS = 2 * Subsistence.FOOD_FIRST;

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
        return crossLevel(statuses, logsWanted, recovering, name -> false);
    }

    /** As above, and saplings to each soldier {@code planting} with none (CrossLevel.SAPLINGS_GIVEN). */
    public static List<CrossLevel.Transfer> crossLevel(List<SoldierStatus> statuses, Map<String, Integer> logsWanted,
                                                       java.util.function.Predicate<String> recovering,
                                                       java.util.function.Predicate<String> planting) {
        List<SoldierStatus> able = statuses.stream().filter(s -> !recovering.test(s.name())).toList();
        Map<String, Integer> wanted = new java.util.HashMap<>();
        able.forEach(s -> Optional.ofNullable(logsWanted.get(s.name())).ifPresent(n -> wanted.put(s.name(), n)));
        Map<String, Integer> fed = new java.util.HashMap<>();
        // Only to one running out, as across squads (operator, 2026-11-21): live (field623), 64 single rations handed
        // round squads in ten minutes between soldiers with days of food - Olga at 20 giving Ned at 12.
        able.forEach(s -> s.condition().flatMap(Condition::kit).filter(k -> k.rations() <= CrossLevel.RUNNING_OUT)
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
        Map<String, Integer> saplingsShort = new java.util.HashMap<>();
        able.stream().filter(s -> planting.test(s.name())).forEach(s -> s.condition().flatMap(Condition::kit)
                .filter(k -> k.saplings() == 0).ifPresent(k -> saplingsShort.put(s.name(), CrossLevel.SAPLINGS_GIVEN)));
        return CrossLevel.pair(able, wanted, fed, disarmed, pickaxesShort, shovelsShort, saplingsShort);
    }

    /**
     * Rations across squads (the operator's: across squads if need be), as a squad of its own would
     * share them: to a soldier running low that no squadmate is sharing with, from the nearest of the
     * force with rations to spare - of {@code outOfContact}, none {@code busy} with a hand-over of
     * its own squad's. A delivery {@code held} from the last cycle stands while both are still free
     * and its receiver still low: chosen afresh, the nearest giver changed as they walked, and each
     * change called the last one's delivery off - live, 69 of 117 called off, one receiver's giver
     * flipping between two soldiers every second.
     */
    public static List<CrossLevel.Transfer> crossRations(List<SoldierStatus> outOfContact, Set<String> busy,
                                                         java.util.Collection<CrossLevel.Transfer> held) {
        List<SoldierStatus> free = outOfContact.stream().filter(s -> !busy.contains(s.name())).toList();
        List<SoldierStatus> short_ = free.stream().filter(s -> s.condition().flatMap(Condition::kit)
                .filter(k -> Subsistence.low(k.rations())).isPresent()).toList();
        Set<String> freeNames = free.stream().map(SoldierStatus::name).collect(java.util.stream.Collectors.toSet());
        Set<String> shortNames = short_.stream().map(SoldierStatus::name).collect(java.util.stream.Collectors.toSet());
        List<CrossLevel.Transfer> kept = held.stream()
                .filter(t -> freeNames.contains(t.giver()) && shortNames.contains(t.receiver())).toList();
        Set<String> taken = new java.util.HashSet<>();
        kept.forEach(t -> {
            taken.add(t.giver());
            taken.add(t.receiver());
        });
        List<CrossLevel.Transfer> all = new java.util.ArrayList<>(kept);
        all.addAll(CrossLevel.across(short_.stream().filter(s -> !taken.contains(s.name())).toList(),
                free.stream().filter(s -> !taken.contains(s.name())).toList()));
        return List.copyOf(all);
    }

    /** How much of a squad keeps the farm: half, one soldier, or none. */
    public enum Farmers { HALF, ONE, NONE }

    /**
     * Field feeding (FM 4-0): how much of a squad keeps the camp's farm by day, whatever its task.
     * Half of it while the squad is {@code hungry} (RED, or one hungry with nothing to eat and no
     * squadmate to share) or the force's rations are below their safety level (PAUSE: stop and fill
     * up); one soldier while the squad is merely {@code short} or the force's rations are below their
     * reorder point (backlog/maturity-model.md: the platoon sergeant tasks producers when the stock
     * crosses it); none otherwise. {@code rations} is the force's Class I as measured, empty before
     * the first look; with no burn measured yet its levels say nothing.
     */
    public static Farmers farmers(boolean hungry, boolean short_, Optional<Supply> rations) {
        Optional<Supply> measured = rations.filter(r -> r.burn() > 0);
        if (hungry || measured.filter(r -> r.state() == Supply.State.PAUSE).isPresent()) {
            return Farmers.HALF;
        }
        if (short_ || measured.filter(r -> r.stock() < r.reorderPoint()).isPresent()) {
            return Farmers.ONE;
        }
        return Farmers.NONE;
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

        /** Below this, the party goes out in strength: the reorder point, and never under the logs' reserve. */
        public double out() {
            return Math.max(Keep.LOGS_RESERVE, reorder);
        }

        /** At this, the last of it comes back: the stockage objective, and never under the logs' most. */
        public double back() {
            return Math.max(Keep.LOGS_MOST, objective);
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
     * a third of the crew, never {@code foreman}, who says the plan. Under the logs' reserve
     * ({@link Wood#out}), between the crew and the stockpile, the whole party cuts; from there to
     * their most ({@link Wood#back}) one builder stays on it - a producer kept at work between the
     * two, so the reserve is refilled before it's an emergency (operator, 2026-11-17: an emergency
     * is a signal to change; live, the party went out at 3 logs) - and at the most none does. It cuts
     * where trees have been seen, {@link #WOOD_LOAD} logs a builder, back with them on the dig and
     * another sent. A place left over plants the grove while it has a cell bare, with trees seen or a
     * sapling carried, unless its planting waits, so what's cut grows back. The rest dig. Members
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
        boolean out = wood.treesSeen() && logs < wood.back();
        // A third of the crew, and never none while the logs are under their reserve and two can be spared
        // one: live (field378), the crew down to two, the party was nobody 38 s after it was Carol, at 3 logs.
        // Under one cutter's load a lone builder - the foreman too - cuts: an emergency is a signal to
        // change (operator, 2026-11-17), and live (field391) the keep's one builder dug on at 0 logs.
        boolean bare = logs < WOOD_LOAD;
        int size = Math.min(WOOD_PARTY, Math.max(crew.size() / 3, out && logs < wood.out() && (crew.size() >= 2 || bare) ? 1 : 0));
        List<String> candidates = crew.stream().map(Hand::name).filter(n -> !n.equals(foreman) || crew.size() == 1 && bare)
                .sorted(Comparator.reverseOrder()).toList();
        // Cutting first while the depot is short: wood now before trees later. In strength under the
        // reserve, one standing to the most.
        int cutters = !out ? 0 : logs < wood.out() ? size : Math.min(1, size);
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
