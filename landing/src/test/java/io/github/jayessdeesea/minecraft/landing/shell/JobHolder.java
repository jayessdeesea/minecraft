package io.github.jayessdeesea.minecraft.landing.shell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * A JVM of its own for the job's rows (as {@link LockHolder} for the lock's): it enters the job, does what it is told
 * with the chain {@code sh <outer>}, where outer runs {@code sh <inner>} and inner {@code exec}s a native program - as
 * verify.sh runs mvn and mvn execs java - and writes to its out file: the pids its job held but itself, what it
 * answered, and its members at the end.
 */
public final class JobHolder {

    private JobHolder() {
    }

    /** {@code <mode> <out file> <outer script>}: mode enter, verify, git or wait. */
    public static void main(String[] args) throws IOException, InterruptedException {
        String mode = args[0];
        Path out = Path.of(args[1]);
        Path outer = Path.of(args[2]);
        Job job = Job.enter();
        long self = ProcessHandle.current().pid();
        Set<Long> seen = new ConcurrentSkipListSet<>();
        Thread watcher = Thread.ofPlatform().daemon().start(() -> {
            while (true) {
                job.members().stream().filter(pid -> pid != self).forEach(seen::add);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        String answer = switch (mode) {
            case "enter" -> "entered";
            case "verify" -> {
                Verifier.Run run = new Verifier(List.of("sh", outer.toString()),
                        new io.github.jayessdeesea.minecraft.landing.VerifyWatch.Limits(Duration.ofSeconds(1),
                                Duration.ofSeconds(30), Duration.ofMillis(50)),
                        out.resolveSibling("verify.log"), Map.of(), job, java.time.Clock.systemUTC()).run(out.getParent());
                yield "end=" + run.end().getClass().getSimpleName();
            }
            case "git" -> {
                Git git = new Git(out.getParent(), new Git.Tool(List.of("sh", outer.toString()), Map.of(), Duration.ofSeconds(1),
                        Duration.ofSeconds(1), job));
                yield "timedOut=" + git.remote().run(List.of("fetch")).timedOut();
            }
            case "wait" -> {
                new ProcessBuilder("sh", outer.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                Thread.sleep(2000);
                yield "started";
            }
            default -> throw new IllegalArgumentException(mode);
        };
        Thread.sleep(200);
        watcher.interrupt();
        String members = job.members().stream().map(String::valueOf).collect(Collectors.joining(" "));
        String others = seen.stream().map(String::valueOf).collect(Collectors.joining(" "));
        Files.writeString(out.resolveSibling(out.getFileName() + ".tmp"), answer + "\n" + others + "\n" + members + "\n" + self + "\n");
        Files.move(out.resolveSibling(out.getFileName() + ".tmp"), out);
        if (mode.equals("wait")) {
            Thread.sleep(120_000);
        }
    }
}
