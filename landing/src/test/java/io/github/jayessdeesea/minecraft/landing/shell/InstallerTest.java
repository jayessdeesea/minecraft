package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * landing install as the config's one writer (docs/conventions.md, "Landing a change"): every key there kept, a seed
 * set only where there is none, jar.* and tool.path this install's, the file written through Properties with its header
 * once - and refused, with nothing written, when what it would write names no repo, its arguments are bad, or the lock
 * is held.
 */
class InstallerTest {

    private static final Sha FROM = Sha.parse(String.format("%040x", 7));
    private static final Sha CHANGE = Sha.parse(String.format("%040x", 5));
    private static final List<String> DIRS = List.of("C:\\git\\bin", "C:\\git\\usr\\bin", "C:\\maven\\bin");

    @TempDir
    Path dir;

    private final ByteArrayOutputStream said = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(said, true, StandardCharsets.UTF_8);

    private static Map<String, String> seeds() {
        Map<String, String> seeds = new LinkedHashMap<>();
        seeds.put("repo", "C:/github/x");
        seeds.put("worktree", "C:/mc-worktrees/landing");
        seeds.put("poke", "schtasks /run /tn mc-landing");
        seeds.put("hook", "C:/github/x/.git/hooks/pre-push");
        return seeds;
    }

    private static Properties of(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return p;
    }

    private static Properties read(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }

    private Path built() throws IOException {
        Path built = dir.resolve("built.jar");
        Files.writeString(built, "a jar");
        return built;
    }

    private List<String> args(Path installed, Map<String, String> seeds, List<String> dirs) throws IOException {
        List<String> args = new ArrayList<>(List.of(built().toString(), installed.toString(), FROM.value(), CHANGE.value()));
        args.addAll(dirs);
        seeds.forEach((k, v) -> args.add(k + "=" + v));
        return args;
    }

    private List<String> comments(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(l -> l.startsWith("#")).toList();
    }

    private static List<String> headerLines() {
        return Installer.HEADER.lines().map(l -> "#" + l).toList();
    }

    /** Row 1: from nothing, the four seeds and this install's three; nothing else. */
    @Test
    void anEmptyConfigTakesTheSeedsAndThisInstallsOwn() {
        Properties merged = Installer.merged(new Properties(), seeds(), FROM, CHANGE, DIRS);
        assertEquals(Set.of("repo", "worktree", "poke", "hook", "jar.built-from", "jar.landing-change", "tool.path"),
                merged.stringPropertyNames());
        seeds().forEach((k, v) -> assertEquals(v, merged.getProperty(k), k));
        assertEquals(FROM.value(), merged.getProperty("jar.built-from"));
        assertEquals(CHANGE.value(), merged.getProperty("jar.landing-change"));
        assertEquals(String.join(";", DIRS), merged.getProperty("tool.path"));
    }

    /** Row 2: a key there is kept over its seed. */
    @Test
    void aKeyThereWinsOverItsSeed() {
        assertEquals("X", Installer.merged(of("repo", "X"), Map.of("repo", "Y"), FROM, CHANGE, DIRS).getProperty("repo"));
    }

    /** Row 3: keys no seed names are kept, their values unchanged. */
    @Test
    void keysNoSeedNamesAreKept() {
        Properties merged = Installer.merged(of("run.budget.minutes", "240", "hook.hash", "abc", "remote", "deploy", "journal",
                "J"), seeds(), FROM, CHANGE, DIRS);
        assertEquals("240", merged.getProperty("run.budget.minutes"));
        assertEquals("abc", merged.getProperty("hook.hash"));
        assertEquals("deploy", merged.getProperty("remote"));
        assertEquals("J", merged.getProperty("journal"));
    }

    /** Row 4: an earlier install's jar.* and tool.path are this install's. */
    @Test
    void anEarlierInstallsOwnKeysAreThisInstalls() {
        Properties merged = Installer.merged(of("jar.built-from", String.format("%040x", 1), "jar.landing-change",
                String.format("%040x", 2), "tool.path", "D:\\old"), seeds(), FROM, CHANGE, DIRS);
        assertEquals(FROM.value(), merged.getProperty("jar.built-from"));
        assertEquals(CHANGE.value(), merged.getProperty("jar.landing-change"));
        assertEquals(String.join(";", DIRS), merged.getProperty("tool.path"));
    }

    /** Row 5: no config: written, HEADER its comment, the seeds read back, the journal and lock beside it. */
    @Test
    void aFirstInstallWritesTheConfig() throws IOException {
        Path config = dir.resolve("state/config");
        Path installed = dir.resolve("state/landing.jar");
        assertEquals(0, Installer.install(config, args(installed, seeds(), DIRS), out), said.toString(StandardCharsets.UTF_8));
        assertEquals("a jar", Files.readString(installed));
        List<String> comments = comments(config);
        assertEquals(headerLines(), comments.subList(0, comments.size() - 1), "HEADER, the date line aside");
        Config read = Config.load(config);
        assertEquals(Path.of("C:/github/x"), read.repo());
        assertEquals(Path.of("C:/mc-worktrees/landing"), read.worktree());
        assertEquals(List.of("schtasks", "/run", "/tn", "mc-landing"), read.poke());
        assertEquals(Optional.of(Path.of("C:/github/x/.git/hooks/pre-push")), read.hook());
        Path home = config.toAbsolutePath().getParent();
        assertEquals(home.resolve("journal.jsonl"), read.journal());
        assertEquals(home.resolve("integrator.lock"), read.lock());
    }

    private Path handWritten() throws IOException {
        Path config = dir.resolve("state/config");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "# note\nrepo=C:/x\nrun.budget.minutes=240\nhook.hash=abc\n");
        return config;
    }

    /** Row 6: over a hand-written file, HEADER once and every key kept, bar this install's own. */
    @Test
    void aReinstallKeepsEveryKey() throws IOException {
        Path config = handWritten();
        Properties before = read(config);
        assertEquals(0, Installer.install(config, args(dir.resolve("state/landing.jar"), seeds(), DIRS), out),
                said.toString(StandardCharsets.UTF_8));
        Properties after = read(config);
        for (String key : before.stringPropertyNames()) {
            assertEquals(before.getProperty(key), after.getProperty(key), key);
        }
        assertEquals(FROM.value(), after.getProperty("jar.built-from"));
        assertEquals(CHANGE.value(), after.getProperty("jar.landing-change"));
        assertEquals(String.join(";", DIRS), after.getProperty("tool.path"));
        Map<String, String> seeded = seeds();
        seeded.remove("repo");
        seeded.forEach((k, v) -> assertEquals(v, after.getProperty(k), k));
        List<String> comments = comments(config);
        assertEquals(headerLines(), comments.subList(0, comments.size() - 1), "HEADER once, the hand-written note gone");
    }

    /** Row 7: installed twice more, HEADER still once and the keys as after the first. */
    @Test
    void installsOverInstallsKeepOneHeader() throws IOException {
        Path config = handWritten();
        Path installed = dir.resolve("state/landing.jar");
        assertEquals(0, Installer.install(config, args(installed, seeds(), DIRS), out));
        Properties first = read(config);
        assertEquals(0, Installer.install(config, args(installed, seeds(), DIRS), out));
        assertEquals(0, Installer.install(config, args(installed, seeds(), DIRS), out));
        assertEquals(headerLines().size() + 1, comments(config).size(), "HEADER's lines and the date line");
        assertEquals(first, read(config));
    }

    /** Row 8: tool.path's directories, a space and backslashes in them, read back as they were given. */
    @Test
    void theToolPathReadsBackAsGiven() throws IOException {
        Path config = dir.resolve("state/config");
        List<String> dirs = List.of("C:\\Program Files\\Git\\mingw64\\bin", "C:\\git\\usr\\bin", "C:\\maven\\bin");
        assertEquals(0, Installer.install(config, args(dir.resolve("state/landing.jar"), seeds(), dirs), out));
        assertEquals(dirs.stream().map(Path::of).toList(), Config.load(config).toolPath());
    }

    /** Row 9: no file and no repo seed: what it would write names no repo - 6, nothing written. */
    @Test
    void noRepoAnywhereIsLandingFailingWithNothingWritten() throws IOException {
        Path config = dir.resolve("state/config");
        Path installed = dir.resolve("state/landing.jar");
        Map<String, String> seeds = seeds();
        seeds.remove("repo");
        List<String> command = new ArrayList<>(List.of("install"));
        command.addAll(args(installed, seeds, DIRS));
        assertEquals(6, Landing.launch(command.toArray(String[]::new), Map.of("LANDING_CONFIG", config.toString()), dir, out,
                Optional.empty(), ProcessTree::new));
        assertTrue(said.toString(StandardCharsets.UTF_8).contains("names no repo"), said.toString(StandardCharsets.UTF_8));
        assertFalse(Files.exists(config), "no config");
        assertFalse(Files.exists(installed), "no jar");
    }

    /** Row 10: a seed with no '=', an empty key, or a key given twice is usage, nothing touched. */
    @ParameterizedTest
    @ValueSource(strings = {"repo", "=x", "repo=a|repo=b"})
    void aBadSeedIsUsage(String bad) throws IOException {
        Path config = dir.resolve("state/config");
        Path installed = dir.resolve("state/landing.jar");
        List<String> args = args(installed, Map.of(), DIRS);
        args.addAll(List.of(bad.split("\\|")));
        assertEquals(2, Installer.install(config, args, out), said.toString(StandardCharsets.UTF_8));
        assertFalse(Files.exists(config), "no config");
        assertFalse(Files.exists(installed), "no jar");
    }

    /** Row 11: the lock held and no config yet: 4, no jar and no config. */
    @Test
    void theLockHeldBeforeAnyConfigTouchesNothing() throws IOException {
        Path config = dir.resolve("state/config");
        Path installed = dir.resolve("state/landing.jar");
        try (IntegratorLock.Held held = new IntegratorLock(dir.resolve("state/integrator.lock")).tryAcquire().orElseThrow()) {
            assertEquals(4, Installer.install(config, args(installed, seeds(), DIRS), out), said.toString(StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(config), "no config");
        assertFalse(Files.exists(installed), "no jar");
    }
}
