package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-leveling within the squad (docs/military-doctrine.md §13): a soldier that needs logs for a
 * tool and can't chop them, food and has none, or a weapon and is bare-handed, gets them from the
 * nearest squadmate with some to spare. This drill only pairs them; walking over and handing them across is the soldiers'
 * ({@code bot.supply}).
 *
 * <p>Pure and stateless like {@link Resupply}. A squad in contact doesn't cross-level - the fight
 * comes first, as it does for rearming.
 */
public final class CrossLevel {

    /** Logs a giver keeps for itself: enough for its own next pickaxe. */
    public static final int KEEP_LOGS = 3;
    /** Rations a giver keeps for itself: its own next two meals. */
    public static final int KEEP_RATIONS = 2;
    /** Rations handed to a soldier with none: two meals, a hungry soldier's way back to full. */
    public static final int RATIONS_GIVEN = 2;

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
     * {@code rationsWanted} (by name) from the nearest squadmate with that many beyond
     * {@link #KEEP_RATIONS}. A soldier is in one transfer at a time, giving or receiving.
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
        pair(squad, Item.WEAPON, weaponsWanted, busy, transfers);
        pair(squad, Item.LOGS, logsWanted, busy, transfers);
        pair(squad, Item.RATIONS, rationsWanted, busy, transfers);
        return List.copyOf(transfers);
    }

    private static void pair(List<SoldierStatus> squad, Item item, Map<String, Integer> wantedBy, Set<String> busy, List<Transfer> transfers) {
        for (SoldierStatus receiver : squad) {
            Integer wanted = wantedBy.get(receiver.name());
            // A replacement links up first, then the squad reorganises round it: live, a giver was
            // sent to one still stranded where it respawned, out of reach on a peak.
            if (wanted == null || wanted <= 0 || busy.contains(receiver.name()) || receiver.report() instanceof Report.LinkingUp) {
                continue;
            }
            squad.stream()
                    .filter(giver -> !giver.name().equals(receiver.name()) && !busy.contains(giver.name())
                            && !wantedBy.containsKey(giver.name()) && !(giver.report() instanceof Report.LinkingUp))
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
