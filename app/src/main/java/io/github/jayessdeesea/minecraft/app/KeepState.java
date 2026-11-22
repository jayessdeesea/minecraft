package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.AccessRoute;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.LevelPace;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.MissionEstimate;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Supply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keep's state, as the runner holds it between ticks and squad loops: the foreman's plan for the level under
 * way and its crews, the haul road, the grove and its wood party, the workshop and its outposts, the chests, and
 * each builder's fights, fetches and draws. Each field says who writes it and under what lock. A holder only:
 * what's done with it is still {@link SoldierRunner}'s (backlog/soldier-state-machine.md).
 */
final class KeepState {

    /** The foreman's plan for a level: who's on it, their rectangles, and when it was made. */
    record Plan(int level, Set<String> crew, Map<String, List<Box>> boxes, long at) {
    }

    /** Keep builders in contact, or out of it less than {@link SoldierRunner#KEEP_FIGHT_MILLIS}: their strips offer the level nothing - squad loops. */
    final Set<String> fighting = ConcurrentHashMap.newKeySet();
    /** When each keep builder was last in contact - squad loops. */
    final Map<String, Long> foughtAt = new ConcurrentHashMap<>();

    /** Where each keep builder last fought - squad loops. */
    final Map<String, Position> fightingAt = new ConcurrentHashMap<>();

    /** The keep's work left as measured, for its rate - the main loop's. */
    final Supply.Burn workBurn = new Supply.Burn(SoldierRunner.DAY_MILLIS);
    /** The keep's estimate as last worked out, and whether it was feasible when last logged - the main loop's. */
    volatile MissionEstimate estimate;
    Boolean feasible;

    /** Ore on the keep's site no builder's pickaxe gets the drop from, by squad - each squad's loop writes its own. */
    final Map<String, Integer> oreAsideBySquad = new ConcurrentHashMap<>();

    /** The keep's workshop, at its depot, shared by every squad building it - squad loop only. */
    volatile Workshop workshop;
    /**
     * The flatten's other depots, along its edge (Flatten.depots): where its builders put their
     * loads in, the nearest of them or the main one - squad loops write, ticks read. Draws are from
     * the main depot, whose chests hold the stores.
     */
    volatile List<Workshop> outposts = List.of();

    /** The depot ground last said to have nowhere for the workshop, so it's said once - squad loops. */
    volatile BlockPos workshopNowhere;

    /** The cells of the keep's depot's chests, in the order they're filled - squad loops write, ticks read. */
    volatile List<BlockPos> chests = List.of();
    /** Which of {@link #chests} is being filled. */
    volatile int chestAt;

    /** The keep's haul road as last planned, when, and who cuts it - guarded by {@link #planLock}. */
    volatile AccessRoute road;
    long roadAt = Long.MIN_VALUE / 2;
    volatile String roadCutter;

    /** Each squad's hands free for the haul road, by squad - the squad loop's. */
    final Map<String, Map<String, Position>> roadHands = new ConcurrentHashMap<>();

    /** Each squad's hands on the keep and where they stand, as its last keep cycle saw them - the squad loop's. */
    final Map<String, Map<String, Position>> handsAt = new ConcurrentHashMap<>();

    /** Soldiers whose dig or fill is the keep's, not the camp's - squad loop writes, tick reads. */
    final Set<String> hands = ConcurrentHashMap.newKeySet();

    /**
     * When each builder's rectangles on the level under way are due done, and only while they're not:
     * a builder dealt in with none here is done - written under {@link #planLock}, read by ticks.
     */
    final Map<String, Long> due = new ConcurrentHashMap<>();

    /** Each of the ground's columns' top as last seen loaded, by packed x and z - squad loops, under its own lock. */
    final Map<Long, Integer> columnTops = new HashMap<>();
    /** Ore in the dig whose rectangle's holder can't take it, by squad as each last saw it: the ore errand's too. */
    final Map<String, Set<Cell>> oreBeyondHolder = new ConcurrentHashMap<>();
    /** The builders given work on the level under way, done or not - guarded by {@link #planLock}. */
    final Set<String> dealt = new HashSet<>();

    /** When the keep last said why nobody cuts - squad loops only. */
    volatile long woodWhyAt = Long.MIN_VALUE / 2;

    /** When a keep squad last saw trees - the squad loop writes and reads it. */
    volatile long treesSeenAt = Long.MIN_VALUE / 2;

    /** The keep's smelter, if one is detailed - squad loops write, ticks read. */
    volatile String smelter;

    /** The stump each keep cutter replants before its next tree - squad loops write, the planting map reads. */
    final Map<String, BlockPos> stumps = new ConcurrentHashMap<>();
    final Map<String, Long> stumpSince = new ConcurrentHashMap<>();
    /** Where each keep cutter's felled logs fell, and when it went to pick them up - squad loops only. */
    final Map<String, BlockPos> collect = new ConcurrentHashMap<>();
    final Map<String, Long> collectSince = new ConcurrentHashMap<>();

    /** The ground the wood party clears for the keep's grove, while it has none: its trees cut first. */
    volatile BlockPos groveToClear;
    /** When the keep's grove was last sought. */
    volatile long groveSoughtAt;

    /** The tree {@link #knownTree} last found, and when it looked - squad loops only. */
    volatile Position knownTree;
    volatile long knownTreeAt = Long.MIN_VALUE / 2;

    /** Each keep builder's digs on the level under way (command.LevelPace) - ticks write, squad loops read and clear. */
    final Map<String, LevelPace.Tally> pace = new ConcurrentHashMap<>();
    /**
     * Each keep builder's ticks on the level under way, by what held them - the routine holding its
     * hands, or waiting with no rectangle (command.LevelPace) - its own tick writes, squad loops take.
     */
    final Map<String, Map<String, Integer>> spent = new ConcurrentHashMap<>();
    /** The keep's dig so far, level on level - guarded by {@link #planLock}. */
    LevelPace.Run run = LevelPace.Run.NONE;
    /** When the level under way was first planned, the nights since left out - guarded by {@link #planLock}. */
    long levelAt;
    /** Since when it's been dark over the level under way, or {@code Long.MIN_VALUE} by day - guarded by {@link #planLock}. */
    long darkSince = Long.MIN_VALUE;
    /** The keep's grove, once sited - squad loops write, ticks read. */
    volatile TreeFarm grove;
    /** Whether the keep's grove has been looked for since the start: once, whatever it found. */
    volatile boolean groveSought;
    /** The keep's wood party, as the platoon sergeant last decided it - guarded by {@link #planLock}, read by ticks. */
    volatile PlatoonSergeant.WoodParty party = PlatoonSergeant.WoodParty.NONE;
    /** When the keep's grove, its last planting failed, is planted again - guarded by {@link #planLock}. */
    long groveBarredUntil = Long.MIN_VALUE;
    /** The saplings each keep builder carries, at its squad's last look - squad loops. */
    final Map<String, Integer> saplingsCarried = new ConcurrentHashMap<>();
    /** The logs each keep builder carries, at its squad's last look - squad loops. */
    final Map<String, Integer> logsCarried = new ConcurrentHashMap<>();

    /** The keep's foreman, by name - squad loops. */
    volatile String foreman;
    /** The foreman's plan for the site's level - guarded by {@link #planLock}; read by every squad's loop. */
    volatile Plan plan;
    final Object planLock = new Object();
    /** Each keep squad's builders on hand - on the keep, fed, not fighting - and where they stand: squad loops. */
    final Map<String, List<LevelPlan.Builder>> crews = new ConcurrentHashMap<>();

    /** How many times each squad's keep borrow pit has moved on, and when it last did - squad loop only. */
    final Map<String, Integer> pitShift = new ConcurrentHashMap<>();

    final Map<String, Long> pitMovedAt = new ConcurrentHashMap<>();

    /** Builders on the keep fetching dirt to climb on - squad loop only. */
    final Set<String> fetching = ConcurrentHashMap.newKeySet();

    /** Soldiers on the keep sent to the stockpile for cobblestone - squad loop writes, tick reads. */
    final Set<String> drawing = ConcurrentHashMap.newKeySet();
    /** Builders on the keep drawing cobblestone from the depot to climb on - squad loop writes, tick reads. */
    final Set<String> climbDrawing = ConcurrentHashMap.newKeySet();

    /** Each keep squad's cells still to fill, -1 while it's still digging - squad loops write, the site measure reads. */
    final Map<String, Integer> fillLeftBySquad = new ConcurrentHashMap<>();

    /** Each keep squad's blocks still to dig in its share, at its last look - squad loops write, ticks read. */
    final Map<String, Integer> digLeftBySquad = new ConcurrentHashMap<>();
}
