package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The keep's state that is not one ground's, as the runner holds it between ticks and squad loops: the grove and its
 * wood party, each builder's fights, fetches and draws. The wood party is guarded by {@link FlattenWork#planLock}.
 * Each field says who writes it and under what lock. A holder only: what's done with it is still
 * {@link SoldierRunner}'s (backlog/soldier-state-machine.md).
 */
final class KeepState {

    /** Keep builders in contact, or out of it less than {@link SoldierRunner#KEEP_FIGHT_MILLIS}: their strips offer the level nothing - squad loops. */
    final Set<String> fighting = ConcurrentHashMap.newKeySet();
    /** When each keep builder was last in contact - squad loops. */
    final Map<String, Long> foughtAt = new ConcurrentHashMap<>();

    /** Where each keep builder last fought - squad loops. */
    final Map<String, Position> fightingAt = new ConcurrentHashMap<>();

    /** When the keep last said why nobody cuts - squad loops only. */
    volatile long woodWhyAt = Long.MIN_VALUE / 2;

    /** When a keep squad last saw trees - the squad loop writes and reads it. */
    volatile long treesSeenAt = Long.MIN_VALUE / 2;

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
    /** The keep's grove, once sited - squad loops write, ticks read. */
    volatile TreeFarm grove;
    /** Whether the keep's grove has been looked for since the start: once, whatever it found. */
    volatile boolean groveSought;
    /** The keep's wood party, as the platoon sergeant last decided it - guarded by {@link FlattenWork#planLock}, read by ticks. */
    volatile PlatoonSergeant.WoodParty party = PlatoonSergeant.WoodParty.NONE;
    /** When the keep's grove, its last planting failed, is planted again - guarded by {@link FlattenWork#planLock}. */
    long groveBarredUntil = Long.MIN_VALUE;
    /** The saplings each keep builder carries, at its squad's last look - squad loops. */
    final Map<String, Integer> saplingsCarried = new ConcurrentHashMap<>();
    /** The logs each keep builder carries, at its squad's last look - squad loops. */
    final Map<String, Integer> logsCarried = new ConcurrentHashMap<>();

    /** How many times each squad's keep borrow pit has moved on, and when it last did - squad loop only. */
    final Map<String, Integer> pitShift = new ConcurrentHashMap<>();

    final Map<String, Long> pitMovedAt = new ConcurrentHashMap<>();

    /** Builders on the keep fetching dirt to climb on - squad loop only. */
    final Set<String> fetching = ConcurrentHashMap.newKeySet();

    /** Soldiers on the keep sent to the stockpile for cobblestone - squad loop writes, tick reads. */
    final Set<String> drawing = ConcurrentHashMap.newKeySet();
    /** Builders on the keep drawing cobblestone from the depot to climb on - squad loop writes, tick reads. */
    final Set<String> climbDrawing = ConcurrentHashMap.newKeySet();
}
