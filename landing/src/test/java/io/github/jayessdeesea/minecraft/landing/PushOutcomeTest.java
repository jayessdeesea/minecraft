package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A push classified from what was seen after it, never from its error text (design section 3): the table is the
 * design's, row for row. A word that is not a value ("any", "unknown", "n/a") stands for both values, and each row is
 * checked for both.
 */
class PushOutcomeTest {

    private static final List<String> TABLE = List.of(
            // push, fetch, id on main, main moved -> answer
            "any, fails, unknown, unknown, Fault FETCH_FAILED",
            "any, ok, yes, n/a, Landed",
            "refused, ok, no, no, Fault PUSH_REFUSED",
            "refused, ok, no, yes, Fault OUTSIDE_PUSH",
            "ok, ok, no, n/a, Halt");

    static Stream<Arguments> table() {
        return TABLE.stream().map(row -> Arguments.of((Object[]) row.split(", ")));
    }

    @ParameterizedTest
    @MethodSource("table")
    void theDesignsTable(String push, String fetch, String onMain, String moved, String expected) {
        for (boolean p : values(push, "ok", "refused")) {
            for (boolean f : values(fetch, "ok", "fails")) {
                for (boolean o : values(onMain, "yes", "no")) {
                    for (boolean m : values(moved, "yes", "no")) {
                        assertEquals(expected, line(PushOutcome.of(p, f, o, m)), p + " " + f + " " + o + " " + m);
                    }
                }
            }
        }
    }

    /** The table has no hole and no overlap: each of the sixteen cases is in exactly one row. */
    @Test
    void everyCaseIsInOneRow() {
        for (boolean p : List.of(true, false)) {
            for (boolean f : List.of(true, false)) {
                for (boolean o : List.of(true, false)) {
                    for (boolean m : List.of(true, false)) {
                        int rows = 0;
                        for (String row : TABLE) {
                            String[] cell = row.split(", ");
                            if (values(cell[0], "ok", "refused").contains(p) && values(cell[1], "ok", "fails").contains(f)
                                    && values(cell[2], "yes", "no").contains(o) && values(cell[3], "yes", "no").contains(m)) {
                                rows++;
                            }
                        }
                        assertEquals(1, rows, p + " " + f + " " + o + " " + m);
                    }
                }
            }
        }
    }

    private static List<Boolean> values(String cell, String yes, String no) {
        if (cell.equals(yes)) {
            return List.of(true);
        }
        if (cell.equals(no)) {
            return List.of(false);
        }
        return List.of(true, false);
    }

    private static String line(PushOutcome outcome) {
        return switch (outcome) {
            case PushOutcome.Landed l -> "Landed";
            case PushOutcome.Halt h -> "Halt";
            case Faulted f -> "Fault " + f.kind();
        };
    }
}
