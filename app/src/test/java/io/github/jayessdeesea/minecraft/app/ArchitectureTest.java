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

import java.util.List;

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

    @Test
    void commandDomainStaysFrameworkFree() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage(ROOT + ".command")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "io.netty..");
        rule.check(classes);
    }
}
