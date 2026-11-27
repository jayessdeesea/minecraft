package io.github.jayessdeesea.minecraft.app;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaModifier;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** Every module of the reactor, for the counts over their sources. */
    static final List<String> MODULES = List.of("protocol", "command", "command-ai", "bot", "landing", "app");

    /** The most lines a source file holds (docs/conventions.md, operator 2026-11-18). */
    static final int MOST_LINES = 1000;

    /**
     * Files already over {@link #MOST_LINES}, each at its length when the limit was set: they may only shrink,
     * and come off this list once under (backlog/split-oversized-sources.md).
     */
    static final Map<String, Integer> OVER_THE_LIMIT = Map.of(
            "app/src/main/java/io/github/jayessdeesea/minecraft/app/SoldierRunner.java", 7356);

    /**
     * The state `SoldierRunner` holds, counted as its declarations at class level - fields and constants, a line each,
     * near enough: it may only shrink (docs/conventions.md, "Refactor
     * every sixteenth commit, between profiles"). State added there is state every change has to read past; a new one belongs in the
     * class whose work it is. The cap comes down with each extraction - the test says when.
     */
    static final int RUNNER_FIELDS = 586;
    /** Lines over {@value #LONG} characters in the main sources: no new one, and the count only comes down. */
    static final int LONG_LINES = 55;
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
        for (String module : MODULES) {
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
        for (String module : MODULES) {
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

    private static final String LANDING = ROOT + ".landing";
    /** The rules below are vacuous unless the landing module's classes are on this test's classpath (app's pom). */
    @Test
    void theLandingModuleIsImported() {
        assertTrue(classes.stream().anyMatch(c -> c.getPackageName().equals(LANDING)),
                "no class of " + LANDING + " imported: the landing rules below would hold of nothing");
    }

    /**
     * The merge queue stands alone: it runs from an installed jar to gatekeep this code, so it uses none of it, and
     * nothing of the force uses it (below).
     */
    @Test
    void landingDependsOnNoOtherModule() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage(LANDING + "..")
                .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage(ROOT + "..")
                        .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage(LANDING + ".."))))
                .orShould().dependOnClassesThat().resideInAnyPackage("org.springframework..", "io.netty..")
                .check(classes);
    }

    @Test
    void noModuleDependsOnLanding() {
        ArchRuleDefinition.noClasses()
                .that().resideOutsideOfPackage(LANDING + "..")
                .should().dependOnClassesThat().resideInAPackage(LANDING + "..")
                .check(classes);
    }

    /**
     * Functional core, imperative shell: outside its shell the landing module is a pure core - each of its decisions a
     * function of its arguments, with no I/O and nothing read from the time, the machine or chance but what it is
     * handed (amendment 1, R4: a closed set, not a list of what is barred).
     */
    @Test
    void landingsCoreIsPure() {
        pureCore(LANDING).check(classes);
    }

    private static final String FIXTURES = ROOT + ".app.purecore";

    /** Each gap a pure core could read the ambient through is refused by the same rule, and a core handed them passes. */
    @ParameterizedTest
    @ValueSource(strings = {"NowInAZone", "SystemDefaultZone", "NewDate", "NewRandom", "ThreadLocalRandomCurrent",
            "RandomUuid", "SystemProperties", "LineSeparator", "Console", "SystemIn", "InstantNowReference",
            "DefaultRandomGenerator", "NamedRandomGenerator", "NestedRandomGeneratorStatic", "MathRandom", "ClockTick",
            "InstantSourceSystem", "NewSplittableRandom", "NewGregorianCalendar", "ShuffleWithNoSource",
            "FormatterToAFile"})
    void thePureCoreRuleRefusesEachGap(String fixture) throws ClassNotFoundException {
        JavaClasses one = new ClassFileImporter().importClasses(Class.forName(FIXTURES + ".PureCoreFixtures$" + fixture));
        assertThrows(AssertionError.class, () -> pureCore(FIXTURES).check(one), fixture + " passed the pure-core rule");
    }

    @Test
    void thePureCoreRulePassesACoreHandedItsClockAndRandomSource() throws ClassNotFoundException {
        pureCore(FIXTURES).check(new ClassFileImporter().importClasses(
                Class.forName(FIXTURES + ".PureCoreFixtures$Handed")));
    }

    /**
     * The pure-core rule for {@code core} - the package and those under it but its {@code shell}: it depends only on
     * listed classes, and on those outside it accesses - by call or method reference - nothing that reads the time,
     * the system's defaults, a file or a random source it was not handed. Its own members may bear those names: a
     * {@code now} it was handed is a value. Methods too broad to bar ({@code hashCode}, {@code toLowerCase}) stay.
     */
    static ArchRule pureCore(String core) {
        DescribedPredicate<JavaClass> listed = DescribedPredicate.describe("listed for a pure core",
                c -> listedForAPureCore(core, c.isArray() ? c.getBaseComponentType() : c));
        DescribedPredicate<JavaAccess<?>> ambient = DescribedPredicate.describe(
                "read the time, the system's defaults or a random source not handed in", access -> {
                    String owner = access.getTargetOwner().getName();
                    String name = access.getTarget().getName();
                    String pkg = access.getTargetOwner().getPackageName();
                    if (pkg.equals(core) || pkg.startsWith(core + ".")) {
                        return false;
                    }
                    List<JavaClass> params = access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget unit
                            ? unit.getRawParameterTypes() : List.of();
                    return List.of("now", "systemDefault", "getDefault").contains(name)
                            || owner.equals(java.time.Clock.class.getName()) && (name.startsWith("system")
                            || name.startsWith("tick"))
                            || owner.equals(java.time.InstantSource.class.getName()) && name.startsWith("system")
                            || List.of(Math.class.getName(), StrictMath.class.getName()).contains(owner)
                            && name.equals("random")
                            || owner.equals(java.util.Collections.class.getName()) && name.equals("shuffle")
                            && params.size() == 1
                            || owner.equals(java.util.Formatter.class.getName()) && name.equals("<init>")
                            && !params.isEmpty() && List.of(String.class.getName(), java.io.File.class.getName())
                            .contains(params.getFirst().getName())
                            || owner.startsWith(java.util.random.RandomGenerator.class.getName())
                            && access.getTarget().resolveMember().map(m -> m.getModifiers().contains(JavaModifier.STATIC))
                            .orElse(false);
                });
        return ArchRuleDefinition.noClasses()
                .that().resideInAPackage(core + "..").and().resideOutsideOfPackage(core + ".shell..")
                .should().dependOnClassesThat(DescribedPredicate.not(listed))
                .orShould().accessTargetWhere(ambient);
    }

    /** What a pure core may depend on: its own packages, the language and the collections, time as values, Jackson. */
    private static boolean listedForAPureCore(String core, JavaClass c) {
        String name = c.getName();
        String pkg = c.getPackageName();
        if (c.isPrimitive()) {
            return true;
        }
        if (pkg.equals(core) || pkg.startsWith(core + ".") && !pkg.startsWith(core + ".shell")) {
            return true;
        }
        if (pkg.equals("java.lang")) {
            String simple = name.substring("java.lang.".length());
            return !(simple.startsWith("Process") || simple.startsWith("Thread")
                    || List.of("System", "Runtime", "ClassLoader").stream()
                    .anyMatch(b -> simple.equals(b) || simple.startsWith(b + "$")));
        }
        if (pkg.equals("java.util")) {
            String simple = name.substring("java.util.".length());
            return List.of("Random", "SplittableRandom", "UUID", "Date", "Calendar", "GregorianCalendar", "TimeZone", "Timer",
                    "Scanner", "Properties", "Locale", "ResourceBundle", "ServiceLoader").stream().noneMatch(b -> simple.equals(b) || simple.startsWith(b + "$"));
        }
        return List.of("java.lang.invoke", "java.lang.runtime", "java.util.function", "java.util.stream", "java.util.regex",
                "java.time", "java.time.format").contains(pkg)
                || name.equals("java.util.random.RandomGenerator") || name.startsWith("java.util.random.RandomGenerator$")
                || name.equals("java.io.IOException")
                || pkg.startsWith("com.fasterxml.jackson.");
    }
}
