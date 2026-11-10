package io.github.jayessdeesea.minecraft.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * Each of {@code hands}'s command for the ground work round {@code feet}: {@code dig} and
     * {@code fill} the boxes with work left in them (empty when there's none), {@code except} the
     * cells neither touches, {@code gathering} those sent for dirt last cycle.
     */
    public static Map<String, Order> allocate(List<Hand> hands, Cell feet, int radius, java.util.Optional<Box> dig,
                                              java.util.Optional<Box> fill, Set<Cell> except, Set<String> gathering) {
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
