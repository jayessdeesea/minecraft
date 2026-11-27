package io.github.jayessdeesea.minecraft.landing.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The in-process tests' {@link Children}: they run four to a JVM, where the job's "all but me" would kill a neighbour's
 * child, so they end a tree by its parent chain. That misses an MSYS program forked and exec'd from sh - its fork stub
 * is gone for its Windows parent - which is why the tool itself uses the job; the rows here start native children.
 */
final class ProcessTree implements Children {

    /** How long each process of a killed tree is waited on to be gone. */
    private static final long GONE_SECONDS = 10;

    @Override
    public boolean kill(ProcessHandle root) {
        List<ProcessHandle> tree = new ArrayList<>(root.descendants().toList());
        tree.add(root);
        tree.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle each : tree) {
            each.onExit().completeOnTimeout(each, GONE_SECONDS, TimeUnit.SECONDS).join();
        }
        return tree.stream().noneMatch(ProcessHandle::isAlive);
    }
}
