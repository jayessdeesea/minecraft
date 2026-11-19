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
            "app/src/main/java/io/github/jayessdeesea/minecraft/app/SoldierRunner.java", 8488,
            "app/src/test/java/io/github/jayessdeesea/minecraft/app/SquadSimulationTest.java", 3761,
            "bot/src/main/java/io/github/jayessdeesea/minecraft/bot/DeterministicSoldier.java", 1535,
            "app/src/main/java/io/github/jayessdeesea/minecraft/app/ChainOfCommand.java", 1305,
            "bot/src/test/java/io/github/jayessdeesea/minecraft/bot/DeterministicSoldierTest.java", 1324,
            "app/src/test/java/io/github/jayessdeesea/minecraft/app/SimServer.java", 1071);

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
