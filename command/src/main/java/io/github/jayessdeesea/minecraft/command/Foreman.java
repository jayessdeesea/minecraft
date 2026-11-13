package io.github.jayessdeesea.minecraft.command;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The keep's foreman (operator, 2026-11-13): one soldier, appointed by its squad leader - the
 * first builder of the first squad on the keep - who runs the flattening level by level over
 * the net. Each builder tells it its strip's top, the highest block left to dig in its own range of
 * x and z, and the highest along each of its four edges; it clears each builder to dig down to a
 * floor a step under its own top and under its neighbours' edges facing it, so where two strips
 * meet they're never more than a step apart - the step a soldier walks up - while a spire inside
 * a strip holds back no one but its own builder. A builder digs only once cleared, and only down
 * to its floor.
 *
 * <p>Decided only from what it's been told: the reports are the net's, point to point
 * ({@code /msg}), and the world - the builder's own strip - is what each report says. Plain Java,
 * never a model: the squad leader appoints, the foreman paces.
 */
public final class Foreman {

    /**
     * What a builder says: its strip's top, and its edges' west, east, north and south - {@code KEEP
     * TOP 88 EDGES 85 87 88 84}, or {@code NONE} for any with nothing left.
     */
    public static final String TOP = "KEEP TOP ";
    static final String EDGES = " EDGES ";
    /** What the foreman says: a builder's floor - {@code KEEP CLEAR TO 87}. */
    public static final String CLEAR = "KEEP CLEAR TO ";
    /** A strip with nothing left to dig. */
    public static final int NONE = Integer.MIN_VALUE;
    /** How long a builder goes without saying its top again, though it hasn't changed: a line lost on the net is made up. */
    public static final long REPEAT_MILLIS = 30_000;

    /** A line on the foreman's net. */
    public sealed interface Signal permits Top, Clear {
        /** As it's said on the net. */
        String text();
    }

    /** A builder's strip's top, and its edges' tops - west, east, north, south - each or {@link #NONE}. */
    public record Top(int top, int west, int east, int north, int south) implements Signal {

        /** A strip whose edges are as high as its top: the most it can hold its neighbours back. */
        public Top(int top) {
            this(top, top, top, top, top);
        }

        @Override
        public String text() {
            return TOP + say(top) + EDGES + say(west) + " " + say(east) + " " + say(north) + " " + say(south);
        }

        private static String say(int y) {
            return y == NONE ? "NONE" : String.valueOf(y);
        }
    }

    /** A builder cleared down to {@code floor}. */
    public record Clear(int floor) implements Signal {
        @Override
        public String text() {
            return CLEAR + floor;
        }
    }

    /** The line {@code text} heard on the net, if it's one of the foreman's. */
    public static Optional<Signal> parse(String text) {
        String t = text.strip();
        try {
            if (t.startsWith(TOP)) {
                String[] v = t.substring(TOP.length()).strip().split("\\s+");
                if (v.length == 1) {
                    return Optional.of(new Top(height(v[0])));
                }
                if (v.length == 6 && v[1].equals(EDGES.strip())) {
                    return Optional.of(new Top(height(v[0]), height(v[2]), height(v[3]), height(v[4]), height(v[5])));
                }
                return Optional.empty();
            }
            if (t.startsWith(CLEAR)) {
                return Optional.of(new Clear(Integer.parseInt(t.substring(CLEAR.length()).strip())));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static int height(String said) {
        return said.equals("NONE") ? NONE : Integer.parseInt(said);
    }

    private final Map<String, Top> tops = new HashMap<>();
    private final Map<String, Integer> cleared = new HashMap<>();

    /** {@code soldier} said its strip's top. */
    public void heard(String soldier, Top top) {
        tops.put(soldier, top);
    }

    /** {@code soldier} is off the keep: neither its top nor its floor stands. */
    public void forget(String soldier) {
        tops.remove(soldier);
        cleared.remove(soldier);
    }

    /** The top {@code soldier} last said, if it has. */
    public Optional<Integer> topOf(String soldier) {
        return Optional.ofNullable(tops.get(soldier)).map(Top::top);
    }

    /**
     * The clearances to send now, given each builder's {@code strips}: a floor a step under its own
     * top and under each neighbour's edge facing it ({@link Keep#floor}), for each builder that has
     * said its top and whose floor isn't the one it was last sent. A builder beside one that hasn't
     * spoken yet waits on it: that neighbour's ground isn't known. Live, held to its neighbours'
     * highest ground anywhere, every builder but the one with the keep's top block stood waiting.
     */
    public Map<String, Clear> decide(Map<String, Box> strips) {
        Map<String, Clear> out = new LinkedHashMap<>();
        for (Map.Entry<String, Box> e : strips.entrySet()) {
            String soldier = e.getKey();
            Top own = tops.get(soldier);
            if (own == null || own.top() == NONE) {
                continue;
            }
            int round = own.top();
            boolean known = true;
            for (Map.Entry<String, Box> n : strips.entrySet()) {
                if (n.getKey().equals(soldier) || !touching(e.getValue(), n.getValue())) {
                    continue;
                }
                Top theirs = tops.get(n.getKey());
                if (theirs == null) {
                    known = false;
                } else {
                    int facing = facing(e.getValue(), n.getValue(), theirs);
                    if (facing != NONE) {
                        round = Math.max(round, facing);
                    }
                }
            }
            if (!known) {
                continue;
            }
            int floor = Keep.floor(round);
            if (!Objects.equals(cleared.get(soldier), floor)) {
                cleared.put(soldier, floor);
                out.put(soldier, new Clear(floor));
            }
        }
        return out;
    }

    /**
     * The top of neighbour {@code b}'s edge facing strip {@code a}, from what {@code b} said: its west
     * edge for a strip to its west, and so on - at a corner, the higher of the two edges there.
     */
    static int facing(Box a, Box b, Top theirs) {
        int y = NONE;
        if (b.maxX() < a.minX()) {
            y = Math.max(y, theirs.east());
        } else if (b.minX() > a.maxX()) {
            y = Math.max(y, theirs.west());
        }
        if (b.maxZ() < a.minZ()) {
            y = Math.max(y, theirs.south());
        } else if (b.minZ() > a.maxZ()) {
            y = Math.max(y, theirs.north());
        }
        return y;
    }

    /** Whether strips {@code a} and {@code b} share an edge or a corner: neighbours, a column apart at most. */
    static boolean touching(Box a, Box b) {
        return a.minX() - 1 <= b.maxX() && b.minX() <= a.maxX() + 1 && a.minZ() - 1 <= b.maxZ() && b.minZ() <= a.maxZ() + 1;
    }

    /**
     * A builder's side of the net: when to say its strip's top - when it changes, and every
     * {@link #REPEAT_MILLIS} though it doesn't.
     */
    public static final class Reporter {
        private Top said;
        private long saidAt;

        /** The line to send now for a strip whose top is {@code top}, at {@code now}, if any. */
        public Optional<Top> report(int top, long now) {
            return report(new Top(top), now);
        }

        /** The line to send now for a strip as {@code top} says, at {@code now}, if any. */
        public Optional<Top> report(Top top, long now) {
            if (top.equals(said) && now - saidAt < REPEAT_MILLIS) {
                return Optional.empty();
            }
            said = top;
            saidAt = now;
            return Optional.of(top);
        }
    }
}
