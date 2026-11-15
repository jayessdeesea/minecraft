package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A place the force works from, and how far it's built up, the lodgment's way for every kind of site
 * (backlog/maturity-model.md; FM 3-99's lodgment phases; ATP 3-37.10's construction standards): a
 * level says what a site can do, measured by its standards, never judged. Each kind fills in what a
 * level means there, and a level with no standards of its kind is met as it stands - the keep needs
 * no shelter, being supplied from the camp.
 *
 * @param kind      what sort of site
 * @param name      which one: "the camp", "the keep"
 * @param standards each level's standards, any order
 */
public record Site(Kind kind, String name, List<Standard> standards) {

    public Site {
        standards = List.copyOf(standards);
    }

    /** The kinds of site. */
    public enum Kind { CAMP, MINE_BASE, KEEP, OUTPOST, NETHER_FOOTHOLD }

    /** The lodgment's levels, lowest first. */
    public enum Level {
        /** A squad survives a night here. */
        SHELTER,
        /** Nothing spawns on the ground; walled or sealed; its workers armed. */
        AIRHEAD,
        /** The site makes what its work consumes. */
        BUILDUP,
        /** Stores at their stockage objective; its workers armoured. */
        ESTABLISHED,
        /** Surplus that supplies other sites; laid out to its final plan. */
        SEMI_PERMANENT
    }

    /**
     * One standard of a level: whether it's met, and how it stands in plain words.
     *
     * @param level  the level it's a standard of
     * @param name   what's measured: "the depot stocked"
     * @param met    whether it's met
     * @param status how it stands: "1,846 blocks to dig"
     */
    public record Standard(Level level, String name, boolean met, String status) {
    }

    /**
     * The highest level reached: every standard of it and of every level below it met - up to the
     * highest level the site has standards for, as no site is further on than it's measured. Empty
     * below its first.
     */
    public Optional<Level> level() {
        Level top = standards.stream().map(Standard::level).max(Enum::compareTo).orElse(Level.SHELTER);
        Level reached = null;
        for (Level level : Level.values()) {
            if (level.compareTo(top) > 0 || standards.stream().anyMatch(s -> s.level() == level && !s.met())) {
                break;
            }
            reached = level;
        }
        return Optional.ofNullable(reached);
    }

    /** The level it's working toward: the one above its level, or its first; empty once its highest is met. */
    public Optional<Level> next() {
        Optional<Level> at = level();
        Level top = standards.stream().map(Standard::level).max(Enum::compareTo).orElse(Level.SHELTER);
        if (at.filter(l -> l == top).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(at.map(l -> Level.values()[l.ordinal() + 1]).orElse(Level.SHELTER));
    }

    /** "the keep: AIRHEAD; BUILDUP open: the ground flattened - 1846 blocks to dig" - its level, and what the next lacks. */
    public String line() {
        String open = next().map(n -> "; " + n + " open: " + standards.stream().filter(s -> s.level() == n && !s.met())
                .map(s -> s.name() + " - " + s.status()).collect(Collectors.joining("; "))).orElse("; every level met");
        return name + ": " + level().map(Level::name).orElse("below " + Level.SHELTER) + open;
    }

    /**
     * The camp, from its foothold's phases (docs/military-doctrine.md § 17): 1 SHELTER, 2 AIRHEAD,
     * 3 BUILDUP, 4 ESTABLISHED - the one name for "Armour", "ESTABLISHED" and "Lodgment
     * established" the item asks for.
     */
    public static Site camp(Foothold foothold) {
        return new Site(Kind.CAMP, "the camp", foothold.standards().stream()
                .map(s -> new Standard(Level.values()[Math.max(0, Math.min(Level.values().length - 1, s.phase() - 1))], s.name(), s.met(), s.status()))
                .toList());
    }
}
