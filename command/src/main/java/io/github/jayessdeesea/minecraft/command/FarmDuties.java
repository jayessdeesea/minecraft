package io.github.jayessdeesea.minecraft.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The squad leader's farm duties (operator, 2026-11-18; docs/echelons/tenets.md, the squad leader owns
 * each producer's duty): the soldiers on a farm are its harvesters, each on a share of the plot of its
 * own - harvested and replanted in one pass, its wheat taken to the stockpile - and, with three or more,
 * the last on the roster its baker, turning the stockpile's wheat into bread. Pure.
 */
public final class FarmDuties {

    /** A soldier's part on the farm. */
    public enum Kind { HARVESTER, BAKER }

    /** {@code soldier}'s duty: a harvester's {@code share} of {@code shares}, or the baker's. */
    public record Duty(String soldier, Kind kind, int share, int shares) {
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
}
