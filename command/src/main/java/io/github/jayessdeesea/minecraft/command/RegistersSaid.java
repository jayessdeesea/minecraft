package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The platoon leader's registers said for the operator's chat to quote (docs/echelons/platoon-leader/decisions.md § Terms):
 * missions, each with the operation's phases under it; tasks, the rows, filtered; one task; a chunk, and the chunk a block
 * is in; the chunks kept. Every count, age and chunk's bounds computed here, never left to the model. Pure.
 */
public final class RegistersSaid {

    private RegistersSaid() {
    }

    /** One mission and the tasks kept for it, by phase: an operator's farm mission, or a task the platoon leader gave itself. */
    public record Operation(String mission, List<ObjectiveRecord> phases) {

        public Operation {
            phases = List.copyOf(phases);
        }

        /** Whether it's the operator's farm mission, carried out in phases - not one of the platoon leader's own tasks. */
        public boolean farm() {
            return FarmMission.named(mission).isPresent();
        }

        /**
         * Whether it's superseded - its order replaced, or cancelled: a phase SUPERSEDED, or kept RELIEVED "superseded by" from
         * before SUPERSEDED was its own state (field672).
         */
        public boolean superseded() {
            return phases.stream().anyMatch(r -> r.state() == ObjectiveRecord.State.SUPERSEDED || r.why().filter(w -> w.startsWith("superseded by")).isPresent());
        }

        /**
         * Whether it's still being carried out: a farm mission with a phase not done and not superseded (a phase's squad
         * relieved, the phase stays open); a task of the platoon leader's own planned, under way or CANTCO.
         */
        public boolean open() {
            return farm() ? !superseded() && phases.stream().anyMatch(r -> r.state() != ObjectiveRecord.State.DONE)
                    : phases.stream().anyMatch(RegistersSaid::live);
        }

        /** Whether {@code task}, one of its phases, is still open: live, or a farm phase relieved while the mission's open. */
        boolean open(ObjectiveRecord task) {
            return live(task) || farm() && open() && task.state() == ObjectiveRecord.State.RELIEVED;
        }

        /** Its first phase not done, while it's open. */
        public Optional<ObjectiveRecord> current() {
            return open() ? phases.stream().filter(r -> r.state() != ObjectiveRecord.State.DONE).min(Comparator.comparingInt(ObjectiveRecord::step))
                    : Optional.empty();
        }
    }

    /** {@code tasks} grouped into their operations, each in the order its first task was kept, its phases by step. */
    public static List<Operation> operations(List<ObjectiveRecord> tasks) {
        Map<String, List<ObjectiveRecord>> byMission = new LinkedHashMap<>();
        tasks.stream().sorted(Comparator.comparingLong(ObjectiveRecord::id)).forEach(r -> byMission.computeIfAbsent(r.mission(), m -> new ArrayList<>()).add(r));
        return byMission.entrySet().stream().map(e -> new Operation(e.getKey(), e.getValue().stream()
                .sorted(Comparator.comparingInt(ObjectiveRecord::step).thenComparingLong(ObjectiveRecord::id)).toList())).toList();
    }

    /**
     * The missions kept - {@code all}, or only those still being carried out - each the operator's with its phases under it,
     * then the tasks the platoon leader gave by its own decisions under the platoon's mission.
     */
    public static String missions(List<ObjectiveRecord> tasks, boolean all, long now) {
        List<Operation> operations = operations(tasks);
        List<Operation> farms = operations.stream().filter(Operation::farm).filter(o -> all || o.open()).toList();
        List<Operation> own = operations.stream().filter(o -> !o.farm()).filter(o -> all || o.open()).toList();
        StringBuilder text = new StringBuilder();
        long kept = operations.stream().filter(Operation::farm).count();
        text.append(farms.isEmpty() ? (all ? "No mission from the operator is kept." : "No mission from the operator is open (" + kept + " kept, all closed).")
                : String.format(Locale.ROOT, "Missions from the operator, %s (%d of %d kept):", all ? "open and closed" : "open", farms.size(), kept));
        for (Operation o : farms) {
            text.append("\n- ").append(o.mission()).append(" - ").append(standing(o)).append(". Objective: ")
                    .append(chunkSaid(o.phases().getFirst().chunkX(), o.phases().getFirst().chunkZ())).append('.');
            for (ObjectiveRecord r : o.phases()) {
                text.append("\n  - phase ").append(r.step() + 1).append(": ").append(r.task()).append(" - ").append(how(r, now)).append(" (task #").append(r.id()).append(')');
            }
        }
        text.append('\n').append(own.isEmpty() ? "No task of the platoon leader's own decisions is " + (all ? "kept." : "open.")
                : "Tasks the platoon leader gave by its own decisions, under the platoon's mission" + (all ? ":" : ", open:"));
        for (Operation o : own) {
            for (ObjectiveRecord r : o.phases()) {
                if (all || o.open(r)) {
                    text.append("\n- ").append(line(o, r, now));
                }
            }
        }
        return text.toString();
    }

    /** The filters {@link #tasks} reads its {@code state} as: OPEN, ALL, or one of the states. */
    public static final String STATES = "OPEN, ALL, " + String.join(", ", Arrays.stream(ObjectiveRecord.State.values()).map(Enum::name).toList());

    /**
     * The tasks kept, filtered: {@code squad}'s (any case), in {@code state} - OPEN for those still being carried out, ALL, or
     * one of the states - of the mission whose name holds {@code mission} (any case); each filter empty for any.
     *
     * @throws IllegalArgumentException for a state that isn't one of {@link #STATES}
     */
    public static String tasks(List<ObjectiveRecord> tasks, Optional<String> squad, Optional<String> state, Optional<String> mission, long now) {
        String which = state.map(s -> s.strip().toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).orElse("OPEN");
        if (!which.equals("OPEN") && !which.equals("ALL") && Arrays.stream(ObjectiveRecord.State.values()).noneMatch(s -> s.name().equals(which))) {
            throw new IllegalArgumentException("no task state '" + state.orElse("") + "': one of " + STATES);
        }
        Optional<String> sq = squad.map(String::strip).filter(s -> !s.isEmpty());
        Optional<String> of = mission.map(m -> m.strip().toLowerCase(Locale.ROOT)).filter(m -> !m.isEmpty());
        List<String> lines = new ArrayList<>();
        for (Operation o : operations(tasks)) {
            if (of.filter(m -> !o.mission().toLowerCase(Locale.ROOT).contains(m)).isPresent()) {
                continue;
            }
            Predicate<ObjectiveRecord> in = r -> which.equals("ALL") || which.equals("OPEN") ? which.equals("ALL") || o.open(r) : r.state().name().equals(which);
            o.phases().stream().filter(in).filter(r -> sq.isEmpty() || r.squad().filter(s -> s.equalsIgnoreCase(sq.get())).isPresent())
                    .forEach(r -> lines.add(line(o, r, now)));
        }
        String filter = (which.equals("ALL") ? "" : " " + which.toLowerCase(Locale.ROOT).replace('_', ' ')) + sq.map(s -> " for " + s).orElse("")
                + of.map(m -> " of a mission naming \"" + mission.get().strip() + "\"").orElse("");
        if (lines.isEmpty()) {
            return "No task" + filter + " is kept (" + tasks.size() + " task(s) kept in all).";
        }
        return lines.size() + " task(s)" + filter + ":\n- " + String.join("\n- ", lines);
    }

    /** Task {@code id} as it stands, or that none is kept by that number. */
    public static String task(List<ObjectiveRecord> tasks, long id, long now) {
        for (Operation o : operations(tasks)) {
            for (ObjectiveRecord r : o.phases()) {
                if (r.id() == id) {
                    return line(o, r, now) + (o.farm() ? " The mission: " + o.mission() + " - " + standing(o) + "." : "");
                }
            }
        }
        return "No task #" + id + " is kept: the numbers kept are " + tasks.stream().map(r -> "#" + r.id()).toList() + ".";
    }

    /** What's kept of chunk ({@code chunkX}, {@code chunkZ}), and the tasks open on it. */
    public static String chunk(List<ChunkRecord> chunks, List<ObjectiveRecord> tasks, int chunkX, int chunkZ, long now) {
        Optional<ChunkRecord> kept = chunks.stream().filter(c -> c.chunkX() == chunkX && c.chunkZ() == chunkZ).findFirst();
        StringBuilder text = new StringBuilder(kept.map(c -> chunkLine(c, now)).orElse("Nothing is kept of " + chunkSaid(chunkX, chunkZ)
                + ": the force has made nothing of it that's measured."));
        List<String> open = new ArrayList<>();
        for (Operation o : operations(tasks)) {
            o.phases().stream().filter(r -> r.chunkX() == chunkX && r.chunkZ() == chunkZ && o.open(r)).forEach(r -> open.add(line(o, r, now)));
        }
        text.append(open.isEmpty() ? "\nNo task is open on it." : "\nTasks open on it:\n- " + String.join("\n- ", open));
        return text.toString();
    }

    /** The chunk block ({@code x}, {@code z}) is in, and what's kept of it. */
    public static String chunkOf(List<ChunkRecord> chunks, List<ObjectiveRecord> tasks, int x, int z, long now) {
        int chunkX = Math.floorDiv(x, 16), chunkZ = Math.floorDiv(z, 16);
        return String.format(Locale.ROOT, "Block x=%d z=%d is in chunk (%d, %d).", x, z, chunkX, chunkZ) + "\n"
                + chunk(chunks, tasks, chunkX, chunkZ, now);
    }

    /**
     * The chunks kept, of {@code kind} if one's given (any case).
     *
     * @throws IllegalArgumentException for a kind that isn't one of {@link ChunkRecord.Kind}
     */
    public static String chunks(List<ChunkRecord> chunks, Optional<String> kind, long now) {
        Optional<ChunkRecord.Kind> of = kind.map(k -> k.strip().toUpperCase(Locale.ROOT)).filter(k -> !k.isEmpty()).map(k -> {
            try {
                return ChunkRecord.Kind.valueOf(k);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("no chunk kind '" + kind.get() + "': one of " + Arrays.toString(ChunkRecord.Kind.values()), e);
            }
        });
        List<ChunkRecord> kept = chunks.stream().filter(c -> of.isEmpty() || c.kind() == of.get()).toList();
        String what = of.map(k -> " " + k.name()).orElse("");
        if (kept.isEmpty()) {
            return "No" + what + " chunk is kept (" + chunks.size() + " chunk(s) kept in all).";
        }
        return kept.size() + what + " chunk(s) kept:\n- " + String.join("\n- ", kept.stream().map(c -> chunkLine(c, now)).toList());
    }

    /** A chunk as said everywhere here: its x and z, and the blocks it spans. */
    public static String chunkSaid(int chunkX, int chunkZ) {
        return String.format(Locale.ROOT, "chunk (%d, %d), x %d..%d z %d..%d", chunkX, chunkZ, chunkX * 16, chunkX * 16 + 15, chunkZ * 16, chunkZ * 16 + 15);
    }

    /** How long ago {@code then} was at {@code now}, in seconds or minutes and seconds. */
    static String age(long now, long then) {
        long seconds = Math.max(0, now - then) / 1000;
        return seconds < 60 ? seconds + " s" : String.format(Locale.ROOT, "%d min %02d s", seconds / 60, seconds % 60);
    }

    private static boolean live(ObjectiveRecord r) {
        return r.state() == ObjectiveRecord.State.PLANNED || r.state() == ObjectiveRecord.State.UNDER_WAY || r.state() == ObjectiveRecord.State.CANTCO;
    }

    private static String standing(Operation o) {
        if (o.open()) {
            ObjectiveRecord at = o.current().orElseThrow();
            return "OPEN, at phase " + (at.step() + 1) + " of " + o.phases().size();
        }
        if (o.superseded()) {
            return "CLOSED, SUPERSEDED: " + o.phases().stream().filter(r -> r.state() == ObjectiveRecord.State.SUPERSEDED || r.state() == ObjectiveRecord.State.RELIEVED)
                    .flatMap(r -> r.why().stream()).findFirst().orElse("no reason kept");
        }
        return "CLOSED, every phase DONE";
    }

    private static String how(ObjectiveRecord r, long now) {
        return r.state() + r.squad().map(s -> ", " + s).orElse(r.state() == ObjectiveRecord.State.PLANNED ? ", no squad yet" : "")
                + (r.state() == ObjectiveRecord.State.PLANNED ? "" : live(r) ? " for " + age(now, r.changedAt()) : " " + age(now, r.changedAt()) + " ago")
                + r.why().map(w -> ": " + w).orElse("");
    }

    private static String line(Operation o, ObjectiveRecord r, long now) {
        String what = o.farm() ? o.mission() + ", phase " + (r.step() + 1) + " of " + o.phases().size() + ": " + r.task()
                : o.mission() + " (" + r.task() + ")";
        return "task #" + r.id() + ": " + what + " - " + how(r, now) + ". Objective: " + chunkSaid(r.chunkX(), r.chunkZ()) + ".";
    }

    private static String chunkLine(ChunkRecord c, long now) {
        return chunkSaid(c.chunkX(), c.chunkZ()) + ": " + c.kind() + " at y=" + c.level() + ", " + c.stage().orElse("not measured")
                + ", measured " + age(now, c.measuredAt()) + " ago.";
    }
}
