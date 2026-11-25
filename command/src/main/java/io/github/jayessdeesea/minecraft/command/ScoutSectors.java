package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;

/**
 * A squad split to scout round a place: the square of {@code radius} round it cut into one rectangle a soldier, none
 * overlapping (docs/echelons/tenets.md, work in parallel: soldiers' ground is disjoint), so the squad covers every
 * direction at once and no two soldiers find the same animal. Two columns, west and east of the place, each cut north
 * to south into as many rows as the soldiers need; one soldier has the whole square.
 */
public final class ScoutSectors {

    private ScoutSectors() {
    }

    /** One soldier's ground, in x and z. */
    public record Sector(double minX, double minZ, double maxX, double maxZ) {

        /**
         * Whether ({@code x}, {@code z}) is in it: its west and north edges are its own, its east and south the next
         * one's - the square's own east and south edge are nobody's, a line with no ground in it.
         */
        public boolean holds(double x, double z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        /**
         * The legs a soldier walks to look it over from {@code place}: the middles of its four quarters, the one
         * nearest the place first and round from there - so no point of it is more than a quarter's diagonal from a
         * leg's end, whatever its shape.
         */
        public List<Position> legs(Position place) {
            double w = (maxX - minX) / 4, d = (maxZ - minZ) / 4;
            List<Position> round = List.of(new Position(minX + w, place.y(), minZ + d), new Position(maxX - w, place.y(), minZ + d),
                    new Position(maxX - w, place.y(), maxZ - d), new Position(minX + w, place.y(), maxZ - d));
            int first = 0;
            for (int i = 1; i < round.size(); i++) {
                if (Math.hypot(round.get(i).x() - place.x(), round.get(i).z() - place.z())
                        < Math.hypot(round.get(first).x() - place.x(), round.get(first).z() - place.z())) {
                    first = i;
                }
            }
            List<Position> legs = new ArrayList<>(round.subList(first, round.size()));
            legs.addAll(round.subList(0, first));
            return List.copyOf(legs);
        }
    }

    /** The sectors for {@code soldiers} of a squad looking {@code radius} out round {@code place}, in the squad's order. */
    public static List<Sector> of(Position place, double radius, int soldiers) {
        if (soldiers < 1) {
            return List.of();
        }
        if (soldiers == 1) {
            return List.of(new Sector(place.x() - radius, place.z() - radius, place.x() + radius, place.z() + radius));
        }
        int west = (soldiers + 1) / 2, east = soldiers / 2;
        List<Sector> sectors = new ArrayList<>(soldiers);
        column(sectors, place.x() - radius, place.x(), place.z() - radius, place.z() + radius, west);
        column(sectors, place.x(), place.x() + radius, place.z() - radius, place.z() + radius, east);
        return List.copyOf(sectors);
    }

    /** {@code rows} sectors north to south between two lines of x: each one's south edge the next one's north, the last the square's. */
    private static void column(List<Sector> into, double minX, double maxX, double north, double south, int rows) {
        double edge = north;
        for (int row = 1; row <= rows; row++) {
            double next = row == rows ? south : north + (south - north) * row / rows;
            into.add(new Sector(minX, edge, maxX, next));
            edge = next;
        }
    }
}
