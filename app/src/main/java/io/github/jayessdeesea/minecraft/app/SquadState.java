package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Setbacks;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import io.github.jayessdeesea.minecraft.command.LinkUp;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Plan;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Recovery;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadOrder;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.TaskRegister;
import io.github.jayessdeesea.minecraft.command.WorkOrders;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static io.github.jayessdeesea.minecraft.app.SoldierRunner.RELIEVED_MILLIS;
import static io.github.jayessdeesea.minecraft.app.SoldierRunner.STRANDED_REPORTS;

/** A squad's deterministic state across cycles - squad thread only. */
final class SquadState {
    final LinkUp linkUp = new LinkUp();
    Set<String> linkingUp = Set.of();
    /** The squad leader's order whose plan changes were last made - each order's are made once. */
    SquadOrder planned;
    /** Its soldiers' work orders, followed until done or timed out. */
    final WorkOrders workOrders = new WorkOrders();
    String haltedFor;
    boolean fortifying;
    boolean allInside;
    /** Where the squad's patrol base is laid out, once it is. */
    Position baseSite;
    /** The last patrol base the squad built - to cut its way out of, once it moves on. */
    PatrolBase.Plan builtBase;
    /** A patrol base point with no level ground near it - logged once. */
    Position unbuildable;
    /** Whether the base was lit at the last check. */
    boolean lit;
    /** Who the squad's torchbearer was last cycle: kept while it still can, so a run isn't handed over half done. */
    String torchbearer;
    /** The squad's staircase, while its objective is MINE. */
    Staircase stairs;
    /** The last staircase it worked, kept by day: the way out for any of it still down there. */
    Staircase lastStairs;
    /** Soldiers sent up its last stairs, said once each climb. */
    final Set<String> climbingOut = new HashSet<>();
    /** The squad's second level, for its second pair of cutters (the rules of a mine: two to a level). */
    Staircase second;
    /** The soldiers' own reports this cycle, before link-up relabels a replacement. */
    List<SoldierStatus> reported = List.of();
    /** The objective's x and z the wall was laid out for: its key in {@code walls}. */
    String wallKey;
    /** The objective's x and z the stairs were sited for. */
    int[] stairsFor;
    /** The recovery drill: who's stranded, the stair laid out for each, and who's fetching. */
    final Recovery recovery = new Recovery(STRANDED_REPORTS);
    /** Soldiers the recovery drill is sending down on their own - said once a spell. */
    final Set<String> escaping = new HashSet<>();
    /** Torches left to put down round a SECURE objective, at the last check: -1 before the first. */
    int torchesLeft = -1;
    /** The squad's wall, laid out once round its FORTIFY objective's ground. */
    List<BlockPos> wall;
    /** The ground its wall is laid out round. */
    BlockPos wallCentre;
    /** What was last said of the keep's work, said again only when it changes. */
    String keepSaid;
    /** Set while the keep's fill waits on cobblestone, said once. */
    String keepWaiting;
    /** What's left of the squad's share of the keep's dig and fill, -1 when it isn't on them. */
    int keepDigLeft = -1;
    /** The keep's ore set aside for a better pickaxe, as last said. */
    Set<Cell> keepOreAside = Set.of();
    int keepFillLeft = -1;
    /** Wall cells left to fill at the last check: -1 before the first. */
    int wallLeft = -1;
    /** Blocks of the camp's ground work still to dig - trees over it, ground above its level - at the last check: -1 before the first. */
    int digLeft = -1;
    /** The wall's blocks of dirt or grass, to replace with stone, at the last check: -1 before the first. */
    int softLeft = -1;
    /** Open cells of the camp's ground work still to fill, at the last check: -1 before the first. */
    int fillLeft = -1;
    /**
     * What the ground work's log line last said: said again only when it changes. FORTIFY resets
     * the counts each cycle it has no wall, and camp upkeep measures them again - live, the same
     * line was logged every second.
     */
    String groundSaid;
    /** Soldiers sent off base for dirt by the ground work's drill, until they carry a load. */
    final Set<String> fetchingDirt = new HashSet<>();
    /** The hands spare of the ground work's dig - more of them than its strips - kept off it till it's done. */
    /** The FARM objective's point no ground to farm was found for, and when - looked at again a minute on. */
    BlockPos farmMissed;
    long farmMissedAt;
    final Set<String> digSpare = new HashSet<>();
    private int digLeftSeen = -1;
    private long digFellAt;
    /** How long the dig may go without a block dug before its spare hands are put back on it. */
    static final long DIG_STALL_MILLIS = 60_000;

    /**
     * The spare hands, kept to those of {@code hands} while the dig has {@code digLeft} blocks: all put back on it once
     * it's done, or while it's gone {@link #DIG_STALL_MILLIS} with none dug - true then, every hand on it, as many to a
     * strip as it takes: its holders can't reach what's left (in the squad simulation's camp on a slope, its last two
     * blocks stood undug with the spares kept off).
     */
    boolean spareDigTill(int digLeft, long now, java.util.List<String> hands) {
        // Fallen only to a new least: a step put down to reach a block and dug again isn't the dig going anywhere.
        if (digLeftSeen < 0 || digLeft < digLeftSeen) {
            digLeftSeen = digLeft;
            digFellAt = now;
        }
        if (digLeft <= 0) {
            digLeftSeen = -1;
        }
        boolean stalled = digLeft > 0 && now - digFellAt > DIG_STALL_MILLIS;
        if (digLeft <= 0 || stalled) {
            digSpare.clear();
        }
        digSpare.retainAll(hands);
        return stalled;
    }
    /** When the torches still to put down last changed: a plan stuck at a cell or two is done. */
    long torchesChangedAt;
    /** When a staircase nobody could site at {@code stairsFor} is looked for again. */
    long stairsRetryAt;
    /** Who in the squad is on what, followed to completion and checked against the world. */
    final TaskRegister register = new TaskRegister();
    /**
     * Soldiers taken off a task the register found STALLED, by "soldier|task": each goes to its
     * next work, and back to that task after a wait that grows each time it stalls there again.
     */
    final Setbacks<String> relieved =
            new Setbacks<>(RELIEVED_MILLIS, 8 * RELIEVED_MILLIS);

    /** Whether {@code soldier} is off {@code task} for now. */
    boolean relieved(String soldier, String task, long now) {
        return relieved.barred(soldier + "|" + task, now);
    }
    /** The squad's tree farm, while it has one. */
    TreeFarm trees;
    /** The squad's farm, while it has one. */
    FarmPlan farm;
    /** The soldier who is the closed wall's while it has dirt in it, if one is. */
    String wallHand;
    /** The point the farm was sited for: kept while it's ordered there. */
    BlockPos farmFor;
    /** Blocks of the laid-out base still to place, at the last check - empty with none laid out. */
    OptionalInt baseBlocksLeft = OptionalInt.empty();
    /** When a soldier of the squad last worked at its task, or the task was given: what a stall is measured from. */
    long workedAt = Long.MIN_VALUE;
    /** The task {@code workedAt} was measured for: a new task starts the clock again. */
    Objective workedFor;
    /** The squad's Class I at the last SITREP, logged when its rating changes. */
    Subsistence.Status classOne;
    /** The squad's Class IV request as last said, to say it once when it starts and once when it's met. */
    Optional<String> classFour = Optional.empty();
    /** The last progress measured, logged when its state changes. */
    Progress progress;
    /** Where the squad crafts on the surface, sited by the engineer near its objective; null with nowhere to put one. */
    Workshop workshop;
    /** The ground {@code workshop} was sited at: sited again only when the objective moves. */
    BlockPos workshopFor;
    /** The doorway cut to let a torchbearer out of a sealed, dark base - left open until it's back with torches. */
    List<Position> torchDoor;
    /** The base's breaches at the last check - logged when they change. */
    List<Position> breaches;
    Set<CrossLevel.Transfer> crossLeveling = Set.of();
    /** The walls and roof of the base the squad holds, kept off-limits to digging. */
    Set<BlockPos> baseCells = Set.of();
}
