package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The missions PAUSED (operator, 2026-11-25): a mineshaft whose stairs were stopped - water or lava it couldn't block, a
 * danger area - held, neither a dead well nor mined dry, with no squad on it, till the operator resumes it. The register's
 * rows are where a pause is kept (ObjectiveBook#paused); this is their copy in memory, read on every tick, filled from them
 * once at load and written through to them. Its owner's lock guards it.
 */
final class MinePauses {

    private final Map<FarmMission, String> paused = new LinkedHashMap<>();

    /** The pauses the register holds, after a restart. */
    void restore(Map<FarmMission, String> held) {
        paused.putAll(held);
    }

    /** {@code mission} paused, {@code why}: true if it wasn't already. */
    boolean pause(FarmMission mission, String why) {
        return paused.putIfAbsent(mission, why) == null;
    }

    /** {@code mission} taken up again: why it was paused, if it was. */
    Optional<String> resume(FarmMission mission) {
        return Optional.ofNullable(paused.remove(mission));
    }

    boolean paused(FarmMission mission) {
        return paused.containsKey(mission);
    }
}
