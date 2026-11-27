package io.github.jayessdeesea.minecraft.landing.shell;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Landing finds its own programs (amendment 3, R11): a scheduled task's PATH has no sh, so git and the verify's first
 * word are looked up in the config's tool.path - a name with a separator is itself; else the first directory holding
 * it, or it with .exe; none found is an error naming it; with no directories, the name as given.
 */
class ProgramsTest {

    private static final Path GIT_DIR = Path.of("C:/Program Files/Git/mingw64/bin");
    private static final Path USR_BIN = Path.of("C:/Program Files/Git/usr/bin");
    private static final Set<Path> FILES = Set.of(GIT_DIR.resolve("git.exe"), USR_BIN.resolve("sh"));

    @Test
    void aNameIsFoundInTheFirstDirectoryHoldingIt() {
        assertEquals(GIT_DIR.resolve("git.exe").toString(), Programs.resolve("git", List.of(USR_BIN, GIT_DIR), FILES::contains));
        assertEquals(USR_BIN.resolve("sh").toString(), Programs.resolve("sh", List.of(USR_BIN, GIT_DIR), FILES::contains));
    }

    @Test
    void aPathIsItself() {
        assertEquals("C:/x/git", Programs.resolve("C:/x/git", List.of(GIT_DIR), FILES::contains));
        assertEquals("C:\\x\\git", Programs.resolve("C:\\x\\git", List.of(GIT_DIR), FILES::contains));
    }

    @Test
    void aNameInNoDirectoryIsAnErrorNamingIt() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Programs.resolve("mvn", List.of(USR_BIN, GIT_DIR), FILES::contains));
        assertTrue(e.getMessage().contains("mvn"), e.getMessage());
    }

    @Test
    void withNoDirectoriesTheNameIsAsGiven() {
        assertEquals("git", Programs.resolve("git", List.of(), FILES::contains));
    }
}
