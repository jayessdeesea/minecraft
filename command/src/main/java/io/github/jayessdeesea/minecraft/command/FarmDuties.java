package io.github.jayessdeesea.minecraft.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The squad leader's farm duties (operator, 2026-11-18; docs/echelons/tenets.md, the squad leader owns
 * each producer's duty): the soldiers on a farm are its harvesters, each on a share of the plot of its
 * own - harvested and replanted in one pass, its wheat taken to the stockpile - and, with three or more,
 * the last on the roster its baker, turning the stockpile's wheat into bread. A chunk farm on it has one tender
 * (operator, 2026-11-19: a single soldier at a time works a specific farm): a harvester whose ground is the chunk and
 * whose yield goes to the chunk's own chest. Pure.
 */
public final class FarmDuties {

    /** A soldier's part on the farm. */
    public enum Kind { HARVESTER, BAKER }

    /** {@code soldier}'s duty: a harvester's {@code share} of {@code shares}, or the baker's. */
    public record Duty(String soldier, Kind kind, int share, int shares) {
    }

    /** A chunk farm on the farm: its ground, fence lines and all, and its chest - its tender's dump. */
    public record Tended(Box ground, Position chest) {
    }

    /** The fewest on a farm for one of them to be its baker. */
    static final int BAKER_FROM = 3;

    private FarmDuties() {
    }

    /** The duties of {@code farmers}, in roster order. */
    public static Map<String, Duty> of(List<String> farmers) {
        int harvesters = farmers.size() >= BAKER_FROM ? farmers.size() - 1 : farmers.size();
        Map<String, Duty> duties = new LinkedHashMap<>();
        for (int i = 0; i < harvesters; i++) {
            duties.put(farmers.get(i), new Duty(farmers.get(i), Kind.HARVESTER, i, harvesters));
        }
        if (harvesters < farmers.size()) {
            String baker = farmers.getLast();
            duties.put(baker, new Duty(baker, Kind.BAKER, 0, 1));
        }
        return Map.copyOf(duties);
    }

    /** {@code duty}'s share of {@code plot}, in its order: a run of it, as near equal to the others' as they come. */
    public static <T> List<T> share(List<T> plot, Duty duty) {
        int from = plot.size() * duty.share() / duty.shares();
        int to = plot.size() * (duty.share() + 1) / duty.shares();
        return plot.subList(from, to);
    }

    /**
     * {@code farmers}' duty orders on {@code plot}, their yield to {@code dump}: each harvester a run of the plot's whole
     * columns of x, as near an equal share as they come - no cell in two harvesters' ground - and the baker none, at the
     * dump. A harvester with no column left (more harvesters than columns) has no ground.
     */
    public static Map<String, DutyOrder> orders(List<String> farmers, List<Position> plot, Optional<Position> dump) {
        return orders(farmers, plot, dump, List.of());
    }

    /**
     * As {@link #orders(List, List, Optional)}, with {@code chunks} on the farm: the last harvesters on the roster each
     * tend one, its ground and its chest - while one harvester is left for the rest of the plot - and the rest share
     * what's outside them. With too few harvesters a chunk farm is the shares' like any other ground.
     */
    public static Map<String, DutyOrder> orders(List<String> farmers, List<Position> plot, Optional<Position> dump, List<Tended> chunks) {
        List<String> harvesters = farmers.stream().filter(f -> of(farmers).get(f).kind() == Kind.HARVESTER).toList();
        List<Position> rest = plot.stream().filter(p -> chunks.stream().noneMatch(c -> within(c.ground(), p))).toList();
        int tenders = Math.min(chunks.size(), harvesters.size() - (rest.isEmpty() ? 0 : 1));
        Map<String, DutyOrder> orders = new LinkedHashMap<>();
        for (int i = 0; i < tenders; i++) {
            String tender = harvesters.get(harvesters.size() - tenders + i);
            orders.put(tender, new DutyOrder(tender, DutyOrder.Duty.HARVESTER, Optional.of(chunks.get(i).ground()), Optional.of(chunks.get(i).chest())));
        }
        List<String> others = farmers.stream().filter(f -> !orders.containsKey(f)).toList();
        orders.putAll(shared(others, tenders > 0 ? rest : plot, dump, harvesters.size() < farmers.size()));
        return Map.copyOf(orders);
    }

    private static boolean within(Box box, Position p) {
        return box.contains((int) Math.floor(p.x()), (int) Math.floor(p.y()), (int) Math.floor(p.z()));
    }

    /** The shares of {@code plot} among {@code farmers}, the last of them its baker if it has {@code baker}. */
    private static Map<String, DutyOrder> shared(List<String> farmers, List<Position> plot, Optional<Position> dump, boolean baker) {
        int harvesters = baker ? farmers.size() - 1 : farmers.size();
        Map<String, Duty> duties = new LinkedHashMap<>();
        for (int i = 0; i < farmers.size(); i++) {
            duties.put(farmers.get(i), i < harvesters ? new Duty(farmers.get(i), Kind.HARVESTER, i, harvesters) : new Duty(farmers.get(i), Kind.BAKER, 0, 1));
        }
        List<Integer> columns = plot.stream().map(p -> (int) Math.floor(p.x())).distinct().sorted().toList();
        Map<String, DutyOrder> orders = new LinkedHashMap<>();
        for (String farmer : farmers) {
            Duty duty = duties.get(farmer);
            if (duty.kind() == Kind.BAKER) {
                orders.put(farmer, new DutyOrder(farmer, DutyOrder.Duty.BAKER, Optional.empty(), dump));
                continue;
            }
            List<Integer> own = share(columns, duty);
            Optional<Box> ground = own.isEmpty() ? Optional.empty() : Optional.of(new Box(own.getFirst(),
                    plot.stream().mapToInt(p -> (int) Math.floor(p.y())).min().orElseThrow() - 1,
                    plot.stream().mapToInt(p -> (int) Math.floor(p.z())).min().orElseThrow(), own.getLast(),
                    plot.stream().mapToInt(p -> (int) Math.floor(p.y())).max().orElseThrow() + 1,
                    plot.stream().mapToInt(p -> (int) Math.floor(p.z())).max().orElseThrow()));
            orders.put(farmer, new DutyOrder(farmer, DutyOrder.Duty.HARVESTER, ground, dump));
        }
        return orders;
    }
}
