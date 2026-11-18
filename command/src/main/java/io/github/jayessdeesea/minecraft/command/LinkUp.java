package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bringing a respawned soldier back into the squad the way doctrine brings in a replacement
 * (docs/military-doctrine.md §6): it doesn't walk alone into a fight. While anyone in the squad is
 * in contact it holds at the rally point; once contact clears it links up with the nearest squad
 * member who isn't stuck (reporting CANTCO), and only then takes a formation slot again.
 *
 * <p>Deterministic bookkeeping, no model call:
 * <ul>
 *   <li>A soldier is a <b>replacement</b> when its {@link Condition#deaths()} has gone up since it
 *       last linked up. It linked up when it came within {@link #LINKUP_DISTANCE} of a squad member
 *       who is neither a replacement nor stuck (reporting CANTCO).</li>
 *   <li>The <b>rally point</b> is where the point man last stood with nobody in contact.</li>
 *   <li>Replacements move to the <b>rear</b> of the formation order. If the point man dies, the next
 *       soldier takes point, rather than the whole squad's slots being measured from the respawn
 *       point.</li>
 * </ul>
 *
 * <p>Holds state between calls, so it has one caller: the squad loop.
 */
public final class LinkUp {

    /** Within this horizontal distance of a squad member, a replacement has linked up. */
    public static final double LINKUP_DISTANCE = 6;
    /** How close to the rally point or the squad member a replacement's order asks it to get. */
    static final double APPROACH_TOLERANCE = 3;
    static final double VERTICAL_TOLERANCE = 3;

    private final Map<String, Integer> linkedDeaths = new HashMap<>();
    private Position rallyPoint;

    /** The squad as the echelons above should see it, and the orders each soldier should get. */
    public record Plan(List<SoldierStatus> squad, List<Order> orders) {
    }

    /**
     * The squad in its effective formation order: replacements moved to the rear and reported as
     * {@link Report.LinkingUp}, with the rally point updated. Call once per squad cycle, before the
     * squad's SITREPs go up.
     */
    public List<SoldierStatus> reconstitute(List<SoldierStatus> squad) {
        List<SoldierStatus> formed = new ArrayList<>();
        List<SoldierStatus> replacements = new ArrayList<>();
        for (SoldierStatus soldier : squad) {
            (isReplacement(soldier) ? replacements : formed).add(soldier);
        }
        if (formed.isEmpty()) {
            // The whole squad is respawning: there's nobody to link up with, so it re-forms as it stands.
            squad.forEach(this::markLinked);
            return squad;
        }
        for (SoldierStatus replacement : replacements) {
            if (horizontalDistance(nearest(linkUpCandidates(formed, name -> false), replacement.position()).position(), replacement.position()) <= LINKUP_DISTANCE) {
                markLinked(replacement);
            }
        }
        boolean inContact = formed.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        if (!inContact) {
            rallyPoint = formed.getFirst().position();
        }
        List<SoldierStatus> result = new ArrayList<>(formed);
        for (SoldierStatus replacement : replacements) {
            result.add(isReplacement(replacement)
                    ? new SoldierStatus(replacement.name(), replacement.position(), replacement.order(), new Report.LinkingUp(),
                            replacement.condition())
                    : replacement);
        }
        return List.copyOf(result);
    }

    /**
     * Orders for a squad from {@link #reconstitute}: formation slots for formed soldiers (as
     * {@code formationOrders} already gives them, in the same order), and for each replacement
     * either "hold at the rally point" while the squad is in contact or "close on the nearest squad
     * member" otherwise.
     */
    public List<Order> orders(List<SoldierStatus> reconstituted, List<Order> formationOrders) {
        return orders(reconstituted, formationOrders, name -> false);
    }

    /**
     * As {@link #orders(List, List)}, and never closing on a squad member {@code stranded} - one the
     * recovery drill has, whatever it reports: live, a soldier rejoining its squad closed on the nearest,
     * a builder in a dead end at a mine's bottom reporting the dig it couldn't reach, and stood on the
     * stair above him at "no route".
     */
    public List<Order> orders(List<SoldierStatus> reconstituted, List<Order> formationOrders, java.util.function.Predicate<String> stranded) {
        return orders(reconstituted, formationOrders, stranded, false);
    }

    /**
     * As {@link #orders(List, List, java.util.function.Predicate)}, after dark ({@code dark}) a replacement
     * further than {@link #NIGHT_LINK_REACH} from its squad holds where it respawned till dawn: live (field410),
     * Alice, Liam and Hal, killed at the mine's faces, respawned at the keep and set off across the dark for
     * their squads 110 blocks east, and each was shot on the way - eight dead in the night.
     */
    public List<Order> orders(List<SoldierStatus> reconstituted, List<Order> formationOrders, java.util.function.Predicate<String> stranded,
                              boolean dark) {
        List<SoldierStatus> formed = reconstituted.stream().filter(s -> !(s.report() instanceof Report.LinkingUp)).toList();
        boolean inContact = formed.stream().anyMatch(s -> s.report() instanceof Report.InContact);
        List<Order> orders = new ArrayList<>(formationOrders);
        for (int i = 0; i < reconstituted.size(); i++) {
            SoldierStatus soldier = reconstituted.get(i);
            // Nobody formed to close on - a one-man unit's soldier, respawned - it keeps its own order.
            if (soldier.report() instanceof Report.LinkingUp && !formed.isEmpty()) {
                Position target = inContact && rallyPoint != null ? rallyPoint : nearest(linkUpCandidates(formed, stranded), soldier.position()).position();
                // Killed again where it waited, it waits no more: the spawn's held by what killed it. Live (field421),
                // Alice was shot at the world's spawn three times in two and a half minutes, waiting for dawn there.
                int deaths = soldier.condition().map(Condition::deaths).orElse(0);
                boolean killedWaiting = deaths > linkedDeaths.getOrDefault(soldier.name(), 0) + 1;
                if (dark && !killedWaiting && horizontalDistance(target, soldier.position()) > NIGHT_LINK_REACH) {
                    Position here = soldier.position();
                    orders.set(i, new Order.MoveTo(here.x(), here.y(), here.z(), APPROACH_TOLERANCE, VERTICAL_TOLERANCE, Risk.LOW));
                    continue;
                }
                // A replacement rejoining is never worth more of its health than the squad's own order.
                Risk risk = formationOrders.get(i) instanceof Order.MoveTo slot ? slot.risk() : Risk.LOW;
                orders.set(i, new Order.MoveTo(target.x(), target.y(), target.z(), APPROACH_TOLERANCE, VERTICAL_TOLERANCE, risk));
            }
        }
        return List.copyOf(orders);
    }

    /**
     * Who a replacement may close on: squad members not reporting CANTCO and not {@code stranded} - a
     * stuck soldier is where nobody can get to - or, if every one of them is, the point man.
     */
    private static List<SoldierStatus> linkUpCandidates(List<SoldierStatus> formed, java.util.function.Predicate<String> stranded) {
        List<SoldierStatus> able = formed.stream().filter(s -> !(s.report() instanceof Report.Cantco) && !stranded.test(s.name())).toList();
        return able.isEmpty() ? List.of(formed.getFirst()) : able;
    }

    /** How far a replacement walks to its squad after dark: beyond it, it waits for dawn where it respawned. */
    public static final double NIGHT_LINK_REACH = 32;

    private boolean isReplacement(SoldierStatus soldier) {
        int deaths = soldier.condition().map(Condition::deaths).orElse(0);
        return deaths > linkedDeaths.getOrDefault(soldier.name(), 0);
    }

    private void markLinked(SoldierStatus soldier) {
        linkedDeaths.put(soldier.name(), soldier.condition().map(Condition::deaths).orElse(0));
    }

    private static SoldierStatus nearest(List<SoldierStatus> candidates, Position from) {
        return candidates.stream().min(Comparator.comparingDouble(s -> horizontalDistance(s.position(), from))).orElseThrow();
    }

    private static double horizontalDistance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
