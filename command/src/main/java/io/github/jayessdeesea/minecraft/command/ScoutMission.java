package io.github.jayessdeesea.minecraft.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The operator's "look here for {animal} and bring them back to {location}" (operator, 2026-11-25): one squad's task,
 * its soldiers each a sector of the ground round the place ({@link ScoutSectors}). Its end is its squad's to say:
 * what was brought, of what was found, and where the land ends - an animal isn't led over water.
 *
 * @param animal  the kind looked for
 * @param place   the middle of the ground searched; its y is the level the order was given at, not the ground's
 * @param bringTo where they're wanted
 */
public record ScoutMission(Animal animal, Position place, Position bringTo) {

    /** How far out from the place, each way, the squad looks. */
    public static final double RADIUS = 32;

    /** The kinds a squad is sent for: each led by a food the force grows. */
    public enum Animal {
        CHICKEN, SHEEP, COW;

        /** The kind {@code said} names, whatever its case: empty for any other. */
        public static Optional<Animal> named(String said) {
            return Arrays.stream(values()).filter(a -> said != null && a.name().equalsIgnoreCase(said.strip())).findFirst();
        }

        /** As said down the chain: "sheep". */
        public String said() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ScoutMission {
        Objects.requireNonNull(animal, "animal");
        Objects.requireNonNull(place, "place");
        Objects.requireNonNull(bringTo, "bringTo");
    }

    /** The mission as given and kept: "scout for sheep round (120, -40), bring them to (8, 40)". */
    public String name() {
        return String.format(Locale.ROOT, "scout for %s round (%d, %d), bring them to (%d, %d)", animal.said(), (int) Math.floor(place.x()),
                (int) Math.floor(place.z()), (int) Math.floor(bringTo.x()), (int) Math.floor(bringTo.z()));
    }

    /** The objective its squad is given: the place, and the task that carries the mission. */
    public Objective objective() {
        return new Objective(name(), place, "The operator's: " + animal.said() + " for the force's farms, led in on their food",
                new Objective.Task.Scout(this));
    }
}
