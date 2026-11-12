package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-leveling within the squad (docs/military-doctrine.md §13): a soldier that needs logs for a
 * tool and can't chop them, food and is running low, or a weapon and is bare-handed, gets them from the
 * nearest squadmate with some to spare. This drill only pairs them; walking over and handing them across is the soldiers'
 * ({@code bot.supply}).
 *
 * <p>Pure and stateless like {@link Resupply}. A squad in contact doesn't cross-level - the fight
 * comes first, as it does for rearming.
 */
public final class CrossLevel {

    /** Logs a giver keeps for itself: enough for its own next pickaxe. */
    public static final int KEEP_LOGS = 3;
    /** Rations a giver keeps for itself: below this it has none to spare (the operator's). */
    public static final int KEEP_RATIONS = 15;
    /**
     * Rations handed across at a time: one, so a giver spreads what it has over everyone short of
     * it, fewest first, rather than unloading on the first (the operator's).
     */
    public static final int RATIONS_GIVEN = 1;
    /**
     * Health at or under which a starving soldier is one hit from death: it gets a meal from any
     * squadmate carrying one, its keep or not - buddy aid, before anything else is handed across.
     */
    public static final double CRITICAL_HEALTH = 6;

    /** What's handed across. */
    public enum Item {
        LOGS, RATIONS, WEAPON;

        /** How many of it {@code soldier} has to give and keep - for a weapon, only its spares. */
        int carried(SoldierStatus soldier) {
            return soldier.condition().flatMap(Condition::kit).map(k -> switch (this) {
                case LOGS -> k.logs();
                case RATIONS -> k.rations();
                case WEAPON -> k.spareWeapons();
            }).orElse(0);
        }

        int kept() {
            return switch (this) {
                case LOGS -> KEEP_LOGS;
                case RATIONS -> KEEP_RATIONS;
                case WEAPON -> 0; // a spare is spare: the giver keeps its best
            };
        }
    }

    /** {@code giver} hands {@code count} of {@code item} to {@code receiver}. */
    public record Transfer(String giver, String receiver, Item item, int count) {
    }

    private CrossLevel() {
    }

    /**
     * Who gives to whom: each soldier in {@code logsWanted} (by name, the logs it is short) is paired
     * with the nearest squadmate whose logs beyond {@link #KEEP_LOGS} cover the shortfall. A giver
     * gives to one soldier at a time; a soldier no one can cover is left out.
     */
    public static List<Transfer> pair(List<SoldierStatus> squad, Map<String, Integer> logsWanted) {
        return pair(squad, logsWanted, Map.of());
    }

    /**
     * As {@link #pair(List, Map)}, logs first, then {@link #RATIONS_GIVEN} rations to each soldier in
     * {@code rationsWanted} (by name), the one carrying fewest first, from the nearest squadmate with
     * that many beyond {@link #KEEP_RATIONS}: one running low itself still gives to one with fewer, as
     * long as it's left with no fewer than it gave to. A soldier is in one transfer at a time, giving or receiving.
     */
    public static List<Transfer> pair(List<SoldierStatus> squad, Map<String, Integer> logsWanted, Map<String, Integer> rationsWanted) {
        return pair(squad, logsWanted, rationsWanted, Map.of());
    }

    /**
     * As {@link #pair(List, Map, Map)}, a spare weapon first to each soldier in {@code weaponsWanted}
     * - bare-handed - from the nearest squadmate carrying one beyond its best.
     */
    public static List<Transfer> pair(List<SoldierStatus> squad, Map<String, Integer> logsWanted, Map<String, Integer> rationsWanted,
                                      Map<String, Integer> weaponsWanted) {
        if (squad.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return List.of();
        }
        List<Transfer> transfers = new ArrayList<>();
        Set<String> busy = new HashSet<>();
        buddyAid(squad, busy, transfers);
        pair(squad, Item.WEAPON, weaponsWanted, busy, transfers);
        pair(squad, Item.LOGS, logsWanted, busy, transfers);
        pair(squad, Item.RATIONS, rationsWanted, busy, transfers);
        return List.copyOf(transfers);
    }

    /**
     * Rations across squads, for those {@code short_} their own squad didn't pair: each from the
     * nearest of {@code givers} within {@link #ACROSS_REACH} - soldiers of other squads, free of their own squad's cross-leveling -
     * with {@link #RATIONS_GIVEN} beyond {@link #KEEP_RATIONS} and left with more than the receiver
     * gets to. Fewest first, one transfer a giver. Neither side in contact or linking up: the caller's
     * to leave out a squad in contact, as {@link #pair} does its own.
     */
    /** How near another squad's giver must be: a squad on its own mission a way off isn't walked across the map. */
    public static final double ACROSS_REACH = 32;

    public static List<Transfer> across(List<SoldierStatus> short_, List<SoldierStatus> givers) {
        List<Transfer> transfers = new ArrayList<>();
        Set<String> busy = new HashSet<>();
        short_.stream().filter(r -> !(r.report() instanceof Report.LinkingUp))
                .sorted(Comparator.comparingInt(Item.RATIONS::carried)).forEach(receiver -> givers.stream()
                        .filter(g -> !busy.contains(g.name()) && !g.name().equals(receiver.name()) && !critical(g)
                                && !(g.report() instanceof Report.LinkingUp) && !(g.report() instanceof Report.InContact)
                                && Cohesion.distance(g.position(), receiver.position()) <= ACROSS_REACH)
                        .filter(g -> Item.RATIONS.carried(g) - KEEP_RATIONS >= RATIONS_GIVEN
                                && Item.RATIONS.carried(g) - RATIONS_GIVEN >= Item.RATIONS.carried(receiver) + RATIONS_GIVEN)
                        .min(Comparator.comparingDouble(g -> Cohesion.distance(g.position(), receiver.position())))
                        .ifPresent(g -> {
                            busy.add(g.name());
                            transfers.add(new Transfer(g.name(), receiver.name(), Item.RATIONS, RATIONS_GIVEN));
                        }));
        return List.copyOf(transfers);
    }

    /** Whether {@code soldier} is starving one hit from death, with nothing to eat. */
    public static boolean critical(SoldierStatus soldier) {
        return soldier.condition().filter(c -> c.food() == 0 && c.health() <= CRITICAL_HEALTH
                && c.kit().map(Kit::rations).orElse(0) == 0).isPresent();
    }

    /**
     * A meal for each soldier starving one hit from death, from the nearest squadmate carrying any
     * who isn't itself: live, a soldier stood at a third of a heart and food 0 beside a squadmate
     * with a ration it kept, since none had more than two to spare.
     */
    private static void buddyAid(List<SoldierStatus> squad, Set<String> busy, List<Transfer> transfers) {
        for (SoldierStatus receiver : squad) {
            if (!critical(receiver) || busy.contains(receiver.name()) || receiver.report() instanceof Report.LinkingUp) {
                continue;
            }
            squad.stream()
                    .filter(giver -> !giver.name().equals(receiver.name()) && !busy.contains(giver.name()) && !critical(giver)
                            && !(giver.report() instanceof Report.LinkingUp) && Item.RATIONS.carried(giver) > 0)
                    .min(Comparator.comparingDouble(giver -> Cohesion.distance(giver.position(), receiver.position())))
                    .ifPresent(giver -> {
                        busy.add(giver.name());
                        busy.add(receiver.name());
                        transfers.add(new Transfer(giver.name(), receiver.name(), Item.RATIONS, 1));
                    });
        }
    }

    private static void pair(List<SoldierStatus> squad, Item item, Map<String, Integer> wantedBy, Set<String> busy, List<Transfer> transfers) {
        List<SoldierStatus> receivers = squad.stream().sorted(Comparator.comparingInt(item::carried)).toList();
        for (SoldierStatus receiver : receivers) {
            Integer wanted = wantedBy.get(receiver.name());
            // A replacement links up first, then the squad reorganises round it: live, a giver was
            // sent to one still stranded where it respawned, out of reach on a peak.
            if (wanted == null || wanted <= 0 || busy.contains(receiver.name()) || receiver.report() instanceof Report.LinkingUp) {
                continue;
            }
            squad.stream()
                    .filter(giver -> !giver.name().equals(receiver.name()) && !busy.contains(giver.name())
                            && !(giver.report() instanceof Report.LinkingUp))
                    // One wanting the same gives only rations, and only to one carrying fewer than it'll
                    // be left with: two running low don't hand the same rations back and forth.
                    .filter(giver -> !wantedBy.containsKey(giver.name())
                            || item == Item.RATIONS && item.carried(giver) - wanted >= item.carried(receiver) + wanted)
                    .filter(giver -> item.carried(giver) - item.kept() >= wanted)
                    .min(Comparator.comparingDouble(giver -> Cohesion.distance(giver.position(), receiver.position())))
                    .ifPresent(giver -> {
                        busy.add(giver.name());
                        busy.add(receiver.name());
                        transfers.add(new Transfer(giver.name(), receiver.name(), item, wanted));
                    });
        }
    }
}
