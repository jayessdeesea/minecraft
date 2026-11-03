package io.github.jayessdeesea.minecraft.bot.script;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptTest {

    private final List<String> done = new ArrayList<>();

    @Test
    void eachStepWaitsForWhatConfirmsItNotForTime() {
        AtomicBoolean opened = new AtomicBoolean();
        Script script = new Script("Alice", List.of(
                new Script.Step("open the chest", () -> done.add("useBlock"),
                        new Script.Expect("the chest's window", opened::get), 40),
                new Script.Step("take the sword", () -> done.add("quickMove"),
                        new Script.Expect("a sword in the inventory", () -> true), 40)));

        script.tick(); // acts
        for (int i = 0; i < 10; i++) {
            assertEquals(new Script.Status.Running(), script.tick(), "waits as long as it takes, within the deadline");
        }
        assertEquals(List.of("useBlock"), done, "and doesn't move on meanwhile");

        opened.set(true);
        script.tick(); // confirmed
        script.tick(); // next step acts
        assertEquals(new Script.Status.Done(), script.tick());
        assertEquals(List.of("useBlock", "quickMove"), done);
    }

    @Test
    void aDeadlineMissedFailsLoudlyWithWhatWasExpected() {
        Script script = new Script("Alice", List.of(
                new Script.Step("smelt", () -> { }, new Script.Expect("charcoal in the furnace", () -> false), 5)));

        Script.Status status = new Script.Status.Running();
        for (int i = 0; i < 10 && status instanceof Script.Status.Running; i++) {
            status = script.tick();
        }

        assertEquals(new Script.Status.Failed("smelt", "expected charcoal in the furnace within 5 ticks; it didn't come"), status);
    }

    @Test
    void aStepAlreadyDoneIsSkippedAndOneThatCantStartFails() {
        Script skipping = new Script("Alice", List.of(
                new Script.Step("craft a table", () -> true, () -> done.add("craft"), new Script.Expect("a table", () -> true), 40)));
        assertEquals(new Script.Status.Done(), skipping.tick());
        assertEquals(List.of(), done);

        Script missing = new Script("Alice", List.of(new Script.Step("place it", () -> {
            throw new IllegalStateException("no crafting table in the inventory");
        }, new Script.Expect("the table placed", () -> true), 40)));
        assertEquals(new Script.Status.Failed("place it", "couldn't start: no crafting table in the inventory"), missing.tick());
    }

    @Test
    void anAnswerThatRulesTheStepOutFailsItAtOnce() {
        AtomicBoolean wrongWindow = new AtomicBoolean();
        Script script = new Script("Alice", List.of(new Script.Step("open the table", () -> { },
                new Script.Expect("the crafting table's window", () -> {
                    if (wrongWindow.get()) {
                        throw new IllegalStateException("a furnace's window opened instead");
                    }
                    return false;
                }), 40)));

        script.tick();
        script.tick();
        wrongWindow.set(true);

        assertEquals(new Script.Status.Failed("open the table", "after 2 ticks: a furnace's window opened instead"), script.tick());
    }

    @Test
    void everyStepNeedsADeadline() {
        assertThrows(IllegalArgumentException.class,
                () -> new Script.Step("wait forever", () -> { }, new Script.Expect("anything", () -> false), 0));
    }
}
