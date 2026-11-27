package io.github.jayessdeesea.minecraft.landing.shell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Landing finds its own programs: Task Scheduler's PATH has no sh, so git and the verify's first word are looked up in
 * the config's {@code tool.path}.
 */
final class Programs {

    private Programs() {
    }

    /**
     * {@code name} as a program to start: a name with a separator is itself; else the first {@code dir/name}, or
     * {@code dir/name.exe}, that is a file; none, with {@code dirs} given, is an error naming it; with no {@code dirs},
     * the name as given, for the inherited PATH to find.
     */
    static String resolve(String name, List<Path> dirs, Predicate<Path> isFile) {
        if (name.contains("/") || name.contains("\\") || dirs.isEmpty()) {
            return name;
        }
        for (Path dir : dirs) {
            for (String file : List.of(name, name + ".exe")) {
                if (isFile.test(dir.resolve(file))) {
                    return dir.resolve(file).toString();
                }
            }
        }
        throw new IllegalStateException("no " + name + " in tool.path " + dirs + ": run the install again");
    }

    /**
     * {@code values} set in a child's {@code environment}, each replacing every spelling of its name: Windows hands a
     * process {@code Path}, the map is case-sensitive, and a child given both reads the inherited one.
     */
    static void set(Map<String, String> environment, Map<String, String> values) {
        values.forEach((name, value) -> {
            environment.keySet().removeIf(key -> key.equalsIgnoreCase(name));
            environment.put(name, value);
        });
    }

    /** {@code command} with its first word resolved. */
    static List<String> command(List<String> command, List<Path> dirs, Predicate<Path> isFile) {
        List<String> resolved = new ArrayList<>(command);
        if (!resolved.isEmpty()) {
            resolved.set(0, resolve(resolved.getFirst(), dirs, isFile));
        }
        return resolved;
    }
}
