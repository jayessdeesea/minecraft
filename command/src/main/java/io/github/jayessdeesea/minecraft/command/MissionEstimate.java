package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a mission costs and whether it's in reach, worked out before it's taken on (backlog/
 * mission-estimate.md; ADP 5-0's screening, FM 4-0's logistics estimate): its bill of materials by
 * class of supply, its duration at the force's measured rate, and its shortfall - what the bill
 * draws beyond the stock above the supplying site's reorder point. Feasible with no shortfall;
 * otherwise it starts with a PREPARE phase that makes the shortfall good. The platoon leader chooses,
 * the code costs: it never estimates.
 *
 * @param mission  what's estimated: "the keep"
 * @param work     the work's size in words: "1,846 blocks to dig, 4,096 cells to fill"
 * @param days     how long it takes at the measured rate, in in-game days; infinite with no rate yet
 * @param bill     what it consumes, by class
 * @param short_   what the supplying site can't spare of it, by class - empty when it can
 */
public record MissionEstimate(String mission, String work, double days, List<Line> bill, List<Line> short_) {

    public MissionEstimate {
        bill = List.copyOf(bill);
        short_ = List.copyOf(short_);
    }

    /** One line of a bill or a shortfall: a class, what of it, how many. */
    public record Line(String supplyClass, String what, int count) {
        @Override
        public String toString() {
            return count + " " + what;
        }
    }

    /** A stone tool's uses, and a log's handles: four planks, eight sticks, four tools. */
    static final int TOOL_USES = 132, TOOLS_A_LOG = 4;
    /** Cobblestone a chest holds: 27 stacks of 64. */
    static final int CHEST_HOLDS = 27 * 64;
    /** Logs a chest takes: eight planks. */
    static final int LOGS_A_CHEST = 2;

    /** Whether the supplying site can spare everything it draws. */
    public boolean feasible() {
        return short_.isEmpty();
    }

    /** The logs the bill draws, all told - its Class IV and its tools' handles. */
    public int logs() {
        return bill.stream().filter(l -> l.what().equals("logs")).mapToInt(Line::count).sum();
    }

    /**
     * The keep (command.Keep): {@code toDig} blocks off the site and {@code toFill} cells of its slab,
     * at {@code digPerDay} blocks a day as measured, by {@code crew} builders. Its bill: a stone tool
     * for every {@link #TOOL_USES} blocks dug, a log's handles for every {@link #TOOLS_A_LOG} of them;
     * the slab's cobblestone, dug out on the way and kept in chests till it's laid, two logs a chest;
     * and its days' rations at {@code rationsPerSoldierDay} a builder. Short of what
     * {@code logsSpare} - logs above the reorder point - and {@code stoneOnHand} don't cover; the
     * rations are the farm's, counted in the force's supply, so they're on the bill and not the
     * shortfall.
     */
    public static MissionEstimate keep(int toDig, int toFill, double digPerDay, int crew, double rationsPerSoldierDay,
                                       int logsSpare, int stoneOnHand) {
        double days = digPerDay <= 0 ? Double.POSITIVE_INFINITY : (toDig + Math.max(0, toFill)) / digPerDay;
        int tools = (int) Math.ceil(toDig / (double) TOOL_USES);
        int handleLogs = (int) Math.ceil(tools / (double) TOOLS_A_LOG);
        int stone = Math.max(0, toFill);
        int chests = (int) Math.ceil(Math.max(0, stone - stoneOnHand) / (double) CHEST_HOLDS);
        int logs = handleLogs + chests * LOGS_A_CHEST;
        int rations = Double.isInfinite(days) ? 0 : (int) Math.ceil(days * crew * rationsPerSoldierDay);
        List<Line> bill = List.of(new Line("II", "stone tools", tools), new Line("IV", "logs", logs), new Line("IV", "chests", chests),
                new Line("IV", "cobblestone", stone), new Line("I", "rations", rations));
        List<Line> short_ = new ArrayList<>();
        if (logs > logsSpare) {
            short_.add(new Line("IV", "logs", logs - Math.max(0, logsSpare)));
        }
        return new MissionEstimate("the keep", String.format(Locale.ROOT, "%d blocks to dig, %d cells to fill", toDig, Math.max(0, toFill)),
                days, bill, short_);
    }

    /** "the keep: 1846 blocks to dig, 0 cells to fill - 2.3 days; bill 14 stone tools, ...; PREPARE first: 3 logs". */
    public String line() {
        return String.format(Locale.ROOT, "%s: %s - %s; bill %s; %s", mission, work,
                Double.isInfinite(days) ? "no rate measured yet" : String.format(Locale.ROOT, "%.1f days", days),
                String.join(", ", bill.stream().map(Line::toString).toList()),
                feasible() ? "FEASIBLE" : "PREPARE first: " + String.join(", ", short_.stream().map(Line::toString).toList()));
    }
}
