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
     * Each of {@code hands}'s command for a soil site's top (Flatten.soil): a strip of {@code fill} each, in
     * dirt only - {@code hands}' blocks counting dirt alone - and one short of dirt sent for a load of it
     * from {@code borrow}, ground to be dug anyway.
     */
    public static Map<String, Order> soil(List<Hand> hands, Optional<Box> fill, Box borrow, Set<Cell> except, Set<String> gathering) {
        Map<String, Order> orders = new LinkedHashMap<>();
        if (hands.isEmpty() || fill.isEmpty()) {
            return orders;
        }
        List<Box> strips = fill.get().strips(hands.size());
        for (int i = 0; i < hands.size(); i++) {
            Hand hand = hands.get(i);
            boolean fetching = gathering.contains(hand.name()) ? hand.blocks() < LOAD : hand.blocks() < LOW;
            orders.put(hand.name(), fetching ? new Order.Gather(Resource.SOIL, borrow, LOAD)
                    : new Order.Fill(strips.get(i % strips.size()), except, Order.Fill.Material.SOIL));
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
        return allocate(hands, feet, radius, dig, fill, except, gathering, (hand, box) -> 0);
    }

    /**
     * As {@link #allocate(List, Cell, int, Optional, Optional, Set, Set)} with {@code everyHand}: every hand on the dig, as
     * many to a strip as it takes - a dig stalled, its holders unable to reach what's left.
     */
    public static Map<String, Order> allocate(List<Hand> hands, Cell feet, int radius, Optional<Box> dig,
                                              Optional<Box> fill, Set<Cell> except, Set<String> gathering, boolean everyHand) {
        if (!everyHand || dig.isEmpty() || hands.isEmpty()) {
            return allocate(hands, feet, radius, dig, fill, except, gathering);
        }
        List<Box> strips = dig.get().strips(hands.size());
        Map<String, Order> orders = new LinkedHashMap<>();
        for (int i = 0; i < hands.size(); i++) {
            orders.put(hands.get(i).name(), new Order.Dig(strips.get(i % strips.size()), except));
        }
        return orders;
    }

    /** As {@link #allocate(List, Cell, int, Optional, Optional, Set, Set)}, {@code far} saying how far each hand is from a strip. */
    public static Map<String, Order> allocate(List<Hand> hands, Cell feet, int radius, Optional<Box> dig,
                                              Optional<Box> fill, Set<Cell> except, Set<String> gathering,
                                              java.util.function.ToDoubleBiFunction<String, Box> far) {
        Map<String, Order> orders = new LinkedHashMap<>();
        if (hands.isEmpty()) {
            return orders;
        }
        // A hand to a strip, never two: a dig narrower than the crew has fewer strips than hands, the strips to the hands
        // {@code far} says are nearest - its holder first - and the rest no share, free for other work (operator,
        // 2026-11-20: spare hands reallocated). Live (field494), the camp's last block was all three of Bravo's share.
        if (dig.isPresent()) {
            List<Box> strips = dig.get().strips(hands.size());
            List<Hand> free = new java.util.ArrayList<>(hands);
            for (Box strip : strips) {
                Hand hand = strips.size() < hands.size() ? free.stream().min(java.util.Comparator.comparingDouble(h -> far.applyAsDouble(h.name(), strip))).orElseThrow()
                        : free.getFirst();
                free.remove(hand);
                orders.put(hand.name(), new Order.Dig(strip, except));
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
