package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.ExitCode;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The scheduler's probe ({@code job-check <file>}, run by install-landing.sh's check task): this process entered its job
 * to get here; then git, sh's mvn and java, found and started as the task finds and starts them, each answer a line of
 * the file. A yes is an exit of 0 and a first line of the program's own form; anything else, or no answer in its time
 * (the probe then ended through its job), is a NO, named, and the exit is 6.
 */
final class JobCheck {

    /** How long one program may take to answer. */
    static final Duration WAIT = Duration.ofSeconds(60);

    private static final Pattern JAVA_VERSION = Pattern.compile("^(openjdk|java) version \".*");

    private JobCheck() {
    }

    /** One program's answer: its line in the file, and whether it is a yes. */
    record Answer(String line, boolean yes) {
    }

    static int run(Path file, Config config, Git.Tool git, Map<String, String> env, Children children, PrintStream out)
            throws IOException {
        List<String> lines = new ArrayList<>(List.of("entered its job, pid " + ProcessHandle.current().pid()));
        boolean all = true;
        List<Answer> answers = new ArrayList<>();
        try {
            answers.add(probe("git", List.of(git.program().getFirst(), "--version"), l -> l.startsWith("git version"), env,
                    children, WAIT));
        } catch (RuntimeException e) {
            answers.add(new Answer("git: NO - " + e.getMessage(), false));
        }
        try {
            answers.add(probe("sh", List.of(Programs.resolve("sh", config.toolPath(), Files::isRegularFile), "-c", "mvn -v"),
                    l -> l.startsWith("Apache Maven"), env, children, WAIT));
        } catch (RuntimeException e) {
            answers.add(new Answer("sh: NO - " + e.getMessage(), false));
        }
        answers.add(probe("java", List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version"),
                l -> JAVA_VERSION.matcher(l).matches(), env, children, WAIT));
        for (Answer answer : answers) {
            lines.add(answer.line());
            all &= answer.yes();
            out.println("job-check " + answer.line());
        }
        // whole or not there: the install reads it as soon as it appears
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.write(part, lines, StandardCharsets.UTF_8);
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return all ? ExitCode.OK : ExitCode.LANDING_FAILED;
    }

    /**
     * {@code command} started with {@code env}, its output read on a thread of its own so {@code wait} bounds it: a yes
     * when it exits 0 and its first line - a JVM's warning skipped - is of {@code form}. Past {@code wait} it is ended
     * through {@code children} and is a NO.
     */
    static Answer probe(String name, List<String> command, Predicate<String> form, Map<String, String> env,
                        Children children, Duration wait) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            Programs.set(builder.environment(), env);
            Process p = builder.start();
            p.getOutputStream().close();
            CompletableFuture<byte[]> output = Git.drain(p.getInputStream());
            if (!p.waitFor(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                boolean gone = children.kill(p.toHandle());
                return new Answer(name + ": NO - no answer in " + wait.toSeconds() + " s" + (gone ? "" : ", and not proven gone"),
                        false);
            }
            String first = new String(output.get(wait.toMillis(), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8).lines()
                    // a JVM's warning (Maven's own, under this JDK) is not the program's answer
                    .filter(l -> !l.isBlank() && !l.startsWith("WARNING:")).findFirst().orElse("").strip();
            if (p.exitValue() != 0) {
                return new Answer(name + ": NO - exited " + p.exitValue() + ": " + first, false);
            }
            if (!form.test(first)) {
                return new Answer(name + ": NO - not its answer: " + first, false);
            }
            return new Answer(name + ": " + first, true);
        } catch (IOException | ExecutionException | java.util.concurrent.TimeoutException e) {
            return new Answer(name + ": NO - " + e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during job-check", e);
        }
    }
}
