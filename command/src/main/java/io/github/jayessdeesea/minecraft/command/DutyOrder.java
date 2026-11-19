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
public record DutyOrder(String soldier, Duty duty, Optional<Box> ground, Optional<Position> dump) {

    /** A producer's duty: the farm's harvest and its baking apart, the wood's felling and its planting apart. */
    public enum Duty { HARVESTER, BAKER, WOODCUTTER, PLANTER }

    public DutyOrder {
        Objects.requireNonNull(duty, "duty");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(dump, "dump");
    }

    /** Whether it works the farm: a harvester or its baker. */
    public boolean farm() {
        return duty == Duty.HARVESTER || duty == Duty.BAKER;
    }

    /** As said down the chain: "harvester over x 6..9, z -4..4, the dump at (3, 64, 2)". */
    public String line() {
        return duty.name().toLowerCase(Locale.ROOT)
                + ground.map(g -> String.format(Locale.ROOT, " over x %d..%d, z %d..%d", g.minX(), g.maxX(), g.minZ(), g.maxZ())).orElse("")
                + dump.map(d -> ", the dump at (" + Math.round(d.x()) + ", " + Math.round(d.y()) + ", " + Math.round(d.z()) + ")").orElse(", no dump yet");
    }
}
