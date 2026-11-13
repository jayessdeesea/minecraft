package io.github.jayessdeesea.minecraft.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The squad's drill for ground work: the engineer's boxes ({@link Groundwork}) handed out as each
 * soldier's concrete command (backlog/echelon-contracts.md). Digging first: while there's any, a
 * strip of the dig box each. Then filling: a strip of the fill box each - but a soldier short of
 * blocks is sent off base first, {@code Gather(SOIL, its borrow pit, LOAD)}, and keeps at it until
 * it carries a full load, so it doesn't walk back for every fourth block. Pure: the caller passes
 * who carries what and which soldiers were gathering last cycle.
 */
public final class Tasking {

    /** Blocks carried below which a soldier filling goes for more. */
    public static final int LOW = 4;
    /** The load it comes back with: a trip's worth, not a walk back for every fourth block. */
    public static final int LOAD = 32;

    private Tasking() {
    }

    /** One soldier as the drill sees it: its name, and the cobblestone and dirt it carries. */
    public record Hand(String name, int blocks) {
    }

    /**
     * Each of {@code hands}'s command for the keep ({@link Keep}): a strip of {@code dig} each while
     * there's any, then a strip of {@code fill} each, in cobblestone only - {@code hands}' blocks
     * counting cobblestone alone. A soldier short of it has no command here: its cobblestone comes
     * from the stockpile, the mine's, which is the caller's to send it for.
     */
    public static Map<String, Order> keep(List<Hand> hands, Optional<Box> dig, Optional<Box> fill, Set<Cell> except) {
        Map<String, Order> orders = new LinkedHashMap<>();
        if (hands.isEmpty()) {
            return orders;
        }
        if (dig.isPresent()) {
            List<Box> strips = dig.get().strips(hands.size());
            for (int i = 0; i < hands.size(); i++) {
                orders.put(hands.get(i).name(), new Order.Dig(strips.get(i % strips.size()), except));
            }
            return orders;
        }
        if (fill.isEmpty()) {
            return orders;
        }
        List<Box> strips = fill.get().strips(hands.size());
        for (int i = 0; i < hands.size(); i++) {
            if (hands.get(i).blocks() >= LOW) {
                orders.put(hands.get(i).name(), new Order.Fill(strips.get(i % strips.size()), except, true));
            }
        }
        return orders;
    }

    /**
     * Each of {@code hands}'s dig of the keep: its own fixed strip of {@code share} - a range of x
     * and z (operator, 2026-11-13) - from its floor up, top down. A strip's floor is two under the
     * highest ground left in it or in the columns just round it ({@link Keep#floor}): neighbouring
     * strips stay within two of each other, so no cliff opens at a strip's edge, while a spire in one
     * holds back only those beside it. A strip with nothing at or over its floor has no command.
     *
     * @param top the highest ground left to dig in column (x, z), or {@link Integer#MIN_VALUE} for none
     */
    public static Map<String, Order> keepDig(List<Hand> hands, Box share, java.util.function.IntBinaryOperator top, Set<Cell> except) {
        Map<String, Order> orders = new LinkedHashMap<>();
        if (hands.isEmpty()) {
            return orders;
        }
        List<Box> strips = share.strips(hands.size());
        for (int i = 0; i < hands.size(); i++) {
            Box strip = strips.get(i % strips.size());
            int own = Integer.MIN_VALUE;
            int round = Integer.MIN_VALUE;
            for (int x = strip.minX() - 1; x <= strip.maxX() + 1; x++) {
                for (int z = strip.minZ() - 1; z <= strip.maxZ() + 1; z++) {
                    int y = top.applyAsInt(x, z);
                    round = Math.max(round, y);
                    if (x >= strip.minX() && x <= strip.maxX() && z >= strip.minZ() && z <= strip.maxZ()) {
                        own = Math.max(own, y);
                    }
                }
            }
            if (own == Integer.MIN_VALUE) {
                continue;
            }
            int floor = Keep.floor(round);
            if (own < floor) {
                continue; // waits on its neighbour's high ground: free for other work
            }
            orders.put(hands.get(i).name(), new Order.Dig(new Box(strip.minX(), floor, strip.minZ(), strip.maxX(), strip.maxY(), strip.maxZ()), except));
        }
        return orders;
    }

    /**
     * Each of {@code hands}'s command for the ground work round {@code feet}: {@code dig} and
     * {@code fill} the boxes with work left in them (empty when there's none), {@code except} the
     * cells neither touches, {@code gathering} those sent for dirt last cycle.
     */
    public static Map<String, Order> allocate(List<Hand> hands, Cell feet, int radius, Optional<Box> dig,
                                              Optional<Box> fill, Set<Cell> except, Set<String> gathering) {
        Map<String, Order> orders = new LinkedHashMap<>();
        if (hands.isEmpty()) {
            return orders;
        }
        if (dig.isPresent()) {
            List<Box> strips = dig.get().strips(hands.size());
            for (int i = 0; i < hands.size(); i++) {
                orders.put(hands.get(i).name(), new Order.Dig(strips.get(i % strips.size()), except));
            }
            return orders;
        }
        if (fill.isEmpty()) {
            return orders;
        }
        List<Box> strips = fill.get().strips(hands.size());
        for (int i = 0; i < hands.size(); i++) {
            Hand hand = hands.get(i);
            boolean fetching = gathering.contains(hand.name()) ? hand.blocks() < LOAD : hand.blocks() < LOW;
            orders.put(hand.name(), fetching
                    ? new Order.Gather(Resource.SOIL, Groundwork.borrowPit(feet, radius, i), LOAD)
                    : new Order.Fill(strips.get(i % strips.size()), except));
        }
        return orders;
    }
}
