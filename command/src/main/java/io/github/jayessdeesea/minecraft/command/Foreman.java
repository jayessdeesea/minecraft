package io.github.jayessdeesea.minecraft.command;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The keep's foreman (operator, 2026-11-13): one soldier, appointed by its squad leader - the
 * first builder of the first squad on the keep - who runs the flattening level by level over
 * the net, in lock step (operator, 2026-11-14). Each builder tells it its strip's top, the highest
 * block left to dig in its own range of x and z; the site's level is the highest any of them said,
 * and every builder is cleared to that level and no lower. Only the strips with ground at the level
 * dig, and only that level; when every strip is under it the level falls. A block put into a strip
 * already done is that strip's top again, and is cleared before anyone goes down; and with the
 * whole site at one height, nobody climbs over a neighbour's ground. A builder digs only once
 * cleared, and only down to its floor.
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
     * The clearances to send now, given each builder's {@code strips}: the site's level - the highest
     * top said - for each builder with ground left whose floor isn't the one it was last sent. Nothing
     * until every builder has said its top.
     */
    public Map<String, Clear> decide(Map<String, Box> strips) {
        Map<String, Clear> out = new LinkedHashMap<>();
        // Lock step (operator, 2026-11-14): one level for the whole site - the highest top any
        // builder has said - and every builder cleared to that level and no lower. Only the strips
        // with ground at the level dig, and only that level; the rest wait, and a block put into a
        // strip already done is that strip's top again and is cleared before anyone goes down. The
        // level falls when every builder's top is under it, so the site comes down as one flat
        // floor and nobody climbs over a neighbour's ground. Nothing is decided until every builder
        // has spoken: a strip not heard from may hold the highest ground.
        if (strips.isEmpty() || !tops.keySet().containsAll(strips.keySet())) {
            return out;
        }
        int level = strips.keySet().stream().mapToInt(s -> tops.get(s).top()).max().orElse(NONE);
        if (level == NONE) {
            return out;
        }
        int floor = Math.max(Keep.BOTTOM, level);
        for (String soldier : strips.keySet()) {
            if (tops.get(soldier).top() != NONE && !Objects.equals(cleared.get(soldier), floor)) {
                cleared.put(soldier, floor);
                out.put(soldier, new Clear(floor));
            }
        }
        return out;
    }

    /** The site's level, as last decided for {@code soldier}: the floor it was cleared to. */
    public Optional<Integer> clearedTo(String soldier) {
        return Optional.ofNullable(cleared.get(soldier));
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
