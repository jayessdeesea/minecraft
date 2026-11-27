package io.github.jayessdeesea.minecraft.landing.shell;

import java.time.Duration;

/** A waiter's pause between looks: the thread's sleep in the tool, the test clock's advance in a test. */
public interface Sleeper {

    void sleep(Duration duration);
}
