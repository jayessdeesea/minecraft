package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * How each kind of command went in the field, for the dawn review (TC 25-20, sustain and improve;
 * backlog/command-scorecard.md). Each soldier is sampled once a second: the task its squad holds
 * (the general's command) and the order it carries (its own), with what it reports. A task's
 * share of soldier-time spent unable (CANTCO) or standing about is what a command that doesn't
 * work looks like - live, a wall left with 39 open cells while its masons stood holding blocks.
 * Pure: samples in, the review out, and the tally starts again.
 */
final class CommandScorecard {

    /** CANTCO reasons listed for each command kind, most often first. */
    static final int REASONS_LISTED = 3;

    private static final class Tally {
        final Map<String, Integer> byReport = new TreeMap<>();
        final Map<String, Integer> cantco = new LinkedHashMap<>();
        int seconds;
        int casualties;

        void add(Report report) {
            seconds++;
            byReport.merge(report.getClass().getSimpleName(), 1, Integer::sum);
            if (report instanceof Report.Cantco(String reason)) {
                cantco.merge(normalized(reason), 1, Integer::sum);
            }
        }
    }

    private final Map<String, Tally> tasks = new TreeMap<>();
    private final Map<String, Tally> orders = new TreeMap<>();

    /** One second of a soldier: its squad's {@code task}, its {@code order}'s kind, and its {@code report}. */
    synchronized void sample(String task, String order, Report report) {
        tasks.computeIfAbsent(task, t -> new Tally()).add(report);
        orders.computeIfAbsent(order, o -> new Tally()).add(report);
    }

    /** A soldier died with its squad on {@code task}, carrying an order of kind {@code order}. */
    synchronized void casualty(String task, String order) {
        tasks.computeIfAbsent(task, t -> new Tally()).casualties++;
        orders.computeIfAbsent(order, o -> new Tally()).casualties++;
    }

    /**
     * The shares of soldier-time since the last review spent standing about (Idle, InPosition) and
     * unable (CANTCO), in percent - each soldier-second counted once, by its order.
     */
    synchronized int[] shares() {
        int seconds = orders.values().stream().mapToInt(t -> t.seconds).sum();
        if (seconds == 0) {
            return new int[]{0, 0};
        }
        int idle = orders.values().stream().mapToInt(t -> t.byReport.getOrDefault("Idle", 0) + t.byReport.getOrDefault("InPosition", 0)).sum();
        int cantco = orders.values().stream().mapToInt(t -> t.byReport.getOrDefault("Cantco", 0)).sum();
        return new int[]{(int) Math.round(100.0 * idle / seconds), (int) Math.round(100.0 * cantco / seconds)};
    }

    /** The review's commands section since the last, as lines; the tally starts again. */
    synchronized List<String> review() {
        List<String> lines = new ArrayList<>();
        lines.add("commands, by soldier-minutes and what the soldiers reported:");
        tasks.forEach((kind, tally) -> lines.addAll(lines("task " + kind, tally)));
        orders.forEach((kind, tally) -> lines.addAll(lines("order " + kind, tally)));
        tasks.clear();
        orders.clear();
        return List.copyOf(lines);
    }

    private static List<String> lines(String what, Tally tally) {
        List<String> lines = new ArrayList<>();
        String shares = tally.byReport.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> e.getKey() + " " + Math.round(100.0 * e.getValue() / tally.seconds) + "%")
                .collect(Collectors.joining(", "));
        lines.add("  " + what + ": " + String.format(Locale.ROOT, "%.0f", tally.seconds / 60.0) + " min - " + shares
                + (tally.casualties > 0 ? "; " + tally.casualties + " killed" : ""));
        tally.cantco.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .limit(REASONS_LISTED)
                .forEach(e -> lines.add("    CANTCO " + e.getValue() + " s: " + e.getKey()));
        return lines;
    }

    /** A reason with its places and numbers taken out, so the same failure counts as one. */
    static String normalized(String reason) {
        return reason.replaceAll("BlockPos\\[[^]]*]", "#").replaceAll("\\([^)]*\\d[^)]*\\)", "#")
                .replaceAll("-?\\d+(\\.\\d+)?", "#");
    }
}
