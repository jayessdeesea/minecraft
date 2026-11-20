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
     * Rations handed across at a time: one, so a giver spreads what it has over everyone short of it,
     * fewest first, rather than unloading on the first (the operator's) - a hand-over a second
     * (Handover; operator, 2026-11-17: one second a ration a player), and a receiver fed by every
     * giver near it at once.
     */
    public static final int RATIONS_GIVEN = 1;
    /**
     * How far apart a giver and a receiver of rations are, at most: a hand-over is a second's work beside a squadmate,
     * not a walk across the farm for one ration. Live (field511), 75 single rations walked over in six minutes between
     * soldiers all short, Carol's harvest dropped for each - no starving one among them (buddy aid, {@link #critical},
     * has no reach).
     */
    public static final double RATIONS_REACH = 8;
    /**
     * Health at or under which a starving soldier is one hit from death: it gets a meal from any
     * squadmate carrying one, its keep or not - buddy aid, before anything else is handed across.
     */
    public static final double CRITICAL_HEALTH = 6;

    /**
     * The tool load: melee weapons, pickaxes and shovels a soldier carries of each - a main and a
     * spare: they wear out, and one that breaks mid-fight or mid-dig is the next in the pack, not a
     * walk to a table (operator, 2026-11-15, of the fighting load: two of each, the rest to the
     * depot - bot.craft.LoadPlan; four, 2026-11-12, before it).
     */
    public static final int TOOL_LOAD = 2;
    /** Below this many of a tool, a soldier asks its squadmates for one, as for rations. */
    public static final int TOOLS_LOW = 2;
    /** What a giver keeps of a tool: it gives only beyond this, one at a time. */
    public static final int KEEP_TOOLS = 2;

    /**
     * How far up or down a giver goes to a receiver: a hand-over is a walk, not a climb. Live, a giver
     * built steps up a hillside to a squadmate fourteen blocks over it, for one ration.
     */
    public static final double HANDOVER_HEIGHT = 6;

    /** Whether {@code giver} and {@code receiver} are near enough in height for a hand-over. */
    static boolean withinHeight(SoldierStatus giver, SoldierStatus receiver) {
        return Math.abs(giver.position().y() - receiver.position().y()) <= HANDOVER_HEIGHT;
    }

    /** What's handed across. */
    public enum Item {
        LOGS, RATIONS, WEAPON, PICKAXE, SHOVEL;

        /** How many of it {@code soldier} has to give and keep - for a weapon, only its spares. */
        int carried(SoldierStatus soldier) {
            return soldier.condition().flatMap(Condition::kit).map(k -> switch (this) {
                case LOGS -> k.logs();
                case RATIONS -> k.rations();
                case WEAPON -> k.spareWeapons();
                case PICKAXE -> k.pickaxes();
                case SHOVEL -> k.shovels();
            }).orElse(0);
        }

        int kept() {
            return switch (this) {
                case LOGS -> KEEP_LOGS;
                case RATIONS -> KEEP_RATIONS;
                case WEAPON -> 0; // a spare is spare, to one bare-handed: the giver keeps its best
                case PICKAXE, SHOVEL -> KEEP_TOOLS;
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
     * As {@link #pair(List, Map, Map, Map)}, and then the tool load: a pickaxe or a shovel to each
     * soldier in {@code pickaxesWanted} and {@code shovelsWanted} - short of {@link #TOOLS_LOW} - from
     * the nearest squadmate with more than {@link #KEEP_TOOLS} of it, one at a time.
     */
    public static List<Transfer> pair(List<SoldierStatus> squad, Map<String, Integer> logsWanted, Map<String, Integer> rationsWanted,
                                      Map<String, Integer> weaponsWanted, Map<String, Integer> pickaxesWanted,
                                      Map<String, Integer> shovelsWanted) {
        List<Transfer> transfers = new ArrayList<>(pair(squad, logsWanted, rationsWanted, weaponsWanted));
        if (squad.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return List.of();
        }
        Set<String> busy = new HashSet<>();
        transfers.forEach(t -> {
            busy.add(t.giver());
            busy.add(t.receiver());
        });
        pair(squad, Item.PICKAXE, pickaxesWanted, busy, transfers);
        pair(squad, Item.SHOVEL, shovelsWanted, busy, transfers);
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
                        .filter(g -> !busy.contains(g.name()) && !g.name().equals(receiver.name()) && !critical(g) && withinHeight(g, receiver)
                                && !(g.report() instanceof Report.LinkingUp) && !(g.report() instanceof Report.InContact)
                                && Cohesion.distance(g.position(), receiver.position()) <= ACROSS_REACH)
                        .filter(g -> evens(g, receiver))
                        .min(Comparator.comparingDouble(g -> Cohesion.distance(g.position(), receiver.position())))
                        .ifPresent(g -> {
                            busy.add(g.name());
                            transfers.add(new Transfer(g.name(), receiver.name(), Item.RATIONS, RATIONS_GIVEN));
                        }));
        return List.copyOf(transfers);
    }

    /**
     * Whether {@code giver} has a ration for {@code receiver}: two more than it carries, so what there is
     * evens out - no keep of its own held back while a squadmate has none (live, field369: 12 of 16 at
     * none, the farm squad at 12 and 13 under a keep of 15, nothing handed across; operator,
     * 2026-11-17: use it if you need it, the reserve included).
     */
    static boolean evens(SoldierStatus giver, SoldierStatus receiver) {
        return Item.RATIONS.carried(giver) >= Item.RATIONS.carried(receiver) + 2 * RATIONS_GIVEN;
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
                            && withinHeight(giver, receiver)
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
                            && !(giver.report() instanceof Report.LinkingUp) && withinHeight(giver, receiver))
                    // One wanting the same gives only rations, and only to one carrying fewer than it'll
                    // be left with: two running low don't hand the same rations back and forth.
                    .filter(giver -> !wantedBy.containsKey(giver.name())
                            || item == Item.RATIONS && item.carried(giver) - wanted >= item.carried(receiver) + wanted)
                    .filter(giver -> item == Item.RATIONS ? evens(giver, receiver) && Cohesion.distance(giver.position(), receiver.position()) <= RATIONS_REACH
                            : item.carried(giver) - item.kept() >= wanted)
                    .min(Comparator.comparingDouble(giver -> Cohesion.distance(giver.position(), receiver.position())))
                    .ifPresent(giver -> {
                        busy.add(giver.name());
                        // Rations: the receiver stays open to the next giver near it, each feeding it at once.
                        if (item != Item.RATIONS) {
                            busy.add(receiver.name());
                        }
                        transfers.add(new Transfer(giver.name(), receiver.name(), item, wanted));
                    });
        }
    }
}
