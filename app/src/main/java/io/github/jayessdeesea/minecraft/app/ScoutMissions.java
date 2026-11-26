package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Herder;
import io.github.jayessdeesea.minecraft.bot.supply.Livestock;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.ScoutMission;
import io.github.jayessdeesea.minecraft.command.ScoutSectors;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The operator's scout missions (command.ScoutMission): each open one held by one squad, whatever the day's work would
 * have given it; each soldier of that squad a sector and a {@link Herder} for it, ticked in its own loop and walked by
 * its squad's; and the mission's end said - what was brought of what was found, where the land ends - when the squad's
 * soldiers are done, or a day after it was given (the operator's: a mission lasts a day unless it says otherwise).
 *
 * <p>Kept in memory: a restart drops an open scout mission, and its squad goes back to the day's work.
 */
final class ScoutMissions {

    private static final Logger LOG = LogManager.getLogger(ScoutMissions.class);

    /** How long a mission stays open. */
    static final long EXPIRES_MILLIS = FarmMissions.DAY_MILLIS;
    /** How long a soldier with none of the kind's food is waited on to draw some before the squad's end is said without it. */
    static final long FOOD_WAIT_MILLIS = 3 * 60_000;
    /** How much of the kind's food a soldier with none draws: one in the hand leads, and under a loaf's wheat none is baked. */
    static final int FOOD_DRAWN = 2;
    /** Ticks a swap's answer is waited for before another is sent. */
    private static final int SWAP_ANSWER_TICKS = 40;
    /** How near a walk's end a soldier is ordered, across and up or down: inside what its herder counts as there. */
    private static final double NEAR = 1.0, LEVELS = 4;

    /** One soldier at a mission: its herder, and what the herder last said - the soldier's tick writes, its squad's loop reads. */
    private static final class Scout {
        final ScoutMission mission;
        final Livestock kind;
        final Herder herder;
        final List<Position> landEnds = new CopyOnWriteArrayList<>();
        volatile Herder.Step step;
        volatile long shortSince = -1;
        /** Whether its squad is held to other work this cycle - the night's shelter: its herder isn't ticked, its walks' time not counted. */
        volatile boolean held = true;
        /** Ticks left to wait for the server's answer to a swap of the food into the hotbar: its own tick's only. */
        int swapWait;

        Scout(String soldier, ScoutMission mission, ScoutSectors.Sector sector) {
            this.mission = mission;
            this.kind = Livestock.valueOf(mission.animal().name());
            this.herder = new Herder(soldier, kind, sector, mission.place(), mission.bringTo());
        }

        boolean done() {
            Herder.Step said = step;
            return said != null && said.phase() == Herder.Phase.DONE;
        }
    }

    /** Each open mission and when it was given, in the order given. */
    private final Map<ScoutMission, Long> open = new LinkedHashMap<>();
    /** The squad last put on each open mission. */
    private final Map<ScoutMission, String> squadOn = new java.util.HashMap<>();
    private final Map<String, Scout> scouts = new ConcurrentHashMap<>();
    /** Where each mission given this run wants its animals. */
    private final Set<Position> broughtTo = ConcurrentHashMap.newKeySet();
    /** Wakes the platoon leader: a mission given or ended, its squad to be given its task or another. */
    private final Consumer<String> wake;
    /** Keys a line out on the net. */
    private final Consumer<String> radio;

    private final LongSupplier clock;

    ScoutMissions(Consumer<String> wake, Consumer<String> radio, LongSupplier clock) {
        this.wake = wake;
        this.radio = radio;
        this.clock = clock;
    }

    /** {@code mission} given: a squad is put on it at the platoon leader's next decision. */
    synchronized String give(ScoutMission mission) {
        if (open.putIfAbsent(mission, clock.getAsLong()) != null) {
            LOG.info("[Mission] {} is open already", mission.name());
            return "WILCO: " + mission.name() + " - open already, its squad on it.";
        }
        broughtTo.add(mission.bringTo());
        LOG.info("[Mission] {} - given: one squad, {} blocks out each way, for a day at most", mission.name(), (int) ScoutMission.RADIUS);
        radio.accept("[PL -> " + OperatorChat.OPERATOR + "] WILCO: " + mission.name());
        wake.accept("the operator's scouting");
        return "WILCO: " + mission.name() + " - one squad, each soldier a sector of the ground " + (int) ScoutMission.RADIUS
                + " blocks out each way, on " + Livestock.valueOf(mission.animal().name()).food().name().toLowerCase(Locale.ROOT)
                + "; it ends when the squad is back, or after a day.";
    }

    /**
     * Where the force keeps animals: where each mission given this run wants them, from the moment it's given, and the
     * animal farms {@code built} - which the registers keep over a restart. An animal there is stock, never hunted
     * (WorldView.stockAt): led in for a farm, it isn't a squadmate's wool or supper.
     */
    List<Position> stockKept(Set<FarmMission> built) {
        List<Position> kept = new ArrayList<>(broughtTo);
        built.stream().filter(m -> m.crop().animals()).forEach(m -> kept.add(m.centre(m.steps().getLast())));
        return kept;
    }

    /** The squads put on the open missions: no review takes them off. */
    synchronized Set<String> scouting() {
        return Set.copyOf(squadOn.values());
    }

    /** The missions open now, as given. */
    synchronized List<ScoutMission> open() {
        return List.copyOf(open.keySet());
    }

    /**
     * {@code day}'s objectives with one squad on each open mission: the one on it already, else the last in the order of
     * battle that isn't the farm squad nor {@code held} to work of its own - a farm mission's step. A mission past its
     * day is closed first. A squad newly put on one is said.
     */
    synchronized List<Objective> guarded(List<Objective> day, List<SquadStatus> statuses, Optional<String> farmSquad, Predicate<Optional<Objective>> held) {
        long now = clock.getAsLong();
        for (ScoutMission late : open.entrySet().stream().filter(e -> now - e.getValue() > EXPIRES_MILLIS).map(Map.Entry::getKey).toList()) {
            end(late, "its day is up", true);
        }
        List<Objective> out = new ArrayList<>(day);
        Set<Integer> on = new HashSet<>();
        for (ScoutMission mission : open.keySet()) {
            Objective scout = mission.objective();
            List<Integer> movable = new ArrayList<>();
            for (int i = 0; i < statuses.size(); i++) {
                if (!on.contains(i) && farmSquad.filter(statuses.get(i).squadName()::equals).isEmpty() && !held.test(Optional.of(out.get(i)))) {
                    movable.add(i);
                }
            }
            if (movable.isEmpty()) {
                LOG.warn("[Guardrail] no squad to put on '{}': the others hold the missions' steps, the farm squad keeps the farm", scout.mission());
                continue;
            }
            Optional<Integer> standing = movable.stream()
                    .filter(i -> statuses.get(i).standing().filter(o -> o.mission().equals(scout.mission())).isPresent()).findFirst();
            // The squad put on it before, while it's free: a night's shelter takes its objective, not its mission.
            int k = standing.or(() -> movable.stream().filter(i -> statuses.get(i).squadName().equals(squadOn.get(mission))).findFirst()).orElse(movable.getLast());
            if (!statuses.get(k).squadName().equals(squadOn.put(mission, statuses.get(k).squadName()))) {
                LOG.warn("[Guardrail] the operator's scouting - {} put on '{}', not '{}'", statuses.get(k).squadName(), scout.mission(), out.get(k).mission());
            }
            out.set(k, scout);
            on.add(k);
        }
        return out;
    }

    /**
     * {@code squad}'s order for the soldier at {@code place} in it, while {@code objective} is an open mission's and the
     * squad is out of contact: where its herder walks, on the ground there, or held where it stands while its herder
     * waits or is done. Empty - the squad leader's order stands - in contact, before its first tick, and while it has
     * none of the kind's food: the walk to draw some is its depositor's. With every soldier done, or short of food
     * past {@link #FOOD_WAIT_MILLIS}, the mission is ended and its outcome said. While it scouts it is taken off
     * {@code hunters}: the animals in sight are the mission's, not game.
     */
    Optional<Order> order(String squad, int place, List<SoldierStatus> soldiers, Optional<Objective> objective, WorldView world, boolean inContact, Set<String> hunters) {
        long now = clock.getAsLong();
        String soldier = soldiers.get(place).name();
        Optional<ScoutMission> mission = objective.map(Objective::task).filter(Objective.Task.Scout.class::isInstance)
                .map(t -> ((Objective.Task.Scout) t).mission()).filter(m -> open().contains(m));
        if (mission.isEmpty()) {
            // Its herder is kept while its mission is open - its squad sheltering the night, or held to other work a
            // while, goes on at dawn from where each soldier was - and dropped once the mission is closed.
            List<ScoutMission> stillOpen = open();
            scouts.computeIfPresent(soldier, (name, was) -> stillOpen.contains(was.mission) ? was : null);
            Optional.ofNullable(scouts.get(soldier)).ifPresent(kept -> kept.held = true);
            return Optional.empty();
        }
        hunters.remove(soldier);
        Scout scout = scouts.compute(soldier, (name, was) -> was != null && was.mission.equals(mission.get()) ? was
                : new Scout(name, mission.get(), ScoutSectors.of(mission.get().place(), ScoutMission.RADIUS, soldiers.size()).get(place)));
        // The squad's soldiers at it: one the recovery drill has, or climbing out of a mine, is given no order here and
        // has no herder - it isn't waited on.
        List<Scout> squadScouts = soldiers.stream().map(s -> scouts.get(s.name())).filter(s -> s != null && s.mission.equals(mission.get())).toList();
        if (!squadScouts.isEmpty() && squadScouts.stream().allMatch(s -> s.done() || s.shortSince >= 0 && now - s.shortSince > FOOD_WAIT_MILLIS)) {
            outcome(squad, mission.get(), squadScouts);
            return Optional.empty();
        }
        scout.held = false;
        Herder.Step step = scout.step;
        if (inContact || step == null || scout.shortSince >= 0) {
            return Optional.empty();
        }
        Position at = soldiers.get(place).position();
        if (step.walkTo().isEmpty()) {
            return Optional.of(new Order.MoveTo(at.x(), at.y(), at.z(), 2, LEVELS, Risk.LOW));
        }
        Position to = step.walkTo().get();
        // An animal's own height is where it stands; a leg's end or the place they're wanted is put on the ground there,
        // looked for from the soldier's height - the order gave an x and a z.
        double y = step.phase() == Herder.Phase.APPROACH ? to.y() : TerrainSurveyor.groundAt(world, BlockPos.containing(to.x(), at.y(), to.z())).y();
        return Optional.of(new Order.MoveTo(to.x(), y, to.z(), NEAR, LEVELS, Risk.LOW));
    }

    /** The mission's outcome from its squad's herders (docs/conventions.md, "Check the outcome"): brought against found, a shortfall said SHORT. */
    private void outcome(String squad, ScoutMission mission, List<Scout> squadScouts) {
        int picked = squadScouts.stream().mapToInt(s -> s.herder.picked()).sum();
        int brought = squadScouts.stream().mapToInt(s -> s.herder.brought()).sum();
        long noFood = squadScouts.stream().filter(s -> !s.done()).count();
        List<String> shores = squadScouts.stream().flatMap(s -> s.landEnds.stream())
                .map(p -> String.format(Locale.ROOT, "(%d, %d)", (int) Math.floor(p.x()), (int) Math.floor(p.z()))).toList();
        String said = brought + " of " + picked + " " + mission.animal().said() + " found brought in"
                + (noFood > 0 ? "; " + noFood + " soldier(s) had no " + squadScouts.getFirst().kind.food().name().toLowerCase(Locale.ROOT) + " to lead with" : "")
                + (shores.isEmpty() ? "" : "; the land ends at " + String.join(", ", shores));
        boolean miss = brought < picked || picked == 0 || noFood > 0;
        if (miss) {
            LOG.warn("[Outcome] [{}] {}: {} - SHORT", squad, mission.name(), said);
        } else {
            LOG.info("[Outcome] [{}] {}: {}", squad, mission.name(), said);
        }
        synchronized (this) {
            end(mission, "[" + squad + "] " + said, picked == 0 || brought == 0);
        }
    }

    /** {@code mission} closed - {@code failed} or accomplished - and said to the operator; the platoon leader woken to give its squad other work. */
    private void end(ScoutMission mission, String why, boolean failed) {
        if (open.remove(mission) == null) {
            return;
        }
        scouts.values().removeIf(s -> s.mission.equals(mission));
        squadOn.remove(mission);
        LOG.info("[Mission] {} {} - {}", mission.name(), failed ? "FAILED" : "ACCOMPLISHED", why);
        radio.accept("[PL -> " + OperatorChat.OPERATOR + "] " + mission.name() + " " + (failed ? "FAILED" : "ACCOMPLISHED") + ": " + why);
        wake.accept("the operator's scouting ended");
    }

    /**
     * {@code soldier}'s turn at its herder: ticked while it scouts and isn't {@code threatened}, the kind's food taken
     * in hand when the herder wants it there and the hands are free, put away when it doesn't. Under way - the hands
     * its own - while the food is to be in hand. A shore it meets is said up the net, once.
     */
    Hands.Turn turn(String soldier, String squad, PlaySession session, Inventory inventory, Position at, WorldView world, boolean threatened) {
        return new Hands.Turn() {
            @Override
            public boolean active() {
                Scout scout = scouts.get(soldier);
                Herder.Step said = scout == null ? null : scout.step;
                return !threatened && said != null && said.holdsFood() && scout.shortSince < 0 && !scout.held;
            }

            @Override
            public void tick(boolean free) {
                Scout scout = scouts.get(soldier);
                if (scout == null || threatened || scout.held || scout.done()) {
                    return;
                }
                ItemClass food = scout.kind.food();
                if (inventory.count(food) == 0) {
                    if (scout.shortSince < 0) {
                        scout.shortSince = clock.getAsLong();
                        LOG.warn("[{}] {} has no {} to lead {} with - draws some before it scouts", squad, soldier, food, scout.kind);
                    }
                    return;
                }
                scout.shortSince = -1;
                Herder.Step step = scout.herder.tick(at, world);
                scout.step = step;
                step.landEnds().ifPresent(shore -> {
                    scout.landEnds.add(shore);
                    radio.accept(String.format(Locale.ROOT, "[%s -> PL] the land ends at (%d, %d): water, no %s led past it", squad,
                            (int) Math.floor(shore.x()), (int) Math.floor(shore.z()), scout.mission.animal().said()));
                });
                boolean inHand = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot())).equals(Optional.of(food));
                if (step.holdsFood() && free && !inHand) {
                    int slot = inventory.slotOf(food);
                    int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : inventory.spareHotbarSlot();
                    if (slot < Inventory.HOTBAR_FIRST) {
                        // One swap, then the server's resend of the inventory is waited for: a second sent on the stale
                        // state swaps it back.
                        if (scout.swapWait > 0) {
                            scout.swapWait--;
                            return;
                        }
                        scout.swapWait = SWAP_ANSWER_TICKS;
                        session.swapWithHotbar(0, 0, slot, hotbar);
                    }
                    hold(hotbar);
                } else if (!step.holdsFood() && inHand && free) {
                    // Put away: any hotbar slot that doesn't hold it - they stay where they were brought.
                    for (int hotbar = 0; hotbar < 9; hotbar++) {
                        if (!ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar)).equals(Optional.of(food))) {
                            hold(hotbar);
                            break;
                        }
                    }
                }
            }

            private void hold(int hotbar) {
                session.selectHotbarSlot(hotbar);
                inventory.selectHotbar(hotbar);
            }
        };
    }

    /** Whether {@code soldier} is at an open mission: its hunter is held - the animals in sight are the mission's. */
    boolean scouts(String soldier) {
        return scouts.containsKey(soldier);
    }

    /** The food {@code soldier} is to draw from a chest: its kind's, while it scouts with none. */
    Optional<ItemClass> draws(String soldier) {
        return Optional.ofNullable(scouts.get(soldier)).filter(s -> s.shortSince >= 0).map(s -> s.kind.food());
    }

    /**
     * What {@code soldier} reports while it scouts: its herder's phase and how many it leads, as work - one unit of it
     * left till its herder is done; or CANTCO with none of the kind's food.
     */
    Optional<Report> report(String soldier) {
        Scout scout = scouts.get(soldier);
        if (scout == null) {
            return Optional.empty();
        }
        if (scout.shortSince >= 0) {
            return Optional.of(new Report.Cantco("no " + scout.kind.food().name().toLowerCase(Locale.ROOT) + " to lead " + scout.mission.animal().said() + " with"));
        }
        Herder.Step step = scout.step;
        return step == null ? Optional.empty() : Optional.of(new Report.Working("scout for " + scout.mission.animal().said() + ": "
                + step.phase().name().toLowerCase(Locale.ROOT) + ", " + step.led() + " led", step.phase() == Herder.Phase.DONE ? 0 : 1));
    }
}
