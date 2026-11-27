package io.github.jayessdeesea.minecraft.landing.shell;

import java.nio.file.Path;

/**
 * A second JVM for the lock's rows: takes the integrator lock at the path given, says "held" (or "busy"), and holds it
 * until it is killed.
 */
public final class LockHolder {

    private LockHolder() {
    }

    public static void main(String[] args) throws InterruptedException {
        var held = new IntegratorLock(Path.of(args[0])).tryAcquire();
        System.out.println(held.isPresent() ? "held" : "busy");
        System.out.flush();
        Thread.sleep(120_000);
    }
}
