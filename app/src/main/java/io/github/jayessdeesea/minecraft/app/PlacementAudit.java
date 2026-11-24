package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every block a soldier puts above sea level, said and counted by what put it (operator, 2026-11-21: audit when a soldier
 * places a block above sea level and validate it was right - sometimes it's needed to move, sometimes a longer way round
 * would leave nothing lying about). All placing goes through one call, {@link PlaySession#useBlockFace} with a block in
 * hand; this wraps each soldier's session, and says each such use as a {@code [Place]} line - who, where, how far over
 * the sea, what it held and which routine had its hands - and every {@link #SUMMARY_MILLIS} the counts by routine.
 * Every block broken at sea level or over it too ({@link PlaySession#finishDigging}), as a {@code [Dig]} line.
 * Thread-safe: each soldier's tick writes, any reads.
 */
final class PlacementAudit {

    private static final Logger LOG = LogManager.getLogger(PlacementAudit.class);

    /** How often the counts by routine are said. */
    static final long SUMMARY_MILLIS = 10 * 60_000;
    /** What's placed as a block, as opposed to used on one - a hoe, a bucket, seeds, bread. */
    static final Set<ItemClass> BLOCKS = Set.of(ItemClass.COBBLESTONE, ItemClass.DIRT, ItemClass.PLANKS, ItemClass.LOG,
            ItemClass.CRAFTING_TABLE, ItemClass.FURNACE, ItemClass.TORCH, ItemClass.CHEST, ItemClass.LADDER, ItemClass.FENCE,
            ItemClass.FENCE_GATE, ItemClass.SAPLING, ItemClass.WOOL, ItemClass.BED);

    private final java.util.function.LongSupplier clock;
    /** Which routine has each soldier's hands this tick - "route step" while its walk puts one, "none" free. */
    private final Map<String, String> routines = new ConcurrentHashMap<>();
    /** Placements above sea level since the last summary, by routine then item. */
    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    /**
     * The item each soldier's hotbar slot holds by what it sent, ahead of the server's word: a swap into the hotbar is in
     * hand for the next use, while the inventory still holds what was there till the server answers - live (field611),
     * route steps of dirt read as a sapling and a fence. By soldier, then hotbar slot: the item and when it was sent, good
     * for {@link #SENT_MILLIS} - by then the server's answer has the inventory right.
     */
    private final Map<String, Map<Integer, long[]>> sent = new ConcurrentHashMap<>();
    /** How long a swap sent stands for what's in hand, ahead of the server's answer. */
    static final long SENT_MILLIS = 2_000;
    /** The hotbar slot each soldier last selected, by what it sent. */
    private final Map<String, Integer> selected = new ConcurrentHashMap<>();
    /** When the counts were last said - the first placement's time till then. */
    private volatile long summarisedAt = Long.MIN_VALUE;

    PlacementAudit(java.util.function.LongSupplier clock) {
        this(clock, (x, y, z) -> "a block");
    }

    /** What's at a cell, as said in a line: the block's name. */
    interface Named {
        String at(int x, int y, int z);
    }

    private final Named named;
    /** Blocks broken at sea level or over it since the audit began, by routine: who takes what's built apart. */
    private final Map<String, Integer> digs = new ConcurrentHashMap<>();

    /** As {@link #PlacementAudit(java.util.function.LongSupplier)}, each block broken said by {@code named}'s name for it. */
    PlacementAudit(java.util.function.LongSupplier clock, Named named) {
        this.clock = clock;
        this.named = named;
    }

    /**
     * A block {@code soldier} breaks at sea level or over it, said with what it was and which routine had its hands
     * (operator, 2026-11-23: when the anti-entropy runs, know why): live, a farm's fourteen fence posts, a tree farm's
     * torch and a birch's foot went, and nothing said who took them. Under the sea level is the mines', and not said.
     */
    private void broke(String soldier, int x, int y, int z) {
        if (y < SoldierRunner.SEA_LEVEL) {
            return;
        }
        String routine = routines.getOrDefault(soldier, "none");
        digs.merge(routine, 1, Integer::sum);
        LOG.info("[Dig] {} breaks {} at ({}, {}, {}), {} over sea level - {}", soldier, named.at(x, y, z), x, y, z, y - SoldierRunner.SEA_LEVEL, routine);
        loops.cell(soldier, "breaks", x, y, z, routine, clock.getAsLong());
    }

    /** Each soldier's breaks and placings by cell: its report of a loop. */
    private final LoopReports loops = new LoopReports();

    /** The blocks broken at sea level or over it by routine (the audit's test). */
    Map<String, Integer> digs() {
        return new TreeMap<>(digs);
    }

    /** {@code soldier}'s hands this tick: {@code routine}'s, or none. */
    void routine(String soldier, Optional<?> routine) {
        routines.put(soldier, routine.map(Object::toString).orElse("none"));
    }

    /** What each soldier's walk said of the step it's putting: its goal, and the walk round there is or isn't. */
    private final Map<String, String> stepWhy = new ConcurrentHashMap<>();

    /** {@code soldier}'s walk putting a step this tick - no routine has its hands for it - and {@code why}, said with it. */
    void routeStep(String soldier, String why) {
        routines.put(soldier, "route step");
        stepWhy.put(soldier, why);
    }

    /** {@code session}, its block placings above sea level said as {@code soldier}'s, with what {@code inventory} holds in hand. */
    PlaySession wrap(String soldier, PlaySession session, Inventory inventory) {
        return (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(), new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
            if (method.getName().equals("useBlockFace") && args != null && args.length == 4) {
                heard(soldier, (int) args[0], (int) args[1], (int) args[2], (BlockFace) args[3], inventory);
            } else if (method.getName().equals("swapWithHotbar") && args != null && args.length == 4) {
                int slot = (int) args[2], hotbar = (int) args[3];
                Map<Integer, long[]> mine = sent.computeIfAbsent(soldier, k -> new ConcurrentHashMap<>());
                long now = clock.getAsLong();
                long[] before = mine.get(-1 - slot); // a slot swapped from just before: what went there
                int into = before != null && now - before[1] < SENT_MILLIS ? (int) before[0] : inventory.itemAt(slot);
                int out = held(soldier, hotbar, inventory);
                mine.put(hotbar, new long[]{into, now});
                mine.put(-1 - slot, new long[]{out, now});
            } else if (method.getName().equals("finishDigging") && args != null && args.length == 3) {
                broke(soldier, (int) args[0], (int) args[1], (int) args[2]);
            } else if (method.getName().equals("selectHotbarSlot") && args != null && args.length == 1) {
                selected.put(soldier, (int) args[0]);
            }
            try {
                return method.invoke(session, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private void heard(String soldier, int x, int y, int z, BlockFace face, Inventory inventory) {
        int held = held(soldier, selected.getOrDefault(soldier, inventory.heldHotbarSlot()), inventory);
        Optional<ItemClass> item = ItemClassRegistry.classOf(held);
        int cy = y + face.dy;
        if (item.filter(BLOCKS::contains).isEmpty() || cy <= SoldierRunner.SEA_LEVEL) {
            return;
        }
        String routine = routines.getOrDefault(soldier, "none");
        counts.merge(routine + " / " + item.get().name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        LOG.info("[Place] {} puts {} at ({}, {}, {}), {} over sea level - {}{}", soldier, item.get().name().toLowerCase(java.util.Locale.ROOT),
                x + face.dx, cy, z + face.dz, cy - SoldierRunner.SEA_LEVEL, routine,
                routine.equals("route step") ? " " + stepWhy.getOrDefault(soldier, "") : "");
        long now = clock.getAsLong();
        loops.cell(soldier, "puts", x + face.dx, cy, z + face.dz, routine, now);
        if (summarisedAt == Long.MIN_VALUE) {
            summarisedAt = now;
        } else if (now - summarisedAt >= SUMMARY_MILLIS) {
            summarisedAt = now;
            Map<String, Integer> was = new TreeMap<>(counts);
            counts.clear();
            LOG.info("[Place] above sea level in the last {} min, by routine / item: {}", SUMMARY_MILLIS / 60_000, was);
        }
    }

    /**
     * What {@code soldier}'s hotbar slot {@code hotbar} holds: what it swapped there, else what the inventory says. A swap's
     * button past the hotbar is the off hand's (40): read as a hotbar slot, a shield put on failed its soldier's tick.
     */
    private int held(String soldier, int hotbar, Inventory inventory) {
        long[] swapped = sent.getOrDefault(soldier, Map.of()).get(hotbar);
        return swapped != null && clock.getAsLong() - swapped[1] < SENT_MILLIS ? (int) swapped[0] : inventory.itemAt(hotbar > 8 ? Inventory.OFF_HAND : Inventory.HOTBAR_FIRST + hotbar);
    }

    /** The counts by routine / item since the last summary (the audit's test). */
    Map<String, Integer> counts() {
        return new TreeMap<>(counts);
    }
}
