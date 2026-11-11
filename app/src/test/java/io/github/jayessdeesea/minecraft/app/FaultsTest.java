package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaultsTest {

    private long now;
    private final Faults faults = new Faults(() -> now);

    @Test
    void aTaskThatFailsRunsAgainNextTick() {
        AtomicInteger runs = new AtomicInteger();
        Runnable task = faults.guard("the squad loop", () -> {
            if (runs.incrementAndGet() == 1) {
                throw new NoSuchElementException("No value present");
            }
        });

        task.run();
        task.run();

        assertEquals(2, runs.get(), "the fault didn't end it");
    }

    @Test
    void theReviewCountsEachFaultThenStartsAgain() {
        Runnable task = faults.guard("Alpha's orders", () -> {
            throw new IllegalStateException("no SECURE objective");
        });
        for (int i = 0; i < 3; i++) {
            task.run();
            now += 1_000;
        }

        List<String> review = faults.review();

        assertEquals(1, review.size());
        assertTrue(review.getFirst().contains("Alpha's orders - IllegalStateException") && review.getFirst().endsWith("x3"), review.toString());
        assertEquals(List.of("  no faults"), faults.review());
    }

    @Test
    void theVmFailingIsNotCaught() {
        assertThrows(StackOverflowError.class, () -> faults.run("a tick", () -> {
            throw new StackOverflowError();
        }));
    }
}
