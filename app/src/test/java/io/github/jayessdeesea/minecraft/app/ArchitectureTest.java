package io.github.jayessdeesea.minecraft.app;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enforces, at build time, the module tenets documented in docs/architecture.md:
 * Netty stays inside the protocol module, and the command-ai implementation
 * package is never depended on directly by the command interfaces it
 * implements or by the bot module that consumes those interfaces.
 */
class ArchitectureTest {

    private static final String ROOT = "io.github.jayessdeesea.minecraft";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages(ROOT);
    }

    @Test
    void nettyIsConfinedToTheProtocolModule() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideOutsideOfPackage(ROOT + ".protocol..")
                .should().dependOnClassesThat().resideInAPackage("io.netty..");
        rule.check(classes);
    }

    @Test
    void commandDomainDoesNotDependOnItsAiImplementation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + ".command")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".command.ai..");
        rule.check(classes);
    }

    @Test
    void botDependsOnTheCommandInterfaceNotItsAiImplementation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + ".bot..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".command.ai..");
        rule.check(classes);
    }

    /**
     * A component with a second constructor - a test seam - names the one Spring uses: otherwise
     * Spring looks for a default constructor and the app fails at startup, past every unit test.
     */
    @Test
    void aComponentWithSeveralConstructorsNamesTheOneSpringUses() {
        List<String> ambiguous = classes.stream()
                .filter(c -> c.isAnnotatedWith(Component.class) || c.isMetaAnnotatedWith(Component.class))
                .filter(c -> c.getConstructors().size() > 1)
                .filter(c -> c.getConstructors().stream().filter(k -> k.isAnnotatedWith(Autowired.class)).count() != 1)
                .map(JavaClass::getName)
                .toList();
        assertEquals(List.of(), ambiguous);
    }

    /** The most lines a source file holds (docs/conventions.md, operator 2026-11-18). */
    static final int MOST_LINES = 1000;

    /**
     * Files already over {@link #MOST_LINES}, each at its length when the limit was set: they may only shrink,
     * and come off this list once under (backlog/split-oversized-sources.md).
     */
    static final Map<String, Integer> OVER_THE_LIMIT = Map.of(
            "app/src/main/java/io/github/jayessdeesea/minecraft/app/SoldierRunner.java", 7930);

    /**
     * The state `SoldierRunner` holds, counted as its declarations at class level - fields and constants, a line each,
     * near enough: it may only shrink (docs/conventions.md, "Refactor
     * every sixteenth commit, between profiles"). State added there is state every change has to read past; a new one belongs in the
     * class whose work it is. The cap comes down with each extraction - the test says when.
     */
    static final int RUNNER_FIELDS = 664;
    /** Lines over {@value #LONG} characters in the main sources: no new one, and the count only comes down. */
    static final int LONG_LINES = 57;
    static final int LONG = 200;
    /** How far under a cap a count may fall before the cap has to follow it down. */
    static final int SLACK = 5;

    @Test
    void theRunnersStateAndTheLongLinesOnlyShrink() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        java.util.regex.Pattern field = java.util.regex.Pattern.compile(
                "^    (?:private |protected |public )?(?:static |final |volatile |transient )*[\\w.<>\\[\\], ?]+ \\w+(?: = .*)?[;({]?\\s*$");
        java.util.regex.Pattern notAField = java.util.regex.Pattern.compile("^    (?:return|throw|if|for|while|else|try|case|//|\\*|@|\\}).*");
        long fields;
        try (Stream<String> lines = Files.lines(root.resolve("app/src/main/java/io/github/jayessdeesea/minecraft/app/SoldierRunner.java"))) {
            fields = lines.filter(l -> field.matcher(l).matches() && !notAField.matcher(l).matches() && !l.contains("(")
                    || l.matches("^    (?:private |protected |public )?(?:static |final |volatile )+[\\w.<>\\[\\], ?]+ \\w+ = .*")).count();
        }
        long longLines = 0;
        for (String module : List.of("protocol", "command", "command-ai", "bot", "app")) {
            Path src = root.resolve(module).resolve("src").resolve("main");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    try (Stream<String> read = Files.lines(file)) {
                        longLines += read.filter(l -> l.length() > LONG).count();
                    }
                }
            }
        }
        List<String> wrong = new ArrayList<>();
        ratchet(wrong, "SoldierRunner's fields", fields, RUNNER_FIELDS, "RUNNER_FIELDS");
        ratchet(wrong, "lines over " + LONG + " characters in the main sources", longLines, LONG_LINES, "LONG_LINES");
        assertEquals(List.of(), wrong);
    }

    private static void ratchet(List<String> wrong, String what, long now, int cap, String name) {
        if (now > cap) {
            wrong.add(what + " grew to " + now + ", past " + cap + " - they may only shrink");
        } else if (now < cap - SLACK) {
            wrong.add(what + " are down to " + now + ": bring " + name + " down to it");
        }
    }

    @Test
    void sourceFilesStayUnder1000Lines() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> wrong = new ArrayList<>();
        OVER_THE_LIMIT.keySet().stream().filter(f -> !Files.isRegularFile(root.resolve(f)))
                .forEach(f -> wrong.add(f + " is listed but not found: take it off OVER_THE_LIMIT"));
        for (String module : List.of("protocol", "command", "command-ai", "bot", "app")) {
            Path src = root.resolve(module).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    int lines;
                    try (Stream<String> read = Files.lines(file)) {
                        lines = (int) read.count();
                    }
                    Integer cap = OVER_THE_LIMIT.get(name);
                    if (cap == null && lines > MOST_LINES) {
                        wrong.add(name + " is " + lines + " lines, over " + MOST_LINES);
                    } else if (cap != null && lines > cap) {
                        wrong.add(name + " grew to " + lines + " lines, past its " + cap + " - it may only shrink");
                    } else if (cap != null && lines <= MOST_LINES) {
                        wrong.add(name + " is under the limit at " + lines + " lines: take it off OVER_THE_LIMIT");
                    }
                }
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void commandDomainStaysFrameworkFree() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + ".command")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "io.netty..");
        rule.check(classes);
    }
}
