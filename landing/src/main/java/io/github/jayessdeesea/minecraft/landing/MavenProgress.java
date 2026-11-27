package io.github.jayessdeesea.minecraft.landing;

import java.util.List;
import java.util.Optional;

/**
 * Where Maven's log says the build is, for display only: the module last begun and its place in the reactor, the test
 * classes done and how many of them failed ({@link MavenLog}).
 */
public record MavenProgress(Optional<String> module, int index, int of, int classesDone, int classesFailing) {

    public static MavenProgress of(List<String> logLines) {
        return MavenLog.of(logLines).progress();
    }

    /** The module and its place: "app [7/7]", or "no module begun yet". */
    public String where() {
        return module.map(m -> m + " [" + index + "/" + of + "]").orElse("no module begun yet");
    }

    /** "app [7/7], 47 test classes done, 0 failing"; before a module, "no module begun yet, 0 test classes done". */
    public String words() {
        String classes = classesDone + (classesDone == 1 ? " test class done" : " test classes done");
        return module.isEmpty() ? where() + ", " + classes : where() + ", " + classes + ", " + classesFailing + " failing";
    }
}
