package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-leveling within the squad (docs/military-doctrine.md §13): a soldier that needs logs for a
 * tool and can't chop them gets them from the nearest squadmate with logs to spare. This drill only
 * pairs them; walking over and handing the logs across is the soldiers' ({@code bot.supply}).
 *
 * <p>Pure and stateless like {@link Resupply}. A squad in contact doesn't cross-level - the fight
 * comes first, as it does for rearming.
 */
public final class CrossLevel {

    /** Logs a giver keeps for itself: enough for its own next pickaxe. */
    public static final int KEEP_LOGS = 3;

    /** {@code giver} hands {@code logs} logs to {@code receiver}. */
    public record Transfer(String giver, String receiver, int logs) {
    }

    private CrossLevel() {
    }

    /**
     * Who gives to whom: each soldier in {@code logsWanted} (by name, the logs it is short) is paired
     * with the nearest squadmate whose logs beyond {@link #KEEP_LOGS} cover the shortfall. A giver
     * gives to one soldier at a time; a soldier no one can cover is left out.
     */
    public static List<Transfer> pair(List<SoldierStatus> squad, Map<String, Integer> logsWanted) {
        if (squad.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return List.of();
        }
        List<Transfer> transfers = new ArrayList<>();
        Set<String> giving = new HashSet<>();
        for (SoldierStatus receiver : squad) {
            Integer wanted = logsWanted.get(receiver.name());
            if (wanted == null || wanted <= 0) {
                continue;
            }
            squad.stream()
                    .filter(giver -> !giver.name().equals(receiver.name()) && !giving.contains(giver.name())
                            && !logsWanted.containsKey(giver.name()) && !(giver.report() instanceof Report.LinkingUp))
                    .filter(giver -> logs(giver) - KEEP_LOGS >= wanted)
                    .min(Comparator.comparingDouble(giver -> Cohesion.distance(giver.position(), receiver.position())))
                    .ifPresent(giver -> {
                        giving.add(giver.name());
                        transfers.add(new Transfer(giver.name(), receiver.name(), wanted));
                    });
        }
        return List.copyOf(transfers);
    }

    private static int logs(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(Kit::logs).orElse(0);
    }
}
