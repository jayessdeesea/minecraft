package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The squad leader's answer to a share's spell ended short of its yield ({@link ShareWatch}): a standing rule per cause,
 * each said on the net (backlog/duty-orders.md; ATP 3-21.8, the squad leader checking its soldiers' tasks are done).
 * <ol>
 * <li>The soldier said CANTCO at its duty - its table or chest out of reach: the order's re-pointed to the next, or,
 * with none, the CANTCO goes up the chain.</li>
 * <li>Its source ran dry: the next source, or, with none stored, up the chain.</li>
 * <li>Its ground worked out - a woodcutter's strip found empty: the wood shared again without it.</li>
 * <li>Its hands were other work's - a fight, a ration run - for half the spell or more: that's the cause, said, and it
 * isn't re-ordered.</li>
 * <li>No cause found: a gap report to the platoon leader naming the duty, the yield short and what was checked.</li>
 * </ol>
 * Pure: the orders at the spell's start and now, what it yielded, and its hands' time by routine are handed in.
 */
public final class MissAnswer {

    /** What a spell ended short was: its order then and now, its yield against what was expected, and how long it ran. */
    public record Miss(String squad, DutyOrder was, DutyOrder now, int yielded, int expected, long millis) {
    }

    /**
     * The cause found, {@code kind} - one answer said once while it holds, not every spell (live, field640-657: the same
     * alarm every few minutes for fifteen runs) - and the line said on the net.
     */
    public record Answer(String kind, String line) {
    }

    /** Whose a soldier's hands are with none of its routines holding them. */
    public static final String FREE = "free";

    private MissAnswer() {
    }

    /**
     * The answer to {@code miss}: {@code cantco} its soldier's last CANTCO at its duty in the spell, if any; {@code held}
     * how long its hands were each routine's in the spell, by name ({@link #FREE} with none); {@code duty} the routine its
     * yield is made in.
     */
    public static Answer answer(Miss miss, Optional<String> cantco, Map<String, Long> held, String duty) {
        DutyOrder was = miss.was(), now = miss.now();
        String soldier = now.soldier(), short_ = String.format(Locale.ROOT, "made %d of %d expected in %d s", miss.yielded(),
                miss.expected(), miss.millis() / 1000);
        if (cantco.isPresent()) {
            if (now.table().isPresent() && !now.table().equals(was.table())) {
                return new Answer("CANTCO", String.format(Locale.ROOT, "[%s -> %s] CANTCO noted, %s: %s - the next table, at %s", miss.squad(),
                        soldier, cantco.get(), short_, at(now.table().get())));
            }
            return new Answer("CANTCO", String.format(Locale.ROOT, "[%s -> PL] %s, %s, CANTCO: %s - %s, %s", miss.squad(), soldier,
                    now.duty().name().toLowerCase(Locale.ROOT), cantco.get(), short_,
                    now.table().isEmpty() && now.duty() == DutyOrder.Duty.BAKER ? "no table standing to re-point it to" : "nothing to re-point it to"));
        }
        if (now.duty() == DutyOrder.Duty.BAKER && !now.source().equals(was.source())) {
            return new Answer("its source ran dry", now.source().map(s -> String.format(Locale.ROOT, "[%s -> %s] %s ran dry, %s - the next, at %s", miss.squad(),
                            soldier, was.source().map(MissAnswer::at).orElse("its source"), short_, at(s)))
                    .orElseGet(() -> String.format(Locale.ROOT, "[%s -> PL] %s, baker, idle: no wheat stored - %s", miss.squad(), soldier, short_)));
        }
        if (!now.ground().equals(was.ground())) {
            return new Answer("its ground worked out", String.format(Locale.ROOT, "[%s -> %s] its ground worked out, %s - shared again: %s", miss.squad(),
                    soldier, short_, now.line()));
        }
        Optional<Map.Entry<String, Long>> other = held.entrySet().stream().filter(e -> !e.getKey().equals(duty) && !e.getKey().equals(FREE))
                .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())));
        if (other.filter(e -> e.getValue() * 2 >= miss.millis()).isPresent()) {
            return new Answer("its hands " + other.get().getKey() + "'s", String.format(Locale.ROOT, "[%s] %s short of its share, %s - its hands %s's for %d s of it: "
                    + "the cause, not re-ordered", miss.squad(), soldier, short_, other.get().getKey(), other.get().getValue() / 1000));
        }
        return new Answer("no cause found", String.format(Locale.ROOT, "[Gap] [%s -> PL] %s's %s short: %s, %s; checked: no CANTCO, its order unchanged, its hands "
                        + "the duty's for %d s and free for %d s", miss.squad(), soldier, now.duty().name().toLowerCase(Locale.ROOT), now.line(), short_,
                held.getOrDefault(duty, 0L) / 1000, held.getOrDefault(FREE, 0L) / 1000));
    }

    private static String at(Position p) {
        return "(" + Math.round(Math.floor(p.x())) + ", " + Math.round(Math.floor(p.y())) + ", " + Math.round(Math.floor(p.z())) + ")";
    }
}
