package io.github.jayessdeesea.minecraft.command;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The least and the most the force keeps of each {@link Renewable} (operator, 2026-11-18;
 * docs/echelons/tenets.md, stock levels drive production). Below its least, a kind is short and a squad
 * is put on producing it; at its most it's taken off; in between, a squad on it stays on it. A value,
 * so the levels can change with what the force is getting ready for - more rations before an external
 * mission, higher leasts as the force grows - by handing on another ({@link #with}).
 */
public record ProductionLevels(Map<Renewable, StockLevel> levels) {

    /** A kind's least and most. */
    public record StockLevel(int least, int most) {
        public StockLevel {
            if (least < 0 || most < least) {
                throw new IllegalArgumentException("a least of " + least + " and a most of " + most);
            }
        }
    }

    public ProductionLevels {
        if (!levels.keySet().containsAll(EnumSet.allOf(Renewable.class))) {
            throw new IllegalArgumentException("levels for " + levels.keySet() + ", not every renewable");
        }
        levels = Collections.unmodifiableMap(new EnumMap<>(levels));
    }

    /** The standing levels (operator, 2026-11-18). */
    public static final ProductionLevels STANDING = standing();

    private static ProductionLevels standing() {
        Map<Renewable, StockLevel> levels = new EnumMap<>(Renewable.class);
        // 256 in the general stockpile, and 256 in each farm's own chest (operator, 2026-11-20).
        levels.put(Renewable.SEEDS, new StockLevel(64, 256));
        levels.put(Renewable.WHEAT, new StockLevel(64, 1024));
        levels.put(Renewable.BREAD, new StockLevel(64, 1024));
        levels.put(Renewable.SAPLINGS, new StockLevel(4, 64));
        levels.put(Renewable.LOGS, new StockLevel(64, 1024));
        return new ProductionLevels(levels);
    }

    /** {@code kind}'s level. */
    public StockLevel of(Renewable kind) {
        return levels.get(kind);
    }

    /** These levels with {@code kind}'s set to {@code level}. */
    public ProductionLevels with(Renewable kind, StockLevel level) {
        Map<Renewable, StockLevel> changed = new EnumMap<>(levels);
        changed.put(kind, level);
        return new ProductionLevels(changed);
    }

    /** {@code stock} said against these levels, a short one marked: "bread 12 (64-1024) SHORT, wheat 30 (64-1024), ...". */
    public String describe(Stockpile stock) {
        StringBuilder out = new StringBuilder();
        for (Renewable kind : Renewable.values()) {
            StockLevel level = of(kind);
            out.append(out.isEmpty() ? "" : ", ").append(kind.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(stock.count(kind))
                    .append(" (").append(level.least()).append('-').append(level.most()).append(')')
                    .append(stock.count(kind) < level.least() ? " SHORT" : "");
        }
        return out.toString();
    }

    /** The kinds {@code stock} holds less than their least of. */
    public Set<Renewable> short_(ToIntFunction<Renewable> stock) {
        Set<Renewable> short_ = EnumSet.noneOf(Renewable.class);
        for (Renewable kind : Renewable.values()) {
            if (stock.applyAsInt(kind) < of(kind).least()) {
                short_.add(kind);
            }
        }
        return short_;
    }

    /**
     * The kinds to produce: each short, and each {@code producing} now that's still under its most - so a
     * squad put on a kind stays on it until it's stocked, not until it's just over its least.
     */
    public Set<Renewable> wanted(ToIntFunction<Renewable> stock, Set<Renewable> producing) {
        Set<Renewable> wanted = short_(stock);
        for (Renewable kind : producing) {
            if (stock.applyAsInt(kind) < of(kind).most()) {
                wanted.add(kind);
            }
        }
        return wanted;
    }
}
