package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.AccessRoute;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.LevelPace;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.MissionEstimate;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Supply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One ground's flatten work: the foreman's plan for the level under way and its crews, the haul road, the workshop and
 * its outposts, the chests, the dig's measures and the chests condemned on it. The keep's, held per ground once several
 * are worked (backlog/named-work.md); the platoon's wood party and each builder's fights, fetches and draws stay in
 * {@link KeepState}. Each field says who writes it and under what lock; {@link #planLock} guards the plan and, by
 * the same object, the wood party. A holder only: what's done with it is still {@link SoldierRunner}'s.
 */
final class FlattenWork {

    /** The foreman's plan for a level: who's on it, their rectangles, and when it was made. */
    record Plan(int level, Set<String> crew, Map<String, List<Box>> boxes, long at) {
    }

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

    /** The keep's smelter, if one is detailed - squad loops write, ticks read. */
    volatile String smelter;

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

    /** The keep's foreman, by name - squad loops. */
    volatile String foreman;
    /** The foreman's plan for the site's level - guarded by {@link #planLock}; read by every squad's loop. */
    volatile Plan plan;
    final Object planLock = new Object();
    /** Each keep squad's builders on hand - on the keep, fed, not fighting - and where they stand: squad loops. */
    final Map<String, List<LevelPlan.Builder>> crews = new ConcurrentHashMap<>();

    /** Each keep squad's cells still to fill, -1 while it's still digging - squad loops write, the site measure reads. */
    final Map<String, Integer> fillLeftBySquad = new ConcurrentHashMap<>();

    /** Each keep squad's blocks still to dig in its share, at its last look - squad loops write, ticks read. */
    final Map<String, Integer> digLeftBySquad = new ConcurrentHashMap<>();

    /** The site's columns' tops in the keep's last scan, for a rise's line: the keep's squad loop writes and reads. */
    volatile Map<Long, Integer> lastScanTops = Map.of();

    /** Chests standing on the ground to flatten, to salvage before they're dug - keep squad loops write, ticks read. */
    volatile Set<BlockPos> condemned = Set.of();
    /** The keep hand salvaging them; null with none - keep squad loops write, ticks read. */
    volatile String salvager;
    /** When the ground to flatten was last looked over for chests, and the site it was. */
    volatile long condemnedAt = Long.MIN_VALUE;
    volatile Flatten condemnedFor;
}
