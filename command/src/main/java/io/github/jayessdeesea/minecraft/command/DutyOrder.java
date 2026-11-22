package io.github.jayessdeesea.minecraft.command;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A producer's duty order from its squad leader (docs/echelons/tenets.md: the squad leader owns each producer's duty):
 * its duty, the ground it works and where its yield goes - the OPORD's execution and sustainment paragraphs at the
 * soldier's level. Apart from the directives of {@link WorkOrders}: a producer works its ground until its duty changes. The
 * soldier does the how; nothing else gives it production work. Written from the squad's
 * {@link WorkArea} by {@link FarmDuties#orders} and {@link WoodDuties#orders}.
 */
public record DutyOrder(String soldier, Duty duty, Optional<Box> ground, Optional<Position> dump, Optional<Position> source,
                        Optional<Position> table, int batch) {

    /** An order with no makings named: a harvester's, a woodcutter's, a planter's. */
    public DutyOrder(String soldier, Duty duty, Optional<Box> ground, Optional<Position> dump) {
        this(soldier, duty, ground, dump, Optional.empty(), Optional.empty(), 0);
    }

    /**
     * This order with its makings named by the squad leader (operator, 2026-11-21: the soldier is how; where the makings
     * are, where it's worked and how much is the squad leader's): the chest it draws from, the table it works at, and
     * how many it makes a batch - a baker's wheat chest, the camp's table, its loaves.
     */
    public DutyOrder making(Optional<Position> source, Optional<Position> table, int batch) {
        return new DutyOrder(soldier, duty, ground, dump, source, table, batch);
    }

    /** A producer's duty: the farm's harvest and its baking apart, the wood's felling and its planting apart. */
    public enum Duty { HARVESTER, BAKER, WOODCUTTER, PLANTER }

    public DutyOrder {
        Objects.requireNonNull(duty, "duty");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(dump, "dump");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(table, "table");
        if (batch < 0) {
            throw new IllegalArgumentException("a batch of " + batch);
        }
    }

    /** Whether it works the farm: a harvester or its baker. */
    public boolean farm() {
        return duty == Duty.HARVESTER || duty == Duty.BAKER;
    }

    /**
     * As said down the chain: "harvester over x 6..9, z -4..4, the dump at (3, 64, 2)"; a baker's with its makings,
     * "baker, the dump at (3, 64, 2), drawing from (5, 64, 1), at the table at (1, 64, -2), 8 a batch".
     */
    public String line() {
        return duty.name().toLowerCase(Locale.ROOT)
                + ground.map(g -> String.format(Locale.ROOT, " over x %d..%d, z %d..%d", g.minX(), g.maxX(), g.minZ(), g.maxZ())).orElse("")
                + dump.map(d -> ", the dump at " + at(d)).orElse(", no dump yet")
                + source.map(s -> ", drawing from " + at(s)).orElse(duty == Duty.BAKER && batch > 0 ? ", no chest known holding its makings" : "")
                + table.map(t -> ", at the table at " + at(t)).orElse(duty == Duty.BAKER && batch > 0 ? ", no table standing" : "")
                + (batch > 0 ? ", " + batch + " a batch" : "");
    }

    private static String at(Position p) {
        return "(" + Math.round(Math.floor(p.x())) + ", " + Math.round(Math.floor(p.y())) + ", " + Math.round(Math.floor(p.z())) + ")";
    }
}
